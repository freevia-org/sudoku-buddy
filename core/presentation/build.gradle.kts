plugins { alias(libs.plugins.kotlin.multiplatform) }
kotlin {
    jvm { testRuns["test"].executionTask.configure { useJUnitPlatform() } }
    iosArm64(); iosSimulatorArm64(); iosX64()
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors.set(true) }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:solver"))
            api(project(":core:recognize"))
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmTest.dependencies { implementation(kotlin("test-junit5")) }
    }
}
