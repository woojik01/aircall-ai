import java.util.Properties
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val playProperties = Properties().apply {
    rootProject.file("config/play.properties").inputStream().use { load(it) }
}
fun playValue(name: String): String = (providers.gradleProperty(name).orNull
    ?: providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
    ?: playProperties.getProperty(name, "")).trim()
fun javaString(value: String): String = "\"" + value.replace("\\", "\\\\")
    .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
val playFieldNames = listOf("AIRCALL_DEVELOPER_NAME", "AIRCALL_SUPPORT_EMAIL",
    "AIRCALL_PRIVACY_POLICY_URL", "AIRCALL_REPORT_ENDPOINT", "AIRCALL_REPORT_RETENTION_DAYS")
if (providers.environmentVariable("AIRCALL_REQUIRE_PLAY_CONFIG").orNull == "true") {
    require(playFieldNames.all { playValue(it).isNotBlank() }) {
        "Complete config/play.properties or matching Actions variables before a Play release."
    }
    listOf("AIRCALL_PRIVACY_POLICY_URL", "AIRCALL_REPORT_ENDPOINT").forEach { name ->
        val uri = URI(playValue(name))
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "$name must be a public HTTPS URL."
        }
    }
    require(playValue("AIRCALL_SUPPORT_EMAIL").matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) {
        "A real public support email is required."
    }
    require(playValue("AIRCALL_REPORT_RETENTION_DAYS").toIntOrNull()?.let { it in 1..365 } == true) {
        "Report retention must be between 1 and 365 days."
    }
}
val requestedVersionCode = providers.gradleProperty("AIRCALL_VERSION_CODE").orNull
    ?: appVersion.getProperty("versionCode")
val releaseStore = providers.environmentVariable("AIRCALL_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("AIRCALL_KEYSTORE_PASSWORD").orNull
val releaseAlias = providers.environmentVariable("AIRCALL_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("AIRCALL_KEY_PASSWORD").orNull
val signingValues = listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword)
require(signingValues.all { it.isNullOrBlank() } || signingValues.all { !it.isNullOrBlank() }) {
    "Release signing requires all four AIRCALL_KEYSTORE/KEY environment values."
}
val hasReleaseSigning = signingValues.all { !it.isNullOrBlank() }
if (providers.environmentVariable("AIRCALL_REQUIRE_RELEASE_SIGNING").orNull == "true") {
    require(hasReleaseSigning) { "Release signing is required; unsigned/debug-signed distribution is forbidden." }
}

android {
    namespace = "com.woojik.aircallai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.woojik.aircallai"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = requestedVersionCode.toIntOrNull()?.takeIf { it in 3..2_100_000_000 }
            ?: error("AIRCALL_VERSION_CODE must be an integer between 3 and 2100000000")
        versionName = appVersion.getProperty("versionName")
        manifestPlaceholders["appLabel"] = "AirCall AI"
        playFieldNames.forEach { name -> buildConfigField("String", name, javaString(playValue(name))) }

        // PRD-09 소셜 로그인 UX: OAuth Client ID는 공개값이므로 빌드 시점 기본값으로 제공한다.
        val githubOAuthClientId = (project.findProperty("GITHUB_OAUTH_CLIENT_ID") as? String)?.trim().orEmpty()
        val googleOAuthClientId = (project.findProperty("GOOGLE_OAUTH_CLIENT_ID") as? String)?.trim().orEmpty()
        buildConfigField("String", "GITHUB_OAUTH_CLIENT_ID", "\"$githubOAuthClientId\"")
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", "\"$googleOAuthClientId\"")
    }

    signingConfigs {
        create("fixedDebug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) create("production") {
            storeFile = file(releaseStore!!)
            require(storeFile!!.isFile) { "Release keystore file is missing." }
            storePassword = releaseStorePassword
            keyAlias = releaseAlias
            keyPassword = releaseKeyPassword
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            require(file("debug.keystore").isFile) { "Fixed debug keystore is missing. Do not regenerate it: updates and OAuth would break." }
            signingConfig = signingConfigs.getByName("fixedDebug")
            versionNameSuffix = "-dev"
            manifestPlaceholders["appLabel"] = "AirCall AI 개발용"
        }
        release {
            // Preserve the installed development package and its Google OAuth registration.
            // A private production signing key uses a separate, permanent identity.
            applicationIdSuffix = ".release"
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("production")
            // PRD-08: Release 빌드에 R8 축소/난독화 적용. LiteRT-LM JNI 유지 규칙은
            // proguard-rules.pro 참조.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    packaging {
        jniLibs { useLegacyPackaging = false }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Gemma 4 Android 모델은 LiteRT-LM의 .litertlm 형식으로 실행한다.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")
    // Google Android AuthorizationClient for Gmail user-data authorization.
    implementation("com.google.android.gms:play-services-auth:21.6.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
