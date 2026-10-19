import org.gradle.api.tasks.testing.Test
plugins { alias(libs.plugins.kotlin.multiplatform) }
kotlin {
    jvm { testRuns["test"].executionTask.configure { useJUnitPlatform() } }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors.set(true) }
    sourceSets {
        commonMain {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                api(project(":core:model"))
                api(project(":core:solver"))
                api(project(":core:image"))
            }
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        jvmMain {
            resources.srcDir("src/main/resources")
            dependencies { compileOnly(libs.opencv.jvm) }
        }
        jvmTest {
            if (providers.gradleProperty("iosOnly").orNull != "true") kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation(libs.opencv.jvm)
                implementation(kotlin("test-junit5"))
                if (providers.gradleProperty("iosOnly").orNull != "true") {
                    implementation(project(":core:vision"))
                }
            }
        }
    }
}
tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    testLogging { events("failed") }
    maxHeapSize = "2g"

    // Gradle does not forward -D to the forked test JVM, so ExportNormalisedTest would
    // never see it. Passed through explicitly, as in :core:vision.
    systemProperty("dump", providers.systemProperty("dump").getOrElse("false"))
    systemProperty("benchmarkLabelledOnly", providers.systemProperty("benchmarkLabelledOnly").getOrElse("false"))

    // The corpus is an input to these tests even though it is not on the compile path,
    // and Gradle cannot know that. Without it a task stays up to date when a photograph
    // is added, so a new page can break a test and the build still reports success -
    // which is exactly what happened when the newsprint pages arrived. A file tree of a
    // directory that does not exist is simply empty, so CI is unaffected.
    inputs.files(rootProject.fileTree("corpus"), rootProject.fileTree("corpus-labels"))
        .withPropertyName("corpus")
        .optional()

    // A folder of photographs to look at that are not in the corpus - see
    // ScanFolderDumpTest. Empty means the test does nothing.
    systemProperty("scan", providers.systemProperty("scan").getOrElse(""))
    systemProperty("probe", providers.systemProperty("probe").getOrElse(""))

    // Where ScanFolderDumpTest writes the straightened grid of each photograph, for
    // reading by eye. Empty means it writes none.
    systemProperty("write", providers.systemProperty("write").getOrElse(""))
}

if (providers.gradleProperty("iosOnly").orNull != "true") {
    dependencies { add("jvmTestImplementation", testFixtures(project(":core:vision"))) }
}
