package no.nav.hjelpemidler.brille.featuretoggle

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.brille.UnleashClient

private val log = KotlinLogging.logger { }

class FeatureToggleService {
    fun hentFeatureToggles(features: List<String>?): Map<String, Boolean> =
        features?.associate { it to isEnabled(it) } ?: emptyMap()

    private fun isEnabled(feature: String): Boolean {
        return try {
            UnleashClient.isEnabled(feature)
        } catch (e: Exception) {
            log.warn(e) { "Klarte ikke å hente unleash-toggles" }
            false
        }
    }
}
