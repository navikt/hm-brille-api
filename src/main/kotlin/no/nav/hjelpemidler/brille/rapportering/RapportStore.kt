package no.nav.hjelpemidler.brille.rapportering

import no.nav.hjelpemidler.brille.rapportering.queries.COLUMN_LABEL_TOTAL
import no.nav.hjelpemidler.brille.rapportering.queries.kravlinjeQuery
import no.nav.hjelpemidler.brille.vedtak.Kravlinje
import no.nav.hjelpemidler.database.JdbcOperations
import no.nav.hjelpemidler.database.Store
import no.nav.hjelpemidler.pagination.Page
import no.nav.hjelpemidler.pagination.PageRequest
import no.nav.hjelpemidler.pagination.pageOf
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
        pageNumber: Int = 0,
        pageSize: Int = 20,
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
        pageNumber: Int,
        pageSize: Int,
    ): Page<Kravlinje> {
        val sql = kravlinjeQuery(kravFilter, tilDato, null, referanseFilter, paginert = true)
        val pageRequest = PageRequest(pageNumber, pageSize)
        return tx.page(
            sql = sql,
            queryParameters = mapOf(
                "orgNr" to orgNr,
                "limit" to pageRequest.limit,
                "offset" to pageRequest.offset,
                "fraDato" to fraDato,
                "tilDato" to tilDato,
                "referanseFilter" to "%$referanseFilter%",
            ),
            pageRequest = PageRequest.ALL, // limit/offset gjøres i kravlinjeQuery
            totalElementsLabel = COLUMN_LABEL_TOTAL,
        ) { row -> Kravlinje.fromRow(row) }.let { pageOf(it.content, it.totalElements, pageRequest) }
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
