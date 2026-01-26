package net.damian.tablethub.di

import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.damian.tablethub.plex.PlexAuthApi
import net.damian.tablethub.plex.PlexServerApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlexModule {

    private const val PLEX_TV_BASE_URL = "https://plex.tv/api/v2/"

    @Provides
    @Singleton
    @Named("plexOkHttp")
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun providePlexAuthApi(
        @Named("plexOkHttp") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): PlexAuthApi {
        return Retrofit.Builder()
            .baseUrl(PLEX_TV_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PlexAuthApi::class.java)
    }

    @Provides
    @Singleton
    fun providePlexServerApiFactory(
        @Named("plexOkHttp") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): PlexServerApiFactory {
        return PlexServerApiFactory(okHttpClient, moshi)
    }
}

/**
 * Factory to create PlexServerApi instances for different server URLs.
 */
class PlexServerApiFactory(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) {
    private val apiCache = mutableMapOf<String, PlexServerApi>()

    fun create(serverUrl: String): PlexServerApi {
        return apiCache.getOrPut(serverUrl) {
            val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(PlexServerApi::class.java)
        }
    }
}
