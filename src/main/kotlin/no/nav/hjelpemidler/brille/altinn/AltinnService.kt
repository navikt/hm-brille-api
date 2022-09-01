package no.nav.hjelpemidler.brille.altinn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import mu.KotlinLogging

private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AltinnService(private val altinnClient: AltinnClient) {
    suspend fun hentAvgivereMedRettighet(fnr: String, rettighet: Rettighet): List<Avgiver> =
        withContext(Dispatchers.IO) {
            val alleAvgivere = altinnClient.hentAvgivere(fnr)

            sikkerLog.info {
                "Hentet avgivere for fnr: $fnr, avgivere: ${alleAvgivere.map { it.orgnr }}"
            }

            val avgivere = alleAvgivere
                .map {
                    async {
                        it.copy(rettigheter = hentRettigheter(fnr = fnr, orgnr = it.orgnr))
                    }
                }
                .awaitAll()

            val avgivereRettighet = avgivere.filter {
                it.harRettighet(rettighet)
            }

            sikkerLog.info {
                "Avgivere hvor fnr: $fnr har rettighet: $rettighet, avgivere: ${avgivereRettighet.map { it.orgnr }}"
            }

            avgivereRettighet
        }

    suspend fun hentRettigheter(fnr: String, orgnr: String): Rettigheter = withContext(Dispatchers.IO) {
        val rettigheter = altinnClient.hentRettigheter(fnr, orgnr)
        sikkerLog.info {
            "Hentet rettigheter: $rettigheter"
        }
        rettigheter
    }

    suspend fun harRettighet(fnr: String, orgnr: String, rettighet: Rettighet): Boolean = withContext(Dispatchers.IO) {
        hentRettigheter(fnr, orgnr).harRettighet(rettighet)
    }

    suspend fun harRettighetOppgjørsavtale(fnr: String, orgnr: String): Boolean = withContext(Dispatchers.IO) {
        harRettighet(fnr, orgnr, Rettighet.OPPGJØRSAVTALE)
    }

    suspend fun harRettighetUtbetalingsrapport(fnr: String, orgnr: String): Boolean = withContext(Dispatchers.IO) {
        harRettighet(fnr, orgnr, Rettighet.UTBETALINGSRAPPORT)
    }
}
