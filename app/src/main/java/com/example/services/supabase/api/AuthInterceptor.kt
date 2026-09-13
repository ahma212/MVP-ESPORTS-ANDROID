package com.example.services.supabase.api

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val anonKey: String,
    private val tokenProvider: () -> String? = { null }
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenProvider() ?: anonKey
        val request = original.newBuilder()
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .build()
        return chain.proceed(request)
    }
}
