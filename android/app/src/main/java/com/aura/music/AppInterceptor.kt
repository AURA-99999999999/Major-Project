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
        val originalRequest = chain.request()
        val request = originalRequest.newBuilder()
            .addHeader("X-APP-KEY", "aura-secure-android-key")
            .build()

        // Log request details
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "REQUEST: ${request.method} ${request.url}")
        Log.d(TAG, "Headers: ${request.headers}")
        Log.d(TAG, "════════════════════════════════════════")

        var lastException: IOException? = null
        var attempt = 0

        while (attempt <= MAX_RETRIES) {
            try {
                Log.i(TAG, "Attempt ${attempt + 1}/${MAX_RETRIES + 1} for ${request.url}")
                val response = chain.proceed(request)
                
                Log.i(TAG, "✓ Response: code=${response.code} url=${request.url}")
                Log.d(TAG, "Response headers: ${response.headers}")

                if (response.code in listOf(502, 503, 504) && attempt < MAX_RETRIES) {
                    response.close()
                    attempt += 1
                    Log.w(TAG, "⚠ Retrying due to upstream cold-start/status ${response.code}. attempt=$attempt")
                    continue
                }

                return response
            } catch (e: IOException) {
                lastException = e
                Log.e(TAG, "✗ Network Error (attempt ${attempt + 1}): ${e.javaClass.simpleName}: ${e.message}")
                Log.e(TAG, "   URL: ${request.url}")
                Log.e(TAG, "   Host: ${request.url.host}")
                Log.e(TAG, "   Port: ${request.url.port}")
                
                if (attempt >= MAX_RETRIES) {
                    Log.e(TAG, "✗✗ FATAL: Network error after ${MAX_RETRIES + 1} retries for ${request.url}", e)
                    throw e
                }
                attempt += 1
                Log.w(TAG, "   Retrying... (attempt=$attempt)")
            }
        }

        throw lastException ?: IOException("Unknown network failure for ${request.url}")
    }
}
