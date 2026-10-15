plugins { id("com.android.application") }

val natives by configurations.creating
val nativeDir = layout.buildDirectory.dir("generated/jniLibs")
val extractNatives by tasks.registering(Sync::class) {
    into(nativeDir)
    listOf("arm64-v8a", "armeabi-v7a", "x86_64").forEach { abi ->
        from(provider { natives.filter { it.name.contains("natives-$abi") }.map { zipTree(it) } }) {
            include("*.so")
            into(abi)
        }
    }
}
android {
    namespace = "com.stackgame.android"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "com.stackgame.android"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-tower"
    }
    sourceSets.getByName("main").jniLibs.directories.add(nativeDir.get().asFile.path)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
tasks.named("preBuild") { dependsOn(extractNatives) }
dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx:1.14.2")
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.14.2")
    listOf("arm64-v8a", "armeabi-v7a", "x86_64").forEach {
        natives("com.badlogicgames.gdx:gdx-platform:1.14.2:natives-$it")
    }
}
