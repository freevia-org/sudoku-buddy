plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure { useJUnitPlatform() }
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors.set(true) }
    sourceSets {
        commonMain {
            kotlin.srcDir("src/main/kotlin")
            dependencies { api(project(":core:model")) }
        }
        jvmTest {
            kotlin.srcDir("src/test/kotlin")
            dependencies { implementation(kotlin("test-junit5")) }
        }
    }
}
