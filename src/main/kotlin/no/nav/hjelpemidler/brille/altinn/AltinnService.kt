package no.nav.hjelpemidler.brille.altinn

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.hjelpemidler.logging.secureInfo

private val log = KotlinLogging.logger {}

class AltinnService(private val altinnClient: Altinn3Client) {
    suspend fun hentAvgivere(fnr: String, tjeneste: Avgiver.Tjeneste): List<Avgiver> =
        withContext(Dispatchers.IO) {
            val avgivere = altinnClient.hentAvgivere(fnr = fnr, tjeneste = tjeneste)
            log.secureInfo {
                "Avgivere for fnr: $fnr, tjeneste: $tjeneste, avgivere: $avgivere"
            }
            avgivere
        }

    suspend fun harTilgangTilOppgjørsavtale(fnr: String, orgnr: String): Boolean = withContext(Dispatchers.IO) {
        altinnClient
            .hentRettigheter(fnr = fnr, orgnr = orgnr)
            .contains(Avgiver.Tjeneste.OPPGJØRSAVTALE)
    }

    suspend fun harTilgangTilUtbetalingsrapport(fnr: String, orgnr: String): Boolean = withContext(Dispatchers.IO) {
        altinnClient
            .hentRettigheter(fnr = fnr, orgnr = orgnr)
            .contains(Avgiver.Tjeneste.UTBETALINGSRAPPORT)
    }
}
