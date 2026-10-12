import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.baselineprofile)
}

// Firma de release: crea keystore.properties (no se sube a git) con storeFile, storePassword, keyAlias y keyPassword
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.bestrong.mobile"
    compileSdk = 36

    defaultConfig {
        // Debe coincidir con el applicationId del módulo wear para la Data Layer
        applicationId = "com.bestrong"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.3.0"
        // Enlace de «Invítame a un café» (Ko-fi, PayPal…): propiedad donationUrl en gradle.properties o -PdonationUrl=…
        buildConfigField("String", "DONATION_URL", "\"${providers.gradleProperty("donationUrl").getOrElse("")}\"")
        buildConfigField("String", "REPO_URL", "\"https://github.com/JMRMINGORANCE/BeStrong\"")
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
            // Sin keystore: firma de depuración solo si se pide (-PsignWithDebug=true), para releases de prueba en GitHub
            signingConfig = signingConfigs.findByName("release")
                ?: if (providers.gradleProperty("signWithDebug").orNull == "true") signingConfigs.getByName("debug") else null
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                // Robolectric + capturas a pantalla completa consumen mucha memoria
                it.maxHeapSize = "3g"
                // En la CI se ve qué prueba empieza y cuál falla (si una se cuelga, es la última «STARTED»)
                it.testLogging {
                    events("started", "passed", "failed", "skipped")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }
    // Lint revisa también los módulos de los que depende la app
    lint { checkDependencies = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":designsystem"))
    implementation(project(":data"))
    implementation(project(":shared"))
    implementation(project(":feature:workout"))
    implementation(project(":feature:routines"))
    implementation(project(":feature:history"))
    implementation(project(":feature:exercises"))
    implementation(project(":feature:home"))
    implementation(project(":feature:planning"))
    implementation(project(":feature:profile"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    // Códigos QR para compartir rutinas
    implementation(libs.zxing.core)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.material3.adaptive)
    implementation(libs.material3.adaptive.layout)
    implementation(libs.material3.adaptive.navigation)
    implementation(libs.material3.adaptive.navigation.suite)
    implementation(libs.androidx.window)
    implementation(libs.health.connect)
    implementation(libs.play.services.wearable)
    implementation(libs.wear.remote.interactions)
    // play-services-wearable arrastra un Fragment antiguo; la API de ActivityResult necesita >= 1.3
    implementation(libs.androidx.fragment)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.window.testing)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.room.testing)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Anotaciones de Hilt en parámetros del constructor: aplicarlas también a la propiedad (KT-73255)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}
