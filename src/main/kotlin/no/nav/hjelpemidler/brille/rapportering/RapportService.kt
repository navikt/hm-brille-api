package no.nav.hjelpemidler.brille.rapportering

import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.vedtak.Kravlinje
import no.nav.hjelpemidler.database.Page
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
        pageNumber: Int = 1,
        pageSize: Int = 20,
    ): Page<Kravlinje> {
        val kravlinjer = transaction(databaseContext) { ctx ->
            ctx.rapportStore.hentPagedKravlinjerForOrgNummer(
                orgNr = orgNr,
                kravFilter = kravFilter,
                fraDato = fraDato,
                tilDato = tilDato,
                referanseFilter = referanseFilter,
                pageNumber = pageNumber,
                pageSize = pageSize,
            )
        }
        return kravlinjer
    }

    suspend fun hentUtbetalingKravlinjer(orgnr: String, avstemmingsreferanse: String) =
        transaction(databaseContext) { ctx ->
            ctx.rapportStore.hentUtbetalingKravlinjerForOrgNummer(
                orgnr,
                avstemmingsreferanse,
            )
        }
}
