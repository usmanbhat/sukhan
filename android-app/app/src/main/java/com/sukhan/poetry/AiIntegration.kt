package com.sukhan.poetry

/**
 * Integration seam for source-app AI surfaces. The demo is intentionally offline:
 * no request is sent unless a future provider implementation is added.
 * Configure values through an untracked ai.properties file or Gradle properties.
 */
internal object AiIntegration {
    val endpoint: String get() = BuildConfig.SUKHAN_AI_ENDPOINT
    val apiKey: String get() = BuildConfig.SUKHAN_AI_API_KEY
    val isConfigured: Boolean get() = endpoint.isNotBlank() && apiKey.isNotBlank()

    fun unavailableMessage(): String =
        if (isConfigured) "AI provider settings are present, but the provider adapter has not been connected yet."
        else "AI is optional and is not configured in this offline demo. Add a provider in AiIntegration.kt when you are ready."
}
