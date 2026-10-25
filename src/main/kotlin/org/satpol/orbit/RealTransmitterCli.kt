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
    fun extract(s:String,key:String):String {
        val expr=Regex("\\\""+key+"\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        return expr.find(s)?.groupValues?.get(1)?:error("Missing '$key' from SP3 JSON")
    }
    fun vec(s:String,key:String):Vector3 {
        val x=Regex("\\\""+key+"\\\"\\s*:\\s*\\[([^\\]]+)]").find(s)?.groupValues?.get(1)
            ?:error("Missing '$key' from SP3 JSON")
        val a=x.split(',').map(String::trim).map(String::toDouble)
        require(a.size==3)
        return Vector3(a[0],a[1],a[2])
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
        println(String.format(Locale.US,
            "REAL_CASSIOPE_JOIN,epoch=%s,selected_panel=%d,off_boresight_deg=%.9f,"+
            "lineal_receiver_mismatch_db=%.9f,position_m=%.2f,angle=UNKNOWN,CWCCW=UNKNOWN",
            instant,link.selectedPanel,link.offBoresightDeg,link.linearReceiverMismatchDb,pos.norm()))
    }
}
