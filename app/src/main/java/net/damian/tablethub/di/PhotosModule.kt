package net.damian.tablethub.di

import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.damian.tablethub.photos.GooglePhotosApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PhotosModule {

    private const val GOOGLE_PHOTOS_BASE_URL = "https://photoslibrary.googleapis.com/"

    @Provides
    @Singleton
    @Named("googlePhotosOkHttp")
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
    fun provideGooglePhotosApi(
        @Named("googlePhotosOkHttp") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): GooglePhotosApi {
        return Retrofit.Builder()
            .baseUrl(GOOGLE_PHOTOS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GooglePhotosApi::class.java)
    }
}
