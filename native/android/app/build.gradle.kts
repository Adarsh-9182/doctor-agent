import java.security.MessageDigest

plugins {
    id("com.android.application")
}

val catalogAssets = layout.buildDirectory.dir("generated/catalogAssets")
val prepareCatalog by tasks.registering(Copy::class) {
    from(rootProject.file("../../knowledge.json"))
    into(catalogAssets)
}
val bundledModelPath = providers.gradleProperty("bundleStarterModel")
val starterAssets = layout.buildDirectory.dir("generated/starterModelAssets")
val prepareStarterModel by tasks.registering {
    inputs.property("modelPath", bundledModelPath.orElse(""))
    if (bundledModelPath.isPresent) inputs.file(file(bundledModelPath.get()))
    outputs.dir(starterAssets)
    doLast {
        val output = starterAssets.get().asFile
        output.mkdirs()
        val target = output.resolve("starter-qwen3.litertlm")
        if (bundledModelPath.isPresent) {
            val source = file(bundledModelPath.get())
            val digest = MessageDigest.getInstance("SHA-256")
            source.inputStream().use { stream ->
                val buffer = ByteArray(65536)
                var size = stream.read(buffer)
                while (size != -1) { digest.update(buffer, 0, size); size = stream.read(buffer) }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            check(hash == "7900eb4e7362d88c58782c6f9999bb7a129e03544aa98b8f338ea0cc5d8c22c1") { "Starter model checksum mismatch" }
            source.copyTo(target, overwrite = true)
        } else {
            check(!target.exists() || target.delete()) { "Cannot remove stale bundled model asset" }
        }
    }
}

android {
    namespace = "org.doctoragent.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.doctoragent.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.8.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").assets.setSrcDirs(listOf(catalogAssets, starterAssets, file("src/main/legalAssets")))
    androidResources { noCompress += "litertlm" }
}

tasks.named("preBuild") { dependsOn(prepareCatalog, prepareStarterModel) }

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
}
