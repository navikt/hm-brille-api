package no.nav.hjelpemidler.brille.altinn

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.hjelpemidler.brille.featuretoggle.FeatureToggleService
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.logging.secureInfo

private val log = KotlinLogging.logger {}

class AltinnService(
    private val altinn2Client: AltinnClient,
    private val altinn3Client: Altinn3Client,
    private val featureToggleService: FeatureToggleService,
) {
    private fun altinn3Enabled(): Boolean {
        val featureToggleKey = "brille-api.altinn3"
        return runCatching {
            featureToggleService.hentFeatureToggles(listOf(featureToggleKey))[featureToggleKey]
        }.onSuccess { v ->
            log.info { "Hentet feature toggle $featureToggleKey med resultat altinn3: $v" }
        }.onFailure { e ->
            log.warn(e) { "Feilet å hente feature toggle $featureToggleKey med feilmelding. Defaulter til \"false\"." }
        }.getOrNull() ?: false
    }

    suspend fun hentAvgivere(fnr: String, tjeneste: Avgiver.Tjeneste): List<Avgiver> =
        withContext(Dispatchers.IO) {
            val avgivere = when (altinn3Enabled()) {
                true -> altinn3Client.hentAvgivere(fnr = fnr, tjeneste = tjeneste)
                false -> altinn2Client.hentAvgivere(fnr = fnr, tjeneste = tjeneste)
            }
            if (!Environment.current.isProd) {
                log.info {
                    "Avgivere for fnr: $fnr, tjeneste: $tjeneste, avgivere: $avgivere"
                }
            }
            log.secureInfo {
                "Avgivere for fnr: $fnr, tjeneste: $tjeneste, avgivere: $avgivere"
            }
            avgivere
        }

    suspend fun harTilgangTilOppgjørsavtale(fnr: String, orgnr: String): Boolean = withContext(Dispatchers.IO) {
        val harTilgangTilOppgjørsavtale = when (altinn3Enabled()) {
            true ->
                altinn3Client
                    .hentRettigheter(fnr = fnr, orgnr = orgnr)
                    .contains(Avgiver.Tjeneste.OPPGJØRSAVTALE)
            false ->
                altinn2Client
                    .hentRettigheter(fnr = fnr, orgnr = orgnr)
                    .contains(Avgiver.Tjeneste.OPPGJØRSAVTALE)
        }
        if (!Environment.current.isProd) {
            log.info {
                "Rettigheter for fnr: $fnr, orgnr: $orgnr, harTilgangTilOppgjørsavtale: $harTilgangTilOppgjørsavtale"
            }
        }
        log.secureInfo {
            "Rettigheter for fnr: $fnr, orgnr: $orgnr, harTilgangTilOppgjørsavtale: $harTilgangTilOppgjørsavtale"
        }
        harTilgangTilOppgjørsavtale
    }

    suspend fun harTilgangTilUtbetalingsrapport(fnr: String, orgnr: String): Boolean = withContext(Dispatchers.IO) {
        val harTilgangTilUtbetalingsrapport = when (altinn3Enabled()) {
            true ->
                altinn3Client
                    .hentRettigheter(fnr = fnr, orgnr = orgnr)
                    .contains(Avgiver.Tjeneste.UTBETALINGSRAPPORT)
            false ->
                altinn2Client
                    .hentRettigheter(fnr = fnr, orgnr = orgnr)
                    .contains(Avgiver.Tjeneste.UTBETALINGSRAPPORT)
        }
        if (!Environment.current.isProd) {
            log.info {
                "Rettigheter for fnr: $fnr, orgnr: $orgnr, harTilgangTilUtbetalingsrapport: $harTilgangTilUtbetalingsrapport"
            }
        }
        log.secureInfo {
            "Rettigheter for fnr: $fnr, orgnr: $orgnr, harTilgangTilUtbetalingsrapport: $harTilgangTilUtbetalingsrapport"
        }
        harTilgangTilUtbetalingsrapport
    }
}
