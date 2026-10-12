// Común a todas las pantallas del móvil: componentes, tema, ayuda, textos (todos los idiomas),
// letras y servicios (descanso, reloj, Health Connect, compartir, avisos, copias). Lo exporta
// con «api» para que cada módulo de función tenga las mismas dependencias.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.bestrong.mobile.shared"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core"))
    api(project(":designsystem"))
    api(project(":data"))
    api(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(libs.hilt.navigation.compose)
    api(libs.androidx.core.ktx)
    api(libs.androidx.activity.compose)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.service)
    api(libs.androidx.work.runtime)
    api(libs.coil.compose)
    api(libs.coil.gif)
    api(libs.zxing.core)
    api(libs.androidx.navigation.compose)
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.material3)
    api(libs.compose.material.icons)
    api(libs.compose.ui.tooling.preview)
    api(libs.material3.adaptive)
    api(libs.material3.adaptive.layout)
    api(libs.material3.adaptive.navigation)
    api(libs.material3.adaptive.navigation.suite)
    api(libs.androidx.window)
    api(libs.health.connect)
    api(libs.play.services.wearable)
    api(libs.wear.remote.interactions)
    api(libs.androidx.fragment)
    api(libs.kotlinx.coroutines.play.services)
    api(libs.kotlinx.coroutines.guava)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}
