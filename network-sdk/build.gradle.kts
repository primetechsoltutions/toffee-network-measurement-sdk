

// TODO:  For JitPack
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("maven-publish")
}

android {
    namespace = "com.ptsl.network_sdk"
    compileSdk = 36
    buildFeatures{
        buildConfig = true
    }

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        val baseURL = project.findProperty("BASE_URL") as String? ?: "BASE_URL"
        buildConfigField("String", "BASE_URL", "\"$baseURL\"")

        val token = project.findProperty("TOKEN") as String? ?: "TOKEN"
        buildConfigField("String", "TOKEN", "\"$token\"")

        val sdkVersion = project.findProperty("SdkVersion") as String? ?: "SdkVersion"
        buildConfigField("String", "SdkVersion", "\"$sdkVersion\"")

    }
    publishing {
        // expose both variants for publishing
        singleVariant("release") {}
        singleVariant("debug") {}
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xstring-concat=inline" // Optional: safe string concat for older APIs

    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Hilt and WorkManager
    implementation("com.google.dagger:hilt-android:2.57.1")
    kapt("com.google.dagger:hilt-android-compiler:2.57.1")
    implementation("androidx.hilt:hilt-common:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.2")

    // Room
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // Permissions and location
    implementation("com.guolindev.permissionx:permissionx:1.8.1") {
        exclude(group = "androidx.databinding", module = "viewbinding")
    }
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // NetMonster
    implementation("app.netmonster:core:1.3.0")

    // Networking (Retrofit + OkHttp)
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.1.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")

    //
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3") // Or the latest version
}

// Maven publishing configuration
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.primetechsoltutions"
                artifactId = "mybl-network-measurement-sdk"
                version = "1.0.5"        // 1.0.21
            }

            create<MavenPublication>("debug") {
                from(components["debug"])
                groupId = "com.github.primetechsoltutions"
                artifactId = "mybl-network-measurement-sdk-debug"
                version = "1.0.5"       // 1.0.21
            }
        }
    }
}
