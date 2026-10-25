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
