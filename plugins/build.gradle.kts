plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:9.1.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "crumbs.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
    }
}
