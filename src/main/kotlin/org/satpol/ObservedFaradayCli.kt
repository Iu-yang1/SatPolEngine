package org.satpol

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Locale
import kotlin.math.*

/**
 * Scientific trace: IRI-2026 background vs GloTEC-F2-corrected electron density
 * with bundled IGRF-14, along the exact same finite synthetic research ray.
 * This tool estimates Faraday rotation, NOT a measured spacecraft POL axis.
 */
object ObservedFaradayCli {
    private fun readMidpoints(raw:String):List<Vector3> {
        val lines=raw.lines().filter { it.isNotBlank() }
        require(lines.size>=5)
        require(lines[1]=="x_m\ty_m\tz_m\tNe_m-3")
        return lines.drop(2).map {
            val parts=it.split('\t')
            require(parts.size==4)
            Vector3(parts[0].toDouble(),parts[1].toDouble(),parts[2].toDouble())
        }
    }

    private fun epoch(raw:String): Instant {
        val header=raw.lineSequence().first().trim()
        require(header.startsWith("# source="))
        val e=header.split(';').single { it.startsWith("time=") }
        return Instant.parse(e.substringAfter("time="))
    }

    @JvmStatic fun main(args:Array<String>) {
        require(args.size==2) { "Usage: observedFaraday original.tsv corrected.tsv" }
        val orig=Files.readString(Path.of(args[0]))
        val corr=Files.readString(Path.of(args[1]))
        val a=readMidpoints(orig)
        val b=readMidpoints(corr)
        require(a.size==b.size)
        val n=a.size
        require(n>=2)
        require(a.indices.all { (a[it]-b[it]).norm()<0.01 }) { "Observation path geometry differs" }
        val step=a[1]-a[0]
        require(step.norm()>1.0)
        require(a.indices.all { i->
            (a[i]-(a[0]+step*i.toDouble())).norm()<0.01
        }) { "Nonuniform ray midpoint geometry" }
        val sat=a[0]-step*0.5
        val receiver=a.last()+step*0.5
        val t=epoch(orig)
        require(epoch(corr)==t) { "Official IRI and NOAA observation epoch mismatch" }
        val geomagnetic=Igrf14.fromBundledCoefficients()
        val raw=IriPathSamples.parseTsv(orig)
        val fused=IriPathSamples.parseTsv(corr)
        require(raw.count()==n && fused.count()==n)
        for(frequency in listOf(145e6,435e6)){
            val original=FaradayIntegrator.integrate(sat,receiver,t,frequency,raw.fresh(),
                geomagnetic,raw.provenance,n)
            val observed=FaradayIntegrator.integrate(sat,receiver,t,frequency,fused.fresh(),
                geomagnetic,fused.provenance,n)
            require(original.angleDeg.isFinite() && observed.angleDeg.isFinite())
            require(original.slantTecElectronsPerM2.isFinite() && observed.slantTecElectronsPerM2.isFinite())
            println(String.format(Locale.US,
                "FARADAY_DATA,frequency_mhz=%.1f,iri_angle_deg=%.12f,fused_angle_deg=%.12f,"+
                "iri_stec_tecu=%.12f,fused_stec_tecu=%.12f,epoch=%s",
                frequency/1e6,original.angleDeg,observed.angleDeg,
                original.slantTecElectronsPerM2/1e16,
                observed.slantTecElectronsPerM2/1e16,t.toString()))
        }
    }
}
