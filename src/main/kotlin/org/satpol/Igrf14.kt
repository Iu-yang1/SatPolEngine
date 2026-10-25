package org.satpol

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.*

/**
 * IGRF-14 geomagnetic MAIN FIELD through degree/order 13, Schmidt quasi-normalized.
 * Coefficients: IAGA IGRF-14 2020, 2025, and 2025-2030 secular variation [nT/yr].
 *
 * This implementation supports 2020.0 <= decimalYear <= 2030.0 only;
 * the 2020-2025 interval is linear interpolation; 2025-2030 is SV extrapolation.
 * ECEF inputs and outputs use metres and Tesla.
 * Internal reference sphere is 6371.2 km; coefficients are NOT WGS-84 ellipsoid.
 */
class Igrf14(private val rows: List<GaussRow>) : MagneticFieldProvider {
    data class GaussRow(
        val kind: Char, val degree: Int, val order: Int,
        val field2020Nt: Double, val field2025Nt: Double, val secularNtPerYear: Double
    )

    init {
        require(rows.size == 195) { "IGRF-14 truncated coefficient set: ${rows.size} (expected 195)" }
        require(rows.all { it.degree in 1..13 && it.order in 0..it.degree && it.kind in "gh" })
        require(rows.distinctBy { Triple(it.kind,it.degree,it.order) }.size == rows.size)
    }

    override fun teslaEcef(ecefM: Vector3, utc: Instant): Vector3 =
        evaluate(ecefM, decimalYear(utc))

    fun evaluate(ecefM: Vector3, year: Double): Vector3 {
        require(ecefM.isFinite() && year.isFinite())
        require(year in 2020.0..2030.0) { "IGRF-14 current coefficient interval is 2020–2030" }
                val rho = hypot(ecefM.x,ecefM.y)
        val radius = hypot(rho,ecefM.z)
        require(radius > 6_000_000.0 && radius < 50_000_000.0)
        val theta = acos((ecefM.z/radius).coerceIn(-1.0,1.0))
        val s=sin(theta)
        val c=cos(theta)
        val longitude=atan2(ecefM.y,ecefM.x)
        // Unnormalized associated Legendre P_nm(cos(theta)) WITHOUT Condon-Shortley phase.
        // Recursions differentiated analytically in theta.
        val p=Array(14){DoubleArray(14)}
        val dp=Array(14){DoubleArray(14)}
        p[0][0]=1.0
        for (m in 1..13) {
            p[m][m]=(2*m-1)*s*p[m-1][m-1]
            dp[m][m]=(2*m-1)*(c*p[m-1][m-1]+s*dp[m-1][m-1])
        }
        for(m in 0..12) {
            p[m+1][m]=(2*m+1)*c*p[m][m]
            dp[m+1][m]=(2*m+1)*(-s*p[m][m]+c*dp[m][m])
        }
        for(m in 0..13) for(n in m+2..13) {
            p[n][m]=((2*n-1)*c*p[n-1][m]-(n+m-1)*p[n-2][m])/(n-m)
            dp[n][m]=((2*n-1)*(-s*p[n-1][m]+c*dp[n-1][m])-
                (n+m-1)*dp[n-2][m])/(n-m)
        }
        // Schmidt semi-normalization factorial ratio, applied to P and its derivative.
        val norm=Array(14){DoubleArray(14)}
        for(n in 1..13) for(m in 0..n) {
            var fac=if(m==0) 1.0 else 2.0
            for(j in n-m+1..n+m) fac/=j.toDouble()
            norm[n][m]=sqrt(fac)
        }
        val gg=Array(14){DoubleArray(14)}
        val hh=Array(14){DoubleArray(14)}
        for(item in rows){
            val v=if(year<=2025.0) {
                item.field2020Nt+(item.field2025Nt-item.field2020Nt)*(year-2020.0)/5.0
            } else {
                item.field2025Nt+item.secularNtPerYear*(year-2025.0)
            }
            if(item.kind=='g') gg[item.degree][item.order]=v
            else hh[item.degree][item.order]=v
        }
        var br=0.0
        var bt=0.0
        var bf=0.0
        for(n in 1..13){
            val rpow=(6_371_200.0/radius).pow(n+2)
            for(m in 0..n){
                val phase=m*longitude
                val g=gg[n][m]; val h=hh[n][m]
                val amplitude=g*cos(phase)+h*sin(phase)
                val pn=p[n][m]*norm[n][m]
                val dpn=dp[n][m]*norm[n][m]
                br+=(n+1)*rpow*amplitude*pn
                bt-=rpow*amplitude*dpn
                if(m>0){
                    // Azimuth component is well-defined by a limiting value at poles.
                    // Polar east axis itself is ambiguous at the exact pole; reject here.
                    require(abs(s)>1e-10) { "IGRF local east undefined at geographic pole" }
                    bf+=rpow*m*(g*sin(phase)-h*cos(phase))*pn/s
                }
            }
        }
        val el=cos(longitude); val sl=sin(longitude)
        val radial=Vector3(s*el,s*sl,c)
        val south=Vector3(c*el,c*sl,-s)
        val east=Vector3(-sl,el,0.0)
        return (radial*br + south*bt + east*bf)*1e-9
    }

    companion object {
        fun decimalYear(instant: Instant): Double {
            val date=instant.atZone(ZoneOffset.UTC)
            val begin=LocalDate.of(date.year,1,1).atStartOfDay(ZoneOffset.UTC).toInstant()
            val end=LocalDate.of(date.year+1,1,1).atStartOfDay(ZoneOffset.UTC).toInstant()
            val nanos=(instant.epochSecond-begin.epochSecond).toDouble()+instant.nano*1e-9
            return date.year+nanos/(end.epochSecond-begin.epochSecond)
        }

        fun fromCoefficientCsv(csv: String): Igrf14 {
            val rows=csv.lineSequence().filter{it.isNotBlank()&&!it.trim().startsWith("#")}
                .map{
                    val a=it.split(',').map(String::trim)
                    require(a.size==6)
                    GaussRow(a[0].single(),a[1].toInt(),a[2].toInt(),
                        a[3].toDouble(),a[4].toDouble(),a[5].toDouble())
                }.toList()
            return Igrf14(rows)
        }

        fun fromBundledCoefficients(): Igrf14 {
            val s=requireNotNull(Igrf14::class.java.getResourceAsStream("/igrf14-2020-2025-sv.csv")) {
                "Missing bundled official IGRF-14 coefficients"
            }.bufferedReader().use{it.readText()}
            return fromCoefficientCsv(s)
        }
    }
}
