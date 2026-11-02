package com.atunwadigital.examples.media

/**
 * Which Atunwa key this build uses. Atunwa issues both kinds of key, and both are safe to ship
 * in an app. They're read from `local.properties` at build time (see `app/build.gradle.kts`).
 */
sealed interface AtunwaConfig {

    /** One station, identified by its player API key. */
    data class SinglePlayer(val playerApiKey: String) : AtunwaConfig

    /** Every station a publisher has, identified by the publisher's SDK key. */
    data class Publisher(val publisherSdkKey: String) : AtunwaConfig

    /** The build has no usable key. [problem] says what to fix. */
    data class Missing(val problem: String) : AtunwaConfig

    companion object {
        fun fromBuildConfig(): AtunwaConfig = when (BuildConfig.ATUNWA_APP_MODE.lowercase()) {
            "single" -> BuildConfig.ATUNWA_PLAYER_KEY.ifBlank { null }?.let(::SinglePlayer)
                ?: Missing("ATUNWA_APP_MODE is single, but ATUNWA_PLAYER_KEY is empty.")
            "publisher" -> BuildConfig.ATUNWA_PUBLISHER_SDK_KEY.ifBlank { null }?.let(::Publisher)
                ?: Missing("ATUNWA_APP_MODE is publisher, but ATUNWA_PUBLISHER_SDK_KEY is empty.")
            else -> Missing("ATUNWA_APP_MODE must be single or publisher, not \"${BuildConfig.ATUNWA_APP_MODE}\".")
        }
    }
}
