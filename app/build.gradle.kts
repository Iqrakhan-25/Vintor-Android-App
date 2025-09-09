
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}
apply(plugin = "kotlin-parcelize")
android {
    namespace = "com.example.vintor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.vintor"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding =true

    }
}
dependencies {
    // AndroidX Core + AppCompat
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Material Components (TextInputLayout, MaterialButton, etc.)
    implementation("com.google.android.material:material:1.11.0")

    // Firebase BoM (manages all Firebase versions automatically)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))

    // Firebase Auth (KTX recommended)
    implementation("com.google.firebase:firebase-auth-ktx")
    // Optional (if you add Firestore or Storage later)
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Glide
    implementation ("com.github.bumptech.glide:glide:4.16.0")
    implementation(libs.androidx.activity)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.navigation.runtime.android)
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.support.annotations)
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Google Cast Framework
    implementation ("com.google.android.gms:play-services-cast-framework:21.4.0")
    // RecyclerView
    implementation ("androidx.recyclerview:recyclerview:1.3.2")

    // Testing (optional)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    implementation ("androidx.fragment:fragment-ktx:1.8.3")
    implementation ("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation ("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation ("androidx.lifecycle:lifecycle-livedata-ktx:2.8.6")

    // OkHttp (Networking for Imgbb Upload)
    implementation ("com.squareup.okhttp3:okhttp:4.12.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // JSON Parsing
    implementation ("org.json:json:20230227")

}

