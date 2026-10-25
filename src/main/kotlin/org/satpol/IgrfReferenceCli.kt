package org.satpol

import java.util.Locale

/** Diagnostic tool: evaluate bundled IGRF-14 against an independent model. */
object IgrfReferenceCli {
    @JvmStatic fun main(args:Array<String>) {
        val lat=requireNotNull(System.getenv("SATPOL_LAT_DEG")).toDouble()
        val lon=requireNotNull(System.getenv("SATPOL_LON_DEG")).toDouble()
        val alt=requireNotNull(System.getenv("SATPOL_ALT_KM")).toDouble()
        val year=requireNotNull(System.getenv("SATPOL_YEAR")).toDouble()
        val ground=GroundStation(lat,lon,alt*1000)
        val field=Igrf14.fromBundledCoefficients().evaluate(ground.ecefM(),year)*1e9
        println(String.format(Locale.US,"SATPOL_FIELD=%.9f,%.9f,%.9f",
            field.dot(ground.east()),field.dot(ground.north()),field.dot(ground.up())))
    }
}
