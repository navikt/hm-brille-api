package no.nav.hjelpemidler.brille.altinn

class AltinnService(private val altinnClient: AltinnClient) {
    suspend fun hentRoller(fnr: String, etternavn: String): RightHolder? {
        val reportee = altinnClient.hentReportee(fnr, etternavn) ?: return null
        return altinnClient.hentRightHolder(reportee.reporteeId)
    }
}
