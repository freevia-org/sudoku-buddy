import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.freevia.sudokubuddy"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.freevia.sudokubuddy"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // CI supplies a build number so every distributed build is distinct;
        // locally it stays 1.
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toIntOrNull()
            ?.takeIf { it in 1..2_100_000_000 }
            ?: error("BUILD_NUMBER must be a positive Play version code, at most 2100000000")
        versionName = "1.0.1"
        buildConfigField("String", "MISREAD_SUBMISSION_ENDPOINT", "\"\"")
    }

    sourceSets.getByName("androidTest").assets.srcDir("../iosApp/TestFixtures")

    // Opt-in local diagnostic build only; the production APK never contains this shim.
    providers.gradleProperty("emulatorHwcapShim").orNull?.let { shim ->
        require(gradle.startParameter.taskNames.none { "release" in it.lowercase() }) {
            "The local emulator capability workaround must not be used for Release builds"
        }
        sourceSets.getByName("debug").jniLibs.srcDir("$shim/jniLibs")
        sourceSets.getByName("debug").resources.srcDir("$shim/resources")
        packaging.jniLibs.useLegacyPackaging = true
    }


    // OpenCV ships native libraries for four ABIs. Only arm64 matters for real phones,
    // and dropping the rest takes roughly 60MB out of the APK.
    splits {
        abi {
            isEnable = true
            reset()
            val localAbi = providers.gradleProperty("localApkAbi").orElse("arm64-v8a").get()
            require(localAbi in setOf("arm64-v8a", "x86_64")) { "Unsupported local APK ABI" }
            include(localAbi)
            isUniversalApk = false
        }
    }

    /**
     * The key the release is signed with, when one has been provided.
     *
     * Android will not update an installed app whose signature has changed, so a release
     * signed with a throwaway key cannot be updated at all - it has to be uninstalled
     * first, taking every puzzle, photograph and setting with it. That is exactly what was
     * happening: releases were signed with the *debug* config, and CI runners have no
     * debug keystore, so the build generated a fresh one every time. Two consecutive
     * builds were signed by two different certificates.
     *
     * Falls back to debug when no keystore is supplied, so a local build still works;
     * [checkReleaseSigning] is what stops that fallback reaching testers unnoticed.
     */
    val keystore = System.getenv("SIGNING_KEYSTORE")?.takeIf { File(it).isFile }

    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = File(keystore)
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        /**
         * Installed beside the distributed build rather than over it.
         *
         * A debug build cannot replace a release one anyway - the signature differs, so
         * Android refuses the update - and the only way to force it is to uninstall,
         * which takes every puzzle, photograph and setting with it. That is too high a
         * price for looking at a layout, and it is a price that gets paid by whoever
         * happens to have the phone. Its own id means both can sit on the same device.
         */
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            val endpoint = providers.gradleProperty("misreadSubmissionEndpoint")
                .orElse("").get().replace("\\", "\\\\").replace("\"", "\\\"")
            buildConfigField("String", "MISREAD_SUBMISSION_ENDPOINT", "\"$endpoint\"")
        }

        release {
            isMinifyEnabled = false
            val endpoint = providers.gradleProperty("misreadSubmissionEndpoint")
                .orElse("https://sudoku-buddy-feedback.antoni-ivanov.workers.dev/v1/reports")
                .get().replace("\\", "\\\\").replace("\"", "\\\"")
            buildConfigField("String", "MISREAD_SUBMISSION_ENDPOINT", "\"$endpoint\"")
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Every picker language must be available immediately, including offline.
    bundle { language { enableSplit = false } }

    testOptions { unitTests.all { it.useJUnitPlatform() } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/LICENSE*")
    }
}

// The core modules have failed on warnings from the start; the app did not, and had
// quietly accumulated three deprecated arrow icons that do not mirror in a right-to-left
// layout and a LocalLifecycleOwner that had moved. Both are the kind of thing a warning
// is for and nobody reads warnings that do not stop anything.
kotlin {
    compilerOptions { allWarningsAsErrors.set(true) }
}

dependencies {
    implementation(project(":core:presentation"))
    implementation(project(":core:model"))
    implementation(project(":core:solver"))
    implementation(project(":core:vision"))
    implementation(project(":core:recognize"))

    implementation(libs.opencv.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Supplies the non-deprecated LocalLifecycleOwner the camera screen needs.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

/**
 * Refuses to distribute a release signed with a throwaway key.
 *
 * A debug-signed release installs once and can never be updated, because the key differs
 * on every machine that builds it - so each new version arrives as an uninstall, and the
 * tester loses everything they had. That is invisible from the build log and obvious only
 * weeks later, which is exactly the kind of thing worth failing the build over.
 *
 * Only the upload is gated. Building and testing a release locally without a keystore
 * stays as easy as it was.
 */
tasks.register("checkReleaseSigning") {
    group = "verification"
    description = "Check the release is signed with a stable key before it goes to testers"
    val named = System.getenv("SIGNING_KEYSTORE")
    doLast {
        // A keystore named but not there is a broken setup, and shipping past it would
        // sign with the debug key while looking configured. That fails.
        check(named == null || File(named).isFile) {
            "SIGNING_KEYSTORE points at $named, which is not a file. The release would " +
                "fall back to the debug key without saying so. See docs/signing.md."
        }

        // Now that a key exists, no key is a fault rather than a state of affairs. This
        // warned while there was none, so as not to take away the only route a build had
        // to a phone; stopping the upload is the right answer once the alternative is
        // simply to set the secrets. A release testers cannot update is worse than no
        // release: it costs them everything they had.
        check(named != null) {
            "This release would be signed with the debug key, which is generated afresh " +
                "on every machine, so testers cannot update over it - they must uninstall " +
                "and lose their puzzles and photographs. The signing secrets are missing. " +
                "See docs/signing.md."
        }
        val missing = listOf(
            "SIGNING_KEYSTORE_PASSWORD", "SIGNING_KEY_ALIAS", "SIGNING_KEY_PASSWORD",
        ).filter { System.getenv(it).isNullOrBlank() }
        check(missing.isEmpty()) {
            "Missing release signing configuration: ${missing.joinToString()}. See docs/signing.md."
        }
    }
}

/**
 * Fails fast when the Play release notes are too long for Play Console to accept.
 *
 * Play caps "What's new" at 500 characters per language and refuses the release form
 * beyond it. That refusal arrives in the console, after a candidate has been built,
 * signed and audited - so this runs as part of `check` instead, where it costs
 * milliseconds and fails before anything has been compiled.
 */
val releaseNotes = rootProject.layout.projectDirectory.file("docs/play-release-notes-en-US.txt")

tasks.register("checkReleaseNotes") {
    group = "verification"
    description = "Check the Play release notes fit inside Play Console's limit"
    val notes = releaseNotes
    inputs.file(notes)
    doLast {
        // Counted in characters rather than bytes, which is how Play counts them.
        val text = notes.asFile.readText().trim()
        val length = text.codePointCount(0, text.length)
        check(length in 1..500) {
            "docs/play-release-notes-en-US.txt is $length characters; Play accepts " +
                "1 to 500 for a language. Say what changed in this release and move the " +
                "rest to docs/changelog.md."
        }
    }
}

tasks.named("check") { dependsOn("checkReleaseNotes") }

/**
 * Refuses to ship native libraries that are not 16 KB page aligned.
 *
 * Play requires this of anything with native code, and a library that fails it does not
 * misbehave here - it fails to load on a 16 KB device, which is a phone this build was
 * never run on. OpenCV 4.11.0 was exactly that case: its own libopencv_java4.so was
 * aligned correctly while the libc++_shared.so packaged beside it was still on 4 KB, so
 * the fault sat in a dependency's packaging rather than anywhere in this repository.
 *
 * Reading the ELF program headers is the whole check: every PT_LOAD segment of every
 * 64-bit library has to declare an alignment of at least 16384. Nothing else in the build
 * would notice, which is the reason to look.
 */
tasks.register("checkNativeAlignment") {
    group = "verification"
    description = "Check every native library is 16 KB page aligned, as Play requires"

    // Inspecting the built APK means depending on the build that makes it. Without this
    // Gradle is free to run the check first and pass on a stale APK from an earlier
    // version - which it duly did, reporting success on libraries it had not looked at.
    dependsOn("assembleRelease")

    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    inputs.dir(apkDir)
    doLast {
        val apks = apkDir.get().asFile.listFiles { f: File -> f.extension == "apk" }.orEmpty()
        check(apks.isNotEmpty()) { "No release APK to inspect in ${apkDir.get().asFile}." }

        val misaligned = mutableListOf<String>()
        for (apk in apks) {
            ZipFile(apk).use { zip ->
                for (entry in zip.entries().asSequence()) {
                    if (!entry.name.endsWith(".so")) continue
                    val bytes = zip.getInputStream(entry).readBytes()
                    // 64-bit ELF only: the 16 KB rule applies to the 64-bit ABIs.
                    if (bytes.size < 64) continue
                    val elf = bytes[0] == 0x7f.toByte() && bytes[1] == 'E'.code.toByte() &&
                        bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte()
                    if (!elf || bytes[4].toInt() != 2) continue

                    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    val headerOffset = buffer.getLong(0x20)
                    val entrySize = buffer.getShort(0x36).toInt()
                    val count = buffer.getShort(0x38).toInt()
                    for (i in 0 until count) {
                        val at = (headerOffset + i * entrySize).toInt()
                        if (buffer.getInt(at) != 1) continue          // PT_LOAD only
                        val align = buffer.getLong(at + 48)
                        if (align < 16384) {
                            misaligned += "${entry.name} in ${apk.name} is aligned to " +
                                "$align, not 16384"
                        }
                    }
                }
            }
        }
        check(misaligned.isEmpty()) {
            "These native libraries would fail to load on a 16 KB page device, and Play " +
                "rejects them:\n  " + misaligned.distinct().joinToString("\n  ") +
                "\nThe alignment comes from whoever packaged the library, so the fix is " +
                "normally a dependency version rather than anything here."
        }
    }
}
