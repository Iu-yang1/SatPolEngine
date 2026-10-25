package org.satpol.orbit

import org.satpol.CcsdsAttitude
import org.satpol.Vector3
import org.satpol.OrbitStateEcef
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Locale

/** Audit actual uploaded CASSIOPE ITRF<-BODY orientation using Kotlin engine. */
object RealAttitudeCli {
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==2) { "Usage: realAttitude [file.aem] [UTC]" }
        val aem=CcsdsAttitude.parseAemKvn(Files.readString(Path.of(args[0])),
            "CASSIOPE real AttQuat converted from Calgary CDF",5.0)
            .withCalibratedAntennaAxis(Vector3(1.0,0.0,0.0))
        // Only rotation is tested: dummy OrbitStateEcef is not a real spacecraft orbit.
        val dummy=OrbitStateEcef(Vector3(7000000.0,0.0,0.0),Vector3(0.0,7400.0,0.0))
        val sample=aem.axisEcef(dummy,Instant.parse(args[1]))
            ?:error("CASSIOPE sample is outside real telemetry interval or unusable")
        val v=sample.directionEcef
        println(String.format(Locale.US,
            "REAL_ATTITUDE_VECTOR=%.12f,%.12f,%.12f",v.x,v.y,v.z))
    }
}
