package com.drywall.calculator.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    // Repository interfaces will be defined and bound here.
    // Currently, repositories are concrete classes injected directly by Hilt.
    // Step 1: Create interface per repository (e.g., MaterialRepository)
    // Step 2: Bind interface -> implementation in this module
    //
    // Example:
    // @Provides @Singleton
    // fun provideMaterialRepository(db: AppDatabase): MaterialRepository = MaterialRepositoryImpl(db)
    //
    // For now, repositories use @Inject constructor on concrete classes,
    // which Hilt can resolve without explicit bindings.
}
