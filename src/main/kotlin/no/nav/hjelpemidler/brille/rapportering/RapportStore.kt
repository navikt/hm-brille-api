package no.nav.hjelpemidler.brille.rapportering

import kotliquery.Session
import no.nav.hjelpemidler.brille.rapportering.queries.kravlinjeQuery
import no.nav.hjelpemidler.brille.store.Page
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.TransactionalStore
import no.nav.hjelpemidler.brille.store.queryList
import no.nav.hjelpemidler.brille.store.queryPagedList
import no.nav.hjelpemidler.brille.vedtak.Kravlinje
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
        offset: Int = 0,
    ): Page<Kravlinje>
}

internal class RapportStorePostgres(sessionFactory: () -> Session) : RapportStore, TransactionalStore(sessionFactory) {

    override fun hentKravlinjerForOrgNummer(
        orgNr: String,
        kravFilter: KravFilter?,
        fraDato: LocalDate?,
        tilDato: LocalDate?,
        referanseFilter: String?,
    ): List<Kravlinje> = session {
        @Language("PostgreSQL")
        val sql = kravlinjeQuery(kravFilter, tilDato, referanseFilter)
        it.queryList(
            sql,
            mapOf(
                "orgNr" to orgNr,
                "fraDato" to fraDato,
                "tilDato" to tilDato,
                "referanseFilter" to "%$referanseFilter%",
            )
        ) { row ->
            Kravlinje.fromRow(row)
        }
    }

    override fun hentPagedKravlinjerForOrgNummer(
        orgNr: String,
        kravFilter: KravFilter?,
        fraDato: LocalDate?,
        tilDato: LocalDate?,
        referanseFilter: String?,
        limit: Int,
        offset: Int,
    ): Page<Kravlinje> = session {
        @Language("PostgreSQL")
        val sql = kravlinjeQuery(kravFilter, tilDato, referanseFilter)
        it.queryPagedList(
            sql,
            mapOf(
                "orgNr" to orgNr,
                "limit" to limit,
                "offset" to offset,
                "fraDato" to fraDato,
                "tilDato" to tilDato,
                "referanseFilter" to "%$referanseFilter%",
            ),
            limit,
            offset
        ) { row ->
            Kravlinje.fromRow(row)
        }
    }
}
