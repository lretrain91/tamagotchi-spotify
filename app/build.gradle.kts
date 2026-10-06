plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Numéro de build GitHub Actions → chaque APK est une mise à jour du précédent.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "fr.lretrain.melopet"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.lretrain.melopet"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    // Clé de signature fixe (versionnée) pour pouvoir installer les mises à jour
    // par-dessus l'ancienne version sans perdre sa créature.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
