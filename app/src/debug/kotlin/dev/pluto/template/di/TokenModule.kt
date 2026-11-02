package dev.pluto.template.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pluto.template.debug.DevTokenProvider
import dev.pluto.template.debug.HttpsTokenFetch
import dev.pluto.template.debug.TokenFetch
import dev.pluto.template.domain.TokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** Debug: tokens come from the development issuer the reference scenario runs on the developer's machine. Never in release. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TokenModule {
    @Binds
    @Singleton
    abstract fun tokenProvider(impl: DevTokenProvider): TokenProvider

    companion object {
        @Provides
        @Singleton
        fun fetch(): TokenFetch = HttpsTokenFetch()

        @Provides
        @Singleton
        fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
