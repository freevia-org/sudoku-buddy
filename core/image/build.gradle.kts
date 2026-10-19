plugins { alias(libs.plugins.kotlin.multiplatform) }
kotlin {
    jvm { testRuns["test"].executionTask.configure { useJUnitPlatform() } }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmTest.dependencies { implementation(kotlin("test-junit5")); implementation(libs.opencv.jvm) }
    }
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors.set(true) }
}
