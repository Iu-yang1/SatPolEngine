package org.satpol

import java.time.Instant
import kotlin.math.*

/**
 * Traceable real spacecraft transmitter characteristics. The boresight is an
 * antenna panel normal, not a fabricated calibrated complex far-field pattern.
 */
data class CircularPatchTransmitter(
    val spacecraftId:String,
    val frequencyHz:Double,
    val bodyPanelNormals:List<Vector3>,
    val rightHandCircular:Boolean,
    val documentation:String
) {
    init {
        require(spacecraftId.isNotBlank() && documentation.isNotBlank())
        require(frequencyHz.isFinite() && frequencyHz>0)
        require(bodyPanelNormals.isNotEmpty())
        require(bodyPanelNormals.all { it.isFinite() && abs(it.norm()-1.0)<1e-9 })
    }

    data class LinkGeometry(
        val selectedPanel:Int,
        val boresightAlignment:Double,
        val offBoresightDeg:Double,
        val polarizationAngleDeg:Double?,
        val linearReceiverMismatchDb:Double,
        val recommendedRotation:RotationDirection,
        val source:String,
        val attitudeEpoch:Instant,
        val orbitEpoch:Instant
    )

    /**
     * For ideal CP and an ideal linear receive feed: 1/2 received power (-3.0103 dB)
     * INDEPENDENT of feed roll angle. This does not claim a perfect real antenna
     * axial ratio, antenna pattern, multipath or physical emission during this pass.
     *
     * Orbit and real quaternion must both refer to the exact same UTC epoch.
     */
    fun modelAt(
        satelliteEcef:Vector3,
        groundEcef:Vector3,
        quaternionBodyToEcef:Quaternion,
        orbitTimeUtc:Instant,
        attitudeTimeUtc:Instant
    ):LinkGeometry {
        require(orbitTimeUtc==attitudeTimeUtc) { "Unsynchronized orbit and attitude" }
        require(satelliteEcef.isFinite() && groundEcef.isFinite())
        val los=(groundEcef-satelliteEcef).unit()
        val candidate=bodyPanelNormals.map{ quaternionBodyToEcef.rotate(it).unit().dot(los) }
        val idx=candidate.indices.maxByOrNull{candidate[it]}?:error("No panel normals")
        val dot=candidate[idx]
        require(dot.isFinite())
        return LinkGeometry(idx,dot,Math.toDegrees(acos(dot.coerceIn(-1.0,1.0))),
            null,10.0*log10(2.0),RotationDirection.UNKNOWN,
            documentation,attitudeTimeUtc,orbitTimeUtc)
    }

    companion object {
        /**
         * Actual mission design: 2231.5 MHz RHCP S-band downlink, top (-Z)
         * and bottom (+Z) patch antennas. See original Swarm-E operations report.
         * No assertion about which patch was transmitting at the particular epoch.
         */
        fun cassiopeSband()=CircularPatchTransmitter(
            "CASSIOPE / Swarm-E",2_231_500_000.0,
            listOf(Vector3(0.0,0.0,-1.0),Vector3(0.0,0.0,1.0)),
            true,
            "CSA Swarm-E Final Operations Report section 2.1.3; " +
            "https://epop.phys.ucalgary.ca/wp-content/Swarm-E_Final_Operations_Report.pdf"
        )
    }
}
