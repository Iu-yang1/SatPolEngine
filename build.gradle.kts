plugins {
    kotlin("jvm") version "2.1.20"
}

group = "io.github.iu-yang1"
version = "0.1.0-SNAPSHOT"

repositories { mavenCentral() }
kotlin { jvmToolchain(17) }

dependencies { testImplementation(kotlin("test-junit5")) }
tasks.test { useJUnitPlatform() }

tasks.register<JavaExec>("orbitVector") {
    group = "verification"
    description = "Emit actual Look4Sat-derived SGP4 TEME state for Vallado cross-check"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.satpol.orbit.OrbitReferenceCli")
}

tasks.register<JavaExec>("igrfVector") {
    group = "verification"
    description = "Emit IGRF-14 ENU field for external IAGA ppigrf cross-validation"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.satpol.IgrfReferenceCli")
}

tasks.register<JavaExec>("realAttitude") {
    group = "verification"
    description = "Verify source-tagged CASSIOPE attitude AEM with the Kotlin engine"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.satpol.orbit.RealAttitudeCli")
    args(project.findProperty("attitudeFile") ?: "real-cassiope.aem",
         project.findProperty("attitudeTime") ?: "2017-06-24T22:32:14Z")
}

tasks.register<JavaExec>("observedFaraday") {
    group = "verification"
    description = "Compare signed IGRF14 Faraday on official IRI and real NOAA-corrected paths"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.satpol.ObservedFaradayCli")
    args(project.findProperty("iriOriginal") ?: "official-profile.tsv",
         project.findProperty("iriFused") ?: "official-fused-profile.tsv")
}

tasks.register<JavaExec>("realTransmitter") {
    group = "verification"
    description = "Same-UTC CASSIOPE real definitive SP3 + attitude with documented RHCP downlink"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.satpol.orbit.RealTransmitterCli")
    args(project.findProperty("orbitFile") ?: "cassiope-orbit.json",
         project.findProperty("aemFile") ?: "real-cassiope.aem")
}
