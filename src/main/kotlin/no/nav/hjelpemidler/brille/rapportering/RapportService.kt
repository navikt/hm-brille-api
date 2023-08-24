package no.nav.hjelpemidler.brille.rapportering

import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.store.Page
import no.nav.hjelpemidler.brille.vedtak.Kravlinje
import java.time.LocalDate

class RapportService(
    private val databaseContext: DatabaseContext,
) {
    suspend fun hentKravlinjer(
        orgNr: String,
        kravFilter: KravFilter? = null,
        fraDato: LocalDate? = null,
        tilDato: LocalDate? = null,
        referanseFilter: String? = "",
    ): List<Kravlinje> {
        val kravlinjer = transaction(databaseContext) { ctx ->
            ctx.rapportStore.hentKravlinjerForOrgNummer(
                orgNr = orgNr,
                kravFilter = kravFilter,
                fraDato = fraDato,
                tilDato = tilDato,
                referanseFilter = referanseFilter,
            )
        }
        return kravlinjer
    }

    suspend fun hentPagedKravlinjer(
        orgNr: String,
        kravFilter: KravFilter? = null,
        fraDato: LocalDate? = null,
        tilDato: LocalDate? = null,
        referanseFilter: String? = "",
        limit: Int = 10,
        offset: Int = 0,
    ): Page<Kravlinje> {
        val kravlinjer = transaction(databaseContext) { ctx ->
            ctx.rapportStore.hentPagedKravlinjerForOrgNummer(
                orgNr = orgNr,
                kravFilter = kravFilter,
                fraDato = fraDato,
                tilDato = tilDato,
                referanseFilter = referanseFilter,
                limit = limit,
                offset = offset
            )
        }
        return kravlinjer
    }
}
