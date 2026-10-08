// Identidad visual compartida por el móvil (Material 3) y el reloj (Wear Material 3):
// paleta de marca, colores de estado y espaciados. Sin dependencias de Material.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.bestrong.designsystem"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
}
