plugins {
    id("com.android.application")
}

val catalogAssets = layout.buildDirectory.dir("generated/catalogAssets")
val prepareCatalog by tasks.registering(Copy::class) {
    from(rootProject.file("../../knowledge.json"))
    into(catalogAssets)
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").assets.setSrcDirs(listOf(catalogAssets))
}

tasks.named("preBuild") { dependsOn(prepareCatalog) }
