package no.nav.hjelpemidler.brille.rapportering

import no.nav.hjelpemidler.brille.store.Page
import no.nav.hjelpemidler.brille.vedtak.Kravlinje
import no.nav.hjelpemidler.brille.vedtak.VedtakStore

class RapportService(
    private val vedtakStore: VedtakStore,
) {
    fun hentKravlinjer(orgNr: String): List<Kravlinje> {
        val kravlinjer = vedtakStore.hentKravlinjerForOrgNummer(orgNr)
        return kravlinjer
    }

    fun hentPagedKravlinjer(
        orgNr: String,
        limit: Int = 20,
        offset: Int = 0,
    ): Page<Kravlinje> {
        val kravlinjer = vedtakStore.hentPagedKravlinjerForOrgNummer(
            orgNr = orgNr,
            limit = limit,
            offset = offset
        )
        return kravlinjer
    }
}
