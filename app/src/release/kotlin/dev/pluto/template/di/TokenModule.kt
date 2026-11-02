package dev.pluto.template.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pluto.template.data.InMemoryTokenProvider
import dev.pluto.template.domain.TokenProvider
import javax.inject.Singleton

/** Release: the fake login of the template, until the product's real one replaces it. There is no development token source here. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TokenModule {
    @Binds
    @Singleton
    abstract fun tokenProvider(impl: InMemoryTokenProvider): TokenProvider
}
