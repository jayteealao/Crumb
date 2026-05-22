package com.github.jayteealao.crumbs.migration

import com.github.jayteealao.twitter.data.Prefs
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Pulled from the worker rather than wiring @HiltWorker — avoids
// androidx.hilt:hilt-work + worker-factory boilerplate for a single one-shot
// runner.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MigrationEntryPoint {
    fun prefs(): Prefs
    fun firebaseFunctions(): FirebaseFunctions
}
