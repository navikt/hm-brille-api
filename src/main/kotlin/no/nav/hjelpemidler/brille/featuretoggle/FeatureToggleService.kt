package no.nav.hjelpemidler.brille.featuretoggle

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.brille.UnleashKlient

private val log = KotlinLogging.logger { }

class FeatureToggleService {
    fun hentFeatureToggles(features: List<String>?): Map<String, Boolean> =
        features?.map { it to isEnabled(it) }?.toMap() ?: emptyMap()

    fun isEnabled(feature: String): Boolean {
        return try {
            UnleashKlient.isEnabled(feature)
        } catch (e: Exception) {
            log.warn(e) { "Klarte ikke å hente unleash-toggles" }
            false
        }
    }
}
