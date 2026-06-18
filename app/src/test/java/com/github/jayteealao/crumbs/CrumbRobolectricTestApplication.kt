package com.github.jayteealao.crumbs

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Robolectric default Application for :app unit tests.
 *
 * Robolectric boots the manifest's application unless a test overrides it via
 * `@Config(application = ...)`. The production [CrumbApplication.onCreate] eagerly
 * resolves the Hilt `FirebaseAuth` binding (via the auth-gateway entry point),
 * which calls `FirebaseApp.getInstance()` and throws
 * "Default FirebaseApp is not initialized" because Robolectric does not run
 * `FirebaseInitProvider`. That crashed every Robolectric test in :app that did not
 * already pin a plain `application = Application::class`.
 *
 * This lightweight stand-in avoids the production cold-start side effects (which are
 * irrelevant to unit tests) and initializes a dummy [FirebaseApp] so any test code
 * path that constructs a real Firebase-backed dependency still works. No :app test
 * relies on the real application's Hilt graph, so a plain [Application] is sufficient.
 * Registered module-wide via src/test/resources/robolectric.properties; tests that set
 * their own `@Config(application = ...)` keep their explicit override.
 */
class CrumbRobolectricTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("AIzaSyTestKeyForRobolectricUnitTests000000")
                    .setProjectId("crumb-robolectric-test")
                    .build(),
            )
        }
    }
}
