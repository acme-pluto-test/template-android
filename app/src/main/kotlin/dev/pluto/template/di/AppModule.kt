package dev.pluto.template.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pluto.template.BuildConfig
import dev.pluto.template.webview.WebConfig
import dev.pluto.template.webview.WebOrigin
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // Where the tokens come from is a module of the build type: `TokenModule` in src/debug and in src/release.
    @Provides
    @Singleton
    fun webConfig(): WebConfig = WebOrigin.config(BuildConfig.WEB_ALLOWLIST, BuildConfig.WEB_START_URL)
}
