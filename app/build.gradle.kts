plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

val webOrigin = (findProperty("plutoWebOrigin") as String?) ?: "https://app.example.com"
val webStartUrl = (findProperty("plutoWebStartUrl") as String?) ?: "$webOrigin/"
require(Regex("^https://[a-z0-9]([a-z0-9.-]*[a-z0-9])?(:[0-9]{1,5})?$").matches(webOrigin)) {
    "plutoWebOrigin must be one exact https origin (https://host or https://host:port), got $webOrigin"
}
require(webStartUrl.startsWith("$webOrigin/") && Regex("^[^\"\\\\\\s]+$").matches(webStartUrl)) {
    "plutoWebStartUrl must start with $webOrigin/ and hold no quote, backslash or space, got $webStartUrl"
}

/**
 * The debug network configuration. With -PplutoDemoCa=<certificate file> the debug build also trusts that one authority
 * (a certificate only, never a key); without it, or in release, the platform's own trust is all there is.
 */
abstract class GenerateNetworkConfig : DefaultTask() {
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val authority: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val xml = File(out, "xml").apply { mkdirs() }
        val cert = authority.orNull?.asFile
        val trust =
            if (cert == null) {
                ""
            } else {
                val text = cert.readText()
                require(text.contains("BEGIN CERTIFICATE") && !text.contains("PRIVATE" + " KEY")) {
                    "plutoDemoCa must be a PEM certificate and hold no private key"
                }
                File(out, "raw").apply { mkdirs() }.resolve("pluto_demo_ca.pem").writeText(text)
                "\n    <debug-overrides>\n        <trust-anchors>\n            <certificates src=\"@raw/pluto_demo_ca\" />\n        </trust-anchors>\n    </debug-overrides>"
            }
        File(xml, "network_security_config.xml").writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<network-security-config>\n" +
                "    <base-config cleartextTrafficPermitted=\"false\" />$trust\n</network-security-config>\n",
        )
    }
}

android {
    namespace = "dev.pluto.template"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.pluto.template"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // The only page the WebView may load: one exact https origin, no wildcard (door 4). A build may point the app
        // at another origin with -PplutoWebOrigin=https://host[:port] and -PplutoWebStartUrl=https://host[:port]/path
        // (door 2 of reference-scenario); without them the placeholder stays.
        buildConfigField("String", "WEB_ALLOWLIST", "\"$webOrigin\"")
        buildConfigField("String", "WEB_START_URL", "\"$webStartUrl\"")
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No signing config here: the pipeline signs the bundle with secrets from a GitHub Environment (FR-MOB-02).
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

// The Compose test manifest is a debug-only dependency, so the unit tests run against the debug variant only.
androidComponents {
    beforeVariants { variant ->
        if (variant.buildType == "release") variant.enableUnitTest = false
    }
    onVariants(selector().withBuildType("debug")) { variant ->
        val generate =
            tasks.register<GenerateNetworkConfig>("generateDebugNetworkConfig") {
                (findProperty("plutoDemoCa") as String?)?.let { authority.set(file(it)) }
            }
        variant.sources.res?.addGeneratedSourceDirectory(generate, GenerateNetworkConfig::outputDir)
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

// the generated codec is proven by its conformance test, not by style rules
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    exclude("**/bridge/Bridge.kt", "**/bridge/BridgeConformanceTest.kt")
}

ktlint {
    filter {
        exclude { it.file.path.contains("/bridge/") }
    }
}

kover {
    reports {
        filters {
            excludes {
                // generated code and the thin Android glue that only a device can exercise
                packages(
                    "dev.pluto.template.bridge",
                    "dev.pluto.template.di",
                    "hilt_aggregated_deps",
                    "dagger.hilt.internal.aggregatedroot.codegen",
                )
                classes(
                    "*Hilt_*",
                    "*_Factory",
                    "*_HiltModules*",
                    "*_MembersInjector",
                    "*ComposableSingletons*",
                    "*BuildConfig",
                    "dev.pluto.template.MainActivity",
                    "dev.pluto.template.PlutoApplication",
                    "dev.pluto.template.webview.WebViewListenerRegistry",
                    "dev.pluto.template.webview.WebViewFactory*",
                    "dev.pluto.template.webview.PolicyClient*",
                    "dev.pluto.template.ui.web.PlutoWebView*",
                )
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
            }
        }
        verify {
            rule {
                minBound(80)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.webkit)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.junit4)
    testImplementation(libs.junit5)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.vintage)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
