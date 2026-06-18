package com.github.jayteealao.crumbs.di

import com.github.jayteealao.crumbs.auth.AuthGateway
import com.github.jayteealao.crumbs.auth.CredentialManagerCoordinator
import com.github.jayteealao.crumbs.auth.FirebaseAuthGateway
import com.github.jayteealao.crumbs.auth.RealCredentialManagerCoordinator
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Pair of modules co-located in one file (mirrors the feature/twitter style
// in ServiceModule.kt). Providers needs an object, bindings needs an abstract
// class — Hilt cannot mix the two in a single declaration.

@Module
@InstallIn(SingletonComponent::class)
object FirebaseProviders {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = Firebase.firestore

    // Region must match the deployed functions; the X OAuth + dailyPoll +
    // triggerPoll surface lives in europe-west2.
    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions =
        FirebaseFunctions.getInstance("europe-west2")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseAuthBindings {
    @Binds
    @Singleton
    abstract fun bindAuthGateway(impl: FirebaseAuthGateway): AuthGateway

    @Binds
    @Singleton
    abstract fun bindCredentialManagerCoordinator(
        impl: RealCredentialManagerCoordinator,
    ): CredentialManagerCoordinator
}
