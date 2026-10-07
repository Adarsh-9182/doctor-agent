plugins {
    id("com.android.application")
}

android {
    namespace = "org.doctoragent.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.doctoragent.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0"
    }
}
