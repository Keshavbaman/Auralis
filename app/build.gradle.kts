plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "app.auralis"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.auralis"
        minSdk = 26
        targetSdk = 36
        versionCode = 53
        versionName = "0.7.31"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testBuildType = providers.gradleProperty("auralisTestBuildType").getOrElse("debug")
    buildTypes {
        create("verification") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".verification"
            matchingFallbacks += "debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    buildTypes.create("optimized") {
        initWith(buildTypes.getByName("release"))
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += "release"
        isDebuggable = false
    }
    sourceSets.getByName("verification").apply {
        manifest.srcFile("src/debug/AndroidManifest.xml")
        res.srcDir("src/debug/res")
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { jvmToolchain(17) }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.preview)
    implementation(libs.material3)
    implementation(libs.icons)
    implementation(libs.activity)
    implementation(libs.core)
    implementation(libs.splashscreen)
    implementation(libs.lifecycle)
    implementation(libs.viewmodel)
    implementation(libs.navigation)
    implementation(libs.exoplayer)
    implementation(libs.session)
    implementation(libs.media3.cast)
    implementation(libs.cast.framework)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.serialization.json)
    implementation(libs.datastore)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.coil)
    implementation(libs.documentfile)
    implementation(libs.work)
    implementation(libs.vosk)
    implementation(libs.gson)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    add("verificationImplementation", libs.compose.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.android.test)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.room.testing)
}

tasks
    .matching {
        it.name == "mergeDebugAndroidTestAssets" || it.name == "mergeVerificationAndroidTestAssets"
    }
    .configureEach {
        dependsOn(if (name.contains("Verification")) "kspVerificationKotlin" else "kspDebugKotlin")
    }
