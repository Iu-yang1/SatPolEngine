package org.satpol.orbit

import java.time.Instant
import org.satpol.EarthOrientation
import org.satpol.OrbitStateEcef
import org.satpol.TemeFrames
import org.satpol.TemeStateKm

/** Parses ordinary 2-line GP data; TLE checksums are validated. */
object TleStateBridge {
    private fun checkLine(line: String, head: Char) {
        require(line.length >= 69 && line[0] == head) { "Expected NORAD line $head" }
        val body=line.substring(0,68)
        val sum=body.sumOf { c -> when {
            c in '0'..'9' -> c-'0'
            c=='-' -> 1
            else -> 0
        } }
        val checksum=line[68].digitToIntOrNull()
        require(checksum == sum%10) { "TLE checksum mismatch on line $head" }
    }

    private fun exponentField(value: String): Double {
        val s=value.trim().replace(" ","")
        if(s.isEmpty()) return 0.0
        require(s.length >= 7) { "Invalid TLE assumed-decimal exponent" }
        val mant=s.substring(0,s.length-2)
        val exponent=s.takeLast(2).toInt()
        val sign=if(mant.startsWith("-")) -1.0 else 1.0
        val digits=mant.removePrefix("+").removePrefix("-")
        return sign * ("0."+digits).toDouble() * Math.pow(10.0,exponent.toDouble())
    }

    fun parse(line1:String,line2:String,name:String="UNKNOWN"): OrbitalData {
        checkLine(line1,'1'); checkLine(line2,'2')
        val cat1=line1.substring(2,7).trim()
        val cat2=line2.substring(2,7).trim()
        require(cat1==cat2 && cat1.all { it.isDigit() }) { "NORAD catalog mismatch/invalid alpha-5 TLE" }
        val cat=cat1.toInt()
        return OrbitalData(
            name=name, epoch=line1.substring(18,32).trim().toDouble(),
            meanmo=line2.substring(52,63).trim().toDouble(),
            eccn=("0."+line2.substring(26,33).trim()).toDouble(),
            incl=line2.substring(8,16).trim().toDouble(),
            raan=line2.substring(17,25).trim().toDouble(),
            argper=line2.substring(34,42).trim().toDouble(),
            meanan=line2.substring(43,51).trim().toDouble(),
            catnum=cat,
            bstar=exponentField(line1.substring(53,61))
        )
    }

    fun propagateTeme(data:OrbitalData,time:Instant):TemeStateKm =
        data.getObject().getTemeState(time.toEpochMilli())

    fun propagateItrf(
        data:OrbitalData,time:Instant,eop:EarthOrientation
    ): OrbitStateEcef = TemeFrames.toEcef(propagateTeme(data,time),time,eop)
}
