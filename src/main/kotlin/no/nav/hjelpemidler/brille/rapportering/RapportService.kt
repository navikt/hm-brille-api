package no.nav.hjelpemidler.brille.rapportering

import no.nav.hjelpemidler.brille.store.Page
import no.nav.hjelpemidler.brille.vedtak.Kravlinje
import java.time.LocalDate

class RapportService(
    private val rapportStore: RapportStore,
) {
    fun hentKravlinjer(
        orgNr: String,
        kravFilter: KravFilter? = null,
        fraDato: LocalDate? = null,
        tilDato: LocalDate? = null
    ): List<Kravlinje> {
        val kravlinjer = rapportStore.hentKravlinjerForOrgNummer(
            orgNr = orgNr,
            kravFilter = kravFilter,
            fraDato = fraDato,
            tilDato = tilDato
        )
        return kravlinjer
    }

    fun hentPagedKravlinjer(
        orgNr: String,
        kravFilter: KravFilter? = null,
        fraDato: LocalDate? = null,
        tilDato: LocalDate? = null,
        limit: Int = 20,
        offset: Int = 0,
    ): Page<Kravlinje> {
        val kravlinjer = rapportStore.hentPagedKravlinjerForOrgNummer(
            orgNr = orgNr,
            kravFilter = kravFilter,
            fraDato = fraDato,
            tilDato = tilDato,
            limit = limit,
            offset = offset
        )
        return kravlinjer
    }
}
