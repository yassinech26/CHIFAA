import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// A local build directory also works for checkouts under OneDrive on Windows.
val localSettings = Properties()
rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(localSettings::load)
localSettings.getProperty("chifaa.buildDir")?.let { layout.buildDirectory.set(file(it)) }

android {
    testOptions.animationsDisabled = true
    namespace = "com.example.chifaaapp"
    buildFeatures { buildConfig = true }
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.chifaaapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        val endpoint = providers.gradleProperty("chifaaApi").getOrElse("https://localhost:8443/api/")
        require(endpoint.matches(Regex("https://[A-Za-z0-9.:-]+/[A-Za-z0-9/_-]*/")))
        buildConfigField("String", "PATIENT_API", "\"$endpoint\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            val localEndpoint = localSettings.getProperty("chifaa.debugApi")
            if (localEndpoint != null) {
                require(localEndpoint.matches(Regex("http://localhost:[0-9]{1,5}/api/"))) {
                    "chifaa.debugApi must use http://localhost:<port>/api/"
                }
                buildConfigField("String", "PATIENT_API", "\"$localEndpoint\"")
            }
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation("com.squareup.retrofit2:retrofit-mock:2.11.0")
    androidTestImplementation(libs.ext.junit)
}
