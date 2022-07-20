package no.nav.hjelpemidler.brille.altinn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import mu.KotlinLogging

private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AltinnService(private val altinnClient: AltinnClient) {
    suspend fun erHovedadministratorFor(fnr: String, orgnr: String): Boolean =
        altinnClient.erHovedadministratorFor(fnr, orgnr)

    suspend fun hentAvgivereHovedadministrator(fnr: String): List<Avgiver> = withContext(Dispatchers.IO) {
        val alleAvgivere = altinnClient.hentAvgivere(fnr)

        sikkerLog.info {
            "Hentet avgivere for fnr: $fnr, avgivere: ${alleAvgivere.map { it.orgnr }}"
        }

        val avgivere = alleAvgivere
            .map {
                async {
                    it.copy(hovedadministrator = erHovedadministratorFor(fnr, it.orgnr))
                }
            }
            .awaitAll()

        val avgivereHovedadministrator = avgivere.filter {
            it.hovedadministrator
        }

        sikkerLog.info {
            "Avgivere hvor fnr: $fnr er hovedadministrator, avgivere: ${avgivereHovedadministrator.map { it.orgnr }}"
        }

        avgivereHovedadministrator
    }
}
