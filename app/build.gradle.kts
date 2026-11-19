plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.eband.aligner"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.eband.aligner"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.webkit:webkit:1.11.0")
}

// Bundle OpenCV.js and Chart.js inside the APK so the app works offline at the radio site.
// They are downloaded once, on the first build. Each file has backup download sites,
// so a temporary outage of one site does not break the build.
val downloadWebLibs by tasks.registering {
    val outDir = file("src/main/assets/www/lib")
    val libs = mapOf(
        "opencv.js" to listOf(
            "https://docs.opencv.org/4.8.0/opencv.js",
            "https://cdn.jsdelivr.net/npm/@techstark/opencv-js@4.8.0-release.10/dist/opencv.js",
            "https://unpkg.com/@techstark/opencv-js@4.8.0-release.10/dist/opencv.js"
        ),
        "chart.umd.min.js" to listOf(
            "https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js",
            "https://unpkg.com/chart.js@4.4.1/dist/chart.umd.min.js"
        )
    )
    outputs.dir(outDir)
    doLast {
        outDir.mkdirs()
        libs.forEach { (name, urls) ->
            val f = File(outDir, name)
            if (f.exists() && f.length() > 1000L) return@forEach
            var ok = false
            for (url in urls) {
                val tmp = File(outDir, "$name.part")
                try {
                    logger.lifecycle("Downloading $url")
                    val conn = uri(url).toURL().openConnection()
                    conn.connectTimeout = 20000
                    conn.readTimeout = 120000
                    conn.getInputStream().use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (tmp.length() > 1000L && tmp.renameTo(f)) { ok = true; break }
                } catch (e: Exception) {
                    logger.warn("Download failed from $url: ${e.message}")
                }
                tmp.delete()
            }
            if (!ok) throw GradleException("Could not download $name from any source")
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(downloadWebLibs) }
