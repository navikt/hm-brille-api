package no.nav.hjelpemidler.brille.altinn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class AltinnService(private val altinnClient: AltinnClient) {
    suspend fun erHovedadministratorFor(fnr: String, orgnr: String): Boolean =
        altinnClient.erHovedadministratorFor(fnr, orgnr)

    suspend fun hentAvgivereHovedadministrator(fnr: String): List<Avgiver> = withContext(Dispatchers.IO) {
        val avgivere = altinnClient.hentAvgivere(fnr)
            .map {
                async {
                    it.copy(hovedadministrator = erHovedadministratorFor(fnr, it.orgnr))
                }
            }
            .awaitAll()

        avgivere.filter {
            it.hovedadministrator
        }
    }
}
