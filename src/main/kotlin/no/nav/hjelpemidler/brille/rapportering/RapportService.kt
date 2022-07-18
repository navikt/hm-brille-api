package no.nav.hjelpemidler.brille.rapportering

import no.nav.hjelpemidler.brille.vedtak.Kravlinje
import no.nav.hjelpemidler.brille.vedtak.VedtakStore

class RapportService(
    private val vedtakStore: VedtakStore,
) {
    fun hentKravlinjer(orgNr: String): List<Kravlinje> {
        val kravlinjer = vedtakStore.hentKravlinjerForOrgNummer(orgNr)
        return kravlinjer
    }
}
