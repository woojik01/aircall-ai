plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.woojik.aircallai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.woojik.aircallai"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"

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
    }

    buildTypes {
        debug {
            if (file("debug.keystore").exists()) {
                signingConfig = signingConfigs.getByName("fixedDebug")
            }
        }
        release {
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.2")
    // Google Android AuthorizationClient for Gmail user-data authorization.
    implementation("com.google.android.gms:play-services-auth:21.6.0")

    testImplementation("junit:junit:4.13.2")
    // Android org.json is a stub in local JVM tests; use the reference implementation for parser tests.
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
