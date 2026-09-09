package com.github.jayteealao.crumbs.data.di

import javax.inject.Qualifier

/**
 * Qualifies the long-lived, application-scoped [kotlinx.coroutines.CoroutineScope]
 * provided by the app DI graph (SupervisorJob + Dispatchers.IO).
 *
 * Injecting an *unqualified* `CoroutineScope` is hazardous: any consumer that
 * calls `cancel()` on it would tear down background work for the whole process.
 * Requiring this qualifier makes the application scope explicit at every
 * injection site and prevents an accidental unqualified binding.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
