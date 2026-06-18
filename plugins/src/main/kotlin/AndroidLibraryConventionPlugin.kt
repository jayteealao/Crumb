import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Convention plugin that applies the standard Android library configuration shared
 * across all :core:* and :feature:* modules in the Crumbs project.
 *
 * What this plugin does:
 *   - Applies com.android.library
 *   - Sets compileSdk = 35, minSdk = 24
 *   - Sets compileOptions to Java 17
 *   - Configures Kotlin jvmTarget = JVM_17 (for any Kotlin plugin declared in the
 *     consuming module — kotlin.android OR kotlin.plugin.compose)
 *
 * What the consuming module must still declare:
 *   - The Kotlin plugin (id 'org.jetbrains.kotlin.android' or
 *     id 'org.jetbrains.kotlin.plugin.compose')
 *   - android.namespace
 *   - Any module-specific dependencies
 *
 * Usage (in a module's build.gradle):
 *   plugins {
 *     id 'crumbs.android.library'
 *     id 'org.jetbrains.kotlin.plugin.compose'  // or kotlin.android
 *   }
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                compileSdk = 35

                defaultConfig {
                    minSdk = 24
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }

            // Configure jvmTarget for whichever Kotlin plugin the module declares.
            // Using withPlugin ensures this runs after the plugin is applied and the
            // kotlin extension is registered, regardless of declaration order.
            pluginManager.withPlugin("org.jetbrains.kotlin.android") {
                extensions.configure<KotlinAndroidProjectExtension> {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                }
            }

            pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
                extensions.configure<KotlinAndroidProjectExtension> {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                }
            }
        }
    }
}
