import java.net.URI

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.chifaaapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.chifaaapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_URL", "\"https://localhost/\"")
    }

    buildTypes {
        debug {
            val url = providers.gradleProperty("chifaaDebugApiUrl").orElse("http://10.0.2.2:8080/")
            val uri = URI.create(url.get())
            require(uri.scheme in listOf("https", "http") && uri.host != null && uri.rawUserInfo == null
                && uri.rawQuery == null && uri.rawFragment == null && url.get().endsWith("/"))
            buildConfigField("String", "API_URL", "\"${uri.toASCIIString()}\"")
        }
        release {
            val url = providers.gradleProperty("chifaaReleaseApiUrl").orElse("https://localhost/").get()
            val uri = URI.create(url)
            require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null
                && uri.rawQuery == null && uri.rawFragment == null && url.endsWith("/"))
            buildConfigField("String", "API_URL", "\"${uri.toASCIIString()}\"")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.4")
    implementation("androidx.lifecycle:lifecycle-livedata:2.9.4")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("androidx.room:room-runtime:2.7.2")
    annotationProcessor("androidx.room:room-compiler:2.7.2")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
