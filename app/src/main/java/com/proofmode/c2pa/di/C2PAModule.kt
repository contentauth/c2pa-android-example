package com.proofmode.c2pa.di

import android.content.Context
import com.proofmode.c2pa.c2pa_signing.C2PAManager
import com.proofmode.c2pa.c2pa_signing.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object C2PAModule {

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideC2PAManager(
        @ApplicationContext context: Context,
        preferencesManager: PreferencesManager
    ): C2PAManager {
        return C2PAManager(context, preferencesManager)
    }
}
