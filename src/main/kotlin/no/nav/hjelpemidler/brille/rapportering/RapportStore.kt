package no.nav.hjelpemidler.brille.rapportering

import no.nav.hjelpemidler.brille.rapportering.queries.kravlinjeQuery
import no.nav.hjelpemidler.brille.vedtak.Kravlinje
import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.Page
import no.nav.hjelpemidler.database.PageRequest
import no.nav.hjelpemidler.database.Store
import org.intellij.lang.annotations.Language
import java.time.LocalDate

interface RapportStore : Store {
    fun hentKravlinjerForOrgNummer(
        orgNr: String,
        kravFilter: KravFilter?,
        fraDato: LocalDate?,
        tilDato: LocalDate?,
        referanseFilter: String?,
    ): List<Kravlinje>

    fun hentPagedKravlinjerForOrgNummer(
        orgNr: String,
        kravFilter: KravFilter?,
        fraDato: LocalDate?,
        tilDato: LocalDate?,
        referanseFilter: String?,
        limit: Int = 20,
        page: Int = 0,
    ): Page<Kravlinje>

    fun hentUtbetalingKravlinjerForOrgNummer(
        orgnr: String,
        avstemmingsreferanse: String,
    ): List<Kravlinje>
}

class RapportStorePostgres(private val tx: JdbcOperations) : RapportStore {
    override fun hentKravlinjerForOrgNummer(
        orgNr: String,
        kravFilter: KravFilter?,
        fraDato: LocalDate?,
        tilDato: LocalDate?,
        referanseFilter: String?,
    ): List<Kravlinje> {
        @Language("PostgreSQL")
        val sql = kravlinjeQuery(kravFilter, tilDato, null, referanseFilter)
        return tx.list(
            sql,
            mapOf(
                "orgNr" to orgNr,
                "fraDato" to fraDato,
                "tilDato" to tilDato,
                "referanseFilter" to "%$referanseFilter%",
            ),
        ) { row -> Kravlinje.fromRow(row) }
    }

    override fun hentPagedKravlinjerForOrgNummer(
        orgNr: String,
        kravFilter: KravFilter?,
        fraDato: LocalDate?,
        tilDato: LocalDate?,
        referanseFilter: String?,
        limit: Int,
        page: Int,
    ): Page<Kravlinje> {
        @Language("PostgreSQL")
        val sql = kravlinjeQuery(kravFilter, tilDato, null, referanseFilter, paginert = true)
        return tx.page(
            sql,
            mapOf(
                "orgNr" to orgNr,
                "limit" to limit,
                "offset" to page,
                "fraDato" to fraDato,
                "tilDato" to tilDato,
                "referanseFilter" to "%$referanseFilter%",
            ),
            PageRequest(page, limit),
        ) { row -> Kravlinje.fromRow(row) }
    }

    override fun hentUtbetalingKravlinjerForOrgNummer(
        orgnr: String,
        avstemmingsreferanse: String,
    ): List<Kravlinje> {
        @Language("PostgreSQL")
        val sql = kravlinjeQuery(null, null, avstemmingsreferanse, null)
        return tx.list(
            sql,
            mapOf(
                "orgNr" to orgnr,
                "avstemmingsreferanse" to avstemmingsreferanse,
            ),
        ) { row -> Kravlinje.fromRow(row) }
    }
}
