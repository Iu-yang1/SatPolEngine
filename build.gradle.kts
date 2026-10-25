plugins {
    kotlin("jvm") version "2.1.20"
}

group = "io.github.iu-yang1"
version = "0.1.0-SNAPSHOT"

repositories { mavenCentral() }
kotlin { jvmToolchain(17) }

dependencies { testImplementation(kotlin("test-junit5")) }
tasks.test { useJUnitPlatform() }
