package org.satpol

import java.time.Instant
import kotlin.math.*

/**
 * Daily EOP table with explicit source and no silent out-of-range extrapolation.
 * CSV schema:
 * utc,dut1_seconds,xp_arcsec,yp_arcsec,lod_excess_seconds
 * 2026-10-25T00:00:00Z,-0.1234,0.123,0.456,0.0001
 *
 * The converter/importer must use a common IERS EOP realization. Since UT1-UTC
 * has a discontinuity at positive/negative UTC leap seconds, interpolation
 * across leaps is FORBIDDEN (DUT1 jump >0.5s triggers an explicit failure).
 */
class EarthOrientationTable private constructor(
    private val data: List<Pair<Instant,EarthOrientation>>
) {
    init {
        require(data.isNotEmpty())
        require(data.zipWithNext().all { (a,b) -> a.first < b.first })
    }

    fun at(time:Instant):EarthOrientation {
        if(data.size==1) {
            require(time==data[0].first) { "EOP interpolation requires at least two points" }
            return data[0].second
        }
        require(time >= data.first().first && time <= data.last().first) {
            "EOP data unavailable or stale for $time"
        }
        val exact=data.binarySearch { it.first.compareTo(time) }
        if(exact>=0) return data[exact].second
        val insert=-(exact+1)
        val lower=data[insert-1]; val upper=data[insert]
        val dt=upper.first.epochSecond-lower.first.epochSecond
        require(dt in 1L..172800L) { "EOP gap exceeds 48 hours" }
        val dutJump=upper.second.dut1Seconds-lower.second.dut1Seconds
        require(abs(dutJump)<0.5) { "Cannot interpolate UT1-UTC across leap second" }
        val f=(time.epochSecond-lower.first.epochSecond+
            (time.nano-lower.first.nano)*1e-9)/dt.toDouble()
        fun lerp(a:Double,b:Double)=a+(b-a)*f
        return EarthOrientation(
            lerp(lower.second.dut1Seconds,upper.second.dut1Seconds),
            lerp(lower.second.polarMotionXArcsec,upper.second.polarMotionXArcsec),
            lerp(lower.second.polarMotionYArcsec,upper.second.polarMotionYArcsec),
            lerp(lower.second.lengthOfDayExcessSeconds,upper.second.lengthOfDayExcessSeconds),
            lower.second.source + " (interpolated)"
        )
    }

    companion object {
        fun parseCsv(contents:String,source:String):EarthOrientationTable {
            require(source.isNotBlank())
            val all=contents.lineSequence().map(String::trim)
                .filter{it.isNotEmpty()&&!it.startsWith("#")}.toList()
            require(all.firstOrNull() ==
                "utc,dut1_seconds,xp_arcsec,yp_arcsec,lod_excess_seconds")
            val entries=all.drop(1).map{line ->
                val a=line.split(',').map(String::trim)
                require(a.size==5)
                val utc=Instant.parse(a[0])
                val eop=EarthOrientation(a[1].toDouble(),a[2].toDouble(),
                    a[3].toDouble(),a[4].toDouble(),source)
                utc to eop
            }
            return EarthOrientationTable(entries)
        }
    }
}
