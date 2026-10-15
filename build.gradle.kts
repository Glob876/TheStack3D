plugins {
    kotlin("jvm") version "2.3.0" apply false
    id("com.android.application") version "9.0.0" apply false
}
allprojects {
    repositories { google(); mavenCentral() }
}
