package com.aura.music

import okhttp3.Interceptor
import okhttp3.Response

class AppInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("X-APP-KEY", "aura-secure-android-key")
            .build()
        return chain.proceed(request)
    }
}
