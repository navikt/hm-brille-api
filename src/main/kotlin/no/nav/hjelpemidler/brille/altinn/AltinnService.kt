package no.nav.hjelpemidler.brille.altinn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import mu.KotlinLogging

private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AltinnService(private val altinnClient: AltinnClient) {
    suspend fun hentAvgivere(fnr: String): List<Avgiver> =
        withContext(Dispatchers.IO) {
            val avgivere = altinnClient.hentAvgivere(fnr)
                .map { avgiver ->
                    async {
                        avgiver.copy(
                            rettigheter = altinnClient.hentRettigheter(fnr = fnr, orgnr = avgiver.orgnr),
                            roller = altinnClient.hentRoller(fnr = fnr, orgnr = avgiver.orgnr)
                        )
                    }
                }
                .awaitAll()

            sikkerLog.info {
                "Avgivere for fnr: $fnr, avgivere: $avgivere"
            }

            avgivere
        }

    suspend fun harTilgangTilOppgjørsavtale(fnr: String, orgnr: String): Boolean = withContext(Dispatchers.IO) {
        Avgiver(
            navn = "",
            orgnr = "",
            parentOrgnr = null,
            rettigheter = altinnClient.hentRettigheter(fnr, orgnr),
            roller = altinnClient.hentRoller(fnr, orgnr)
        ).harTilgangTilOppgjørsavtale()
    }

    suspend fun harTilgangTilUtbetalingsrapport(fnr: String, orgnr: String): Boolean = withContext(Dispatchers.IO) {
        Avgiver(
            navn = "",
            orgnr = "",
            parentOrgnr = null,
            rettigheter = altinnClient.hentRettigheter(fnr, orgnr),
            roller = altinnClient.hentRoller(fnr, orgnr)
        ).harTilgangTilUtbetalingsrapport()
    }
}
