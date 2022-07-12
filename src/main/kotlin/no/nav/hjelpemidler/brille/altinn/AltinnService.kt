package no.nav.hjelpemidler.brille.altinn

class AltinnService(private val altinnClient: AltinnClient) {
    suspend fun hentAvgivereHovedadministrator(fnr: String): List<Avgiver> {
        val avgivere = altinnClient.hentAvgivere(fnr)
        return avgivere.filter {
            altinnClient.erHovedadministratorFor(fnr, it.orgnr)
        }
    }
}
