package org.satpol.orbit

import org.satpol.CcsdsAttitude
import org.satpol.Vector3
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Locale

/**
 * Audit real CASSIOPE ITRF<-BODY quaternions without pretending that the
 * body +X reference vector is a documented radio-transmit antenna.
 */
object RealAttitudeCli {
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==2) { "Usage: realAttitude [file.aem] [UTC]" }
        val aem=CcsdsAttitude.parseAemKvn(Files.readString(Path.of(args[0])),
            "CASSIOPE actual AttQuat converted from Calgary CDF",5.0)
        val quaternion=aem.quaternionAt(Instant.parse(args[1]))
            ?:error("CASSIOPE sample outside attitude telemetry interval")
        val v=quaternion.rotate(Vector3(1.0,0.0,0.0)).unit()
        println(String.format(Locale.US,
            "REAL_ATTITUDE_VECTOR=%.12f,%.12f,%.12f",v.x,v.y,v.z))
    }
}
