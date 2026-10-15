plugins {
    kotlin("jvm")
    application
}
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    sourceSets.main { kotlin.srcDir("../src/main/kotlin") }
}
sourceSets.main { resources.srcDir("../src/main/resources") }
dependencies {
    implementation("com.badlogicgames.gdx:gdx:1.12.1")
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:1.12.1")
    implementation("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-desktop")
}
application { mainClass.set("DesktopLauncherKt") }
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
