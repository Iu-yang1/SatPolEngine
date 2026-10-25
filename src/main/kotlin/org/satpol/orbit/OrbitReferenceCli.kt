package org.satpol.orbit

import java.time.Instant
import java.util.Locale

/** Diagnostic CLI for independent numerical comparison with Vallado SGP4. */
object OrbitReferenceCli {
    @JvmStatic fun main(args:Array<String>) {
        val l1=requireNotNull(System.getenv("SATPOL_TLE1"))
        val l2=requireNotNull(System.getenv("SATPOL_TLE2"))
        val time=Instant.parse(requireNotNull(System.getenv("SATPOL_EPOCH_UTC")))
        val state=TleStateBridge.propagateTeme(TleStateBridge.parse(l1,l2),time)
        val p=state.positionKm;val v=state.velocityKmPerSecond
        println(String.format(Locale.US,
            "SATPOL_VECTOR=%.12f,%.12f,%.12f,%.12f,%.12f,%.12f",
            p.x,p.y,p.z,v.x,v.y,v.z))
    }
}
