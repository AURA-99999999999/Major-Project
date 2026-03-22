package com.aura.music

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class AppInterceptor : Interceptor {
    companion object {
        private const val TAG = "AppInterceptor"
        private const val MAX_RETRIES = 2
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("X-APP-KEY", "aura-secure-android-key")
            .build()

        var lastException: IOException? = null
        var attempt = 0

        while (attempt <= MAX_RETRIES) {
            try {
                Log.i(TAG, "Request: ${request.method} ${request.url}")
                val response = chain.proceed(request)
                Log.i(TAG, "Response: code=${response.code} url=${request.url}")

                if (response.code in listOf(502, 503, 504) && attempt < MAX_RETRIES) {
                    response.close()
                    attempt += 1
                    Log.w(TAG, "Retrying due to upstream cold-start/status ${response.code}. attempt=$attempt")
                    continue
                }

                return response
            } catch (e: IOException) {
                lastException = e
                if (attempt >= MAX_RETRIES) {
                    Log.e(TAG, "Network error after retries for ${request.url}: ${e.message}", e)
                    throw e
                }
                attempt += 1
                Log.w(TAG, "Retrying due to network error. attempt=$attempt url=${request.url} error=${e.message}")
            }
        }

        throw lastException ?: IOException("Unknown network failure for ${request.url}")
    }
}
