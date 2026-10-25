package org.satpol.orbit

import org.satpol.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Locale
import kotlin.math.*

/** Scientific-only, measured spacecraft quaternion + definitive SP3 ECEF position.
 * A ground station is chosen SYNTHETICALLY at spacecraft subpoint in this test,
 * not a recorded ground reception. Circular polarization yields no POL optimum.
 */
object RealTransmitterCli {
    private fun valueLine(s:String,key:String):String =
        s.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("\"$key\"") }
            ?.substringAfter(':')?.trim()?.trimEnd(',')
            ?:error("Missing '$key' from SP3 JSON")

    fun extract(s:String,key:String):String =
        valueLine(s,key).removeSurrounding("\"")

    fun vec(s:String,key:String):Vector3 {
        val tail=s.substringAfter("\"$key\"",missingDelimiterValue="")
        require(tail.isNotEmpty()) { "Missing '$key' coordinate vector" }
        val opening=tail.indexOf('[')
        val closing=tail.indexOf(']',opening+1)
        require(opening>=0 && closing>opening) { "Invalid JSON array for $key" }
        val values=tail.substring(opening+1,closing).split(',')
            .map(String::trim).map(String::toDouble)
        require(values.size==3)
        return Vector3(values[0],values[1],values[2])
    }
    @JvmStatic fun main(args:Array<String>){
        require(args.size==2){"Usage realTransmitter realOrbit.json realAttitude.aem"}
        val sp3=Files.readString(Path.of(args[0]))
        val instant=Instant.parse(extract(sp3,"time_utc"))
        require(extract(sp3,"reference_frame")=="ITRF / ECEF")
        val pos=vec(sp3,"position_ecef_m")
        val vel=vec(sp3,"velocity_ecef_m_s")
        val q=CcsdsAttitude.parseAemKvn(Files.readString(Path.of(args[1])),
            "actual CASSIOPE one-second attitude, Calgary archival dataset",5.0)
            .quaternionAt(instant)?:error("Missing real quaternion at SP3 epoch")
        val orbit=OrbitStateEcef(pos,vel)
        // Mathematical test station: point on WGS84 surface below position.
        val lon=Math.toDegrees(atan2(pos.y,pos.x))
        val lat=Math.toDegrees(atan2(pos.z,hypot(pos.x,pos.y)))
        val station=GroundStation(lat,lon).ecefM()
        val tx=CircularPatchTransmitter.cassiopeSband()
        val link=tx.modelAt(orbit.positionM,station,q,instant,instant)
        require(link.polarizationAngleDeg==null && link.recommendedRotation==RotationDirection.UNKNOWN)
        require(link.linearReceiverMismatchDb>3.0 && link.linearReceiverMismatchDb<3.02)
        val bodyX=q.rotate(Vector3(1.0,0.0,0.0))
        val linear=PolarizationEngine.solveIdealDipole(
            GroundStation(lat,lon),orbit.positionM,bodyX,
            AttitudeEvidence.ASSUMED,
            FaradayEstimate(0.0,0.0,"EXPLICIT VACUUM COUNTERFACTUAL / NO IONOSPHERE",1))
        // This ideal dipole DOES NOT EXIST as the published CASSIOPE S-band
        // transmit antenna. It is an independent algorithm verification target.
        println(String.format(Locale.US,
            "COUNTERFACTUAL_LINEAR,angle_deg=%.12f,evidence=SYNTHETIC_BODY_X_VACUUM,"+
            "status=%s",
            requireNotNull(linear.targetAngleDeg),linear.status))
        println(String.format(Locale.US,
            "REAL_CASSIOPE_JOIN,epoch=%s,selected_panel=%d,off_boresight_deg=%.9f,"+
            "lineal_receiver_mismatch_db=%.9f,position_m=%.2f,angle=UNKNOWN,CWCCW=UNKNOWN",
            instant,link.selectedPanel,link.offBoresightDeg,link.linearReceiverMismatchDb,pos.norm()))
    }
}
