import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Esfera en Watch Face Format: solo recursos (XML), sin código. Se instala como APK aparte.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.bestrong.watchface"
    compileSdk = 36

    defaultConfig {
        // Las esferas WFF deben ir en un paquete propio: <app>.watchfacepush o similar
        applicationId = "com.copidev.bestrong.watchface"
        minSdk = 33 // Watch Face Format v1: Wear OS 4+
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
                ?: if (providers.gradleProperty("signWithDebug").orNull == "true") signingConfigs.getByName("debug") else null
            isMinifyEnabled = false
        }
    }
}
