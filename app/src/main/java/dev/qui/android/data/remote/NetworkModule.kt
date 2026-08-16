/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.data.remote

import android.annotation.SuppressLint
import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideSessionStore(@ApplicationContext context: Context): SessionStore =
        SessionStore(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(session: SessionStore): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // SSE streams are long-lived; a read timeout would kill them mid-flight,
            // so the stream client overrides this per-call.
            .retryOnConnectionFailure(true)
            .cookieJar(SessionCookieJar(session))
            .addInterceptor(AuthInterceptor(session))

        if (runBlocking { session.trustAllCerts() }) {
            builder.applyTrustAllCerts()
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofitFactory(
        client: OkHttpClient,
        json: Json,
        session: SessionStore,
    ): QuiApiProvider = QuiApiProvider(client, json, session)

    @Provides
    @Singleton
    fun provideStreamClient(
        client: OkHttpClient,
        json: Json,
        session: SessionStore,
    ): QuiStreamClient = QuiStreamClient(client, json, session)
}

/**
 * qui's base URL is only known after the user configures it, so Retrofit instances are
 * built lazily and rebuilt whenever the stored address changes.
 */
@Singleton
class QuiApiProvider(
    private val client: OkHttpClient,
    private val json: Json,
    private val session: SessionStore,
) {
    @Volatile
    private var cachedUrl: String? = null

    @Volatile
    private var cachedApi: QuiApi? = null

    suspend fun api(): QuiApi {
        val url = session.currentServerUrl()
            ?: throw IllegalStateException("No qui server configured")

        cachedApi?.let { if (cachedUrl == url) return it }

        return synchronized(this) {
            cachedApi?.takeIf { cachedUrl == url } ?: build(url).also {
                cachedUrl = url
                cachedApi = it
            }
        }
    }

    /** Builds a client for an address that is not stored yet (used by the login screen). */
    fun apiFor(baseUrl: String): QuiApi = build(SessionStore.normalizeBaseUrl(baseUrl))

    fun invalidate() {
        synchronized(this) {
            cachedApi = null
            cachedUrl = null
        }
    }

    private fun build(baseUrl: String): QuiApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(QuiApi::class.java)
}

/**
 * Password login authenticates by session cookie, which OkHttp would otherwise drop
 * between calls. Captured cookies are written back to [SessionStore] so the session
 * survives process death, which is what makes "stay signed in" work.
 */
class SessionCookieJar(private val session: SessionStore) : okhttp3.CookieJar {

    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
        if (cookies.isEmpty()) return
        val header = cookies.joinToString("; ") { "${it.name}=${it.value}" }
        runBlocking { session.setCookie(header) }
    }

    // The stored header is replayed by AuthInterceptor, so nothing is returned here;
    // doing both would duplicate the Cookie header.
    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> = emptyList()
}

/**
 * Attaches whichever credential is stored. qui checks `X-API-Key` first, then the
 * session cookie, so sending both is safe and keeps the app working right after
 * a password login and after an API key is issued.
 */
class AuthInterceptor(private val session: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val builder = chain.request().newBuilder()

        runBlocking {
            session.currentApiKey()?.takeIf { it.isNotBlank() }?.let {
                builder.header("X-API-Key", it)
            }
            session.currentCookie()?.takeIf { it.isNotBlank() }?.let {
                builder.header("Cookie", it)
            }
        }

        builder.header("Accept", "application/json")
        return chain.proceed(builder.build())
    }
}

/**
 * Self-hosted qui instances very often sit behind a self-signed certificate. This is
 * opt-in per server and mirrors the `tlsSkipVerify` switch qui exposes for its own
 * outbound qBittorrent connections.
 */
@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
internal fun OkHttpClient.Builder.applyTrustAllCerts(): OkHttpClient.Builder {
    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), SecureRandom())
    }
    sslSocketFactory(sslContext.socketFactory, trustManager)
    hostnameVerifier { _, _ -> true }
    return this
}
