package org.satpol

import java.time.Instant
import kotlin.math.*

/**
 * Site/path-specific electron density samples produced by a real IRI-family model
 * (e.g. PyIRI exporter). No extrapolation or synthetic fallback is allowed.
 * Coordinates, epoch and model provenance are checked on EVERY lookup.
 */
class IriPathSamples private constructor(
    private val epoch: Instant,
    private val samples: List<Sample>,
    val provenance: String,
    val f107Sfu: Double
): ElectronDensityProvider {
    data class Sample(val ecefM: Vector3, val electronDensityM3: Double)

    init {
        require(provenance.isNotBlank() && f107Sfu.isFinite() && f107Sfu > 0)
        require(samples.isNotEmpty())
        require(samples.all { it.ecefM.isFinite() && it.electronDensityM3.isFinite() && it.electronDensityM3>=0 })
    }

    private var nextIndex=0
    override fun electronsPerCubicMetre(ecefM:Vector3,utc:Instant):Double {
        require(utc==epoch) { "IRI path sample epoch mismatch" }
        require(ecefM.isFinite())
        // The integral evaluates midpoints in order, no spatial interpolant is assumed.
        require(nextIndex<samples.size) { "IRI samples exhausted" }
        val sample=samples[nextIndex]
        require((sample.ecefM-ecefM).norm()<1.0) { "IRI sample path mismatch at index $nextIndex" }
        nextIndex++
        return sample.electronDensityM3
    }

    fun fresh() = IriPathSamples(epoch,samples,provenance,f107Sfu)
    fun count()=samples.size

    companion object {
        /**
         * TSV:
         * # source=PyIRI:version;F107=120.0;time=2026-10-25T00:00:00Z
         * x_m TAB y_m TAB z_m TAB Ne_m-3
         */
        fun parseTsv(content:String):IriPathSamples {
            val lines=content.lineSequence().map(String::trim)
                .filter {it.isNotBlank()}.toList()
            val header=lines.firstOrNull()?:error("IRI sample content empty")
            require(header.startsWith("# source=")) { "Missing IRI provenance" }
            val meta=header.removePrefix("# source=").split(';')
            require(meta.size==3 && meta[1].startsWith("F107=")&&meta[2].startsWith("time="))
            val p=meta[0]; val f=meta[1].removePrefix("F107=").toDouble()
            val time=Instant.parse(meta[2].removePrefix("time="))
            require(lines.getOrNull(1)=="x_m\ty_m\tz_m\tNe_m-3") { "Missing IRI column header" }
            val points=lines.drop(2).map {
                val a=it.split('\t').map(String::toDouble)
                require(a.size==4)
                Sample(Vector3(a[0],a[1],a[2]),a[3])
            }
            return IriPathSamples(time,points,p,f)
        }
    }
}
