plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure { useJUnitPlatform() }
    }
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "SudokuShared"
            isStatic = true
        }
    }
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors.set(true) }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:presentation"))
            implementation(project(":core:model"))
            implementation(project(":core:solver"))
            implementation(project(":core:recognize"))
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmTest.dependencies { implementation(kotlin("test-junit5")) }
    }
}
