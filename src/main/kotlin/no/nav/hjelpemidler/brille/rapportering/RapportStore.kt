package no.nav.hjelpemidler.brille.rapportering

import no.nav.hjelpemidler.brille.rapportering.queries.kravlinjeQuery
import no.nav.hjelpemidler.brille.store.Page
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.queryList
import no.nav.hjelpemidler.brille.store.queryPagedList
import no.nav.hjelpemidler.brille.vedtak.Kravlinje
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import javax.sql.DataSource

interface RapportStore : Store {

    fun hentKravlinjerForOrgNummer(
        orgNr: String,
        kravFilter: KravFilter?,
        fraDato: LocalDate?,
        tilDato: LocalDate?
    ): List<Kravlinje>

    fun hentPagedKravlinjerForOrgNummer(
        orgNr: String,
        kravFilter: KravFilter?,
        fraDato: LocalDate?,
        tilDato: LocalDate?,
        limit: Int = 20,
        offset: Int = 0,
    ): Page<Kravlinje>
}

internal class RapportStorePostgres(private val ds: DataSource) : RapportStore {

    override fun hentKravlinjerForOrgNummer(
        orgNr: String,
        kravFilter: KravFilter?,
        fraDato: LocalDate?,
        tilDato: LocalDate?
    ): List<Kravlinje> {
        @Language("PostgreSQL")
        val sql = kravlinjeQuery(kravFilter, tilDato)
        return ds.queryList(
            sql,
            mapOf(
                "orgNr" to orgNr,
                "fraDato" to fraDato,
                "tilDato" to tilDato
            )
        ) { row ->
            Kravlinje(
                id = row.long("id"),
                bestillingsdato = row.localDate("bestillingsdato"),
                behandlingsresultat = row.string("behandlingsresultat"),
                opprettet = row.localDateTime("opprettet"),
                beløp = row.bigDecimal("belop"),
                bestillingsreferanse = row.string("bestillingsreferanse")
            )
        }
    }

    override fun hentPagedKravlinjerForOrgNummer(
        orgNr: String,
        kravFilter: KravFilter?,
        fraDato: LocalDate?,
        tilDato: LocalDate?,
        limit: Int,
        offset: Int,
    ): Page<Kravlinje> {
        @Language("PostgreSQL")
        val sql = kravlinjeQuery(kravFilter, tilDato)
        return ds.queryPagedList(
            sql,
            mapOf(
                "orgNr" to orgNr,
                "limit" to limit,
                "offset" to offset,
                "fraDato" to fraDato,
                "tilDato" to tilDato
            ),
            limit,
            offset
        ) { row ->
            Kravlinje(
                id = row.long("id"),
                bestillingsdato = row.localDate("bestillingsdato"),
                behandlingsresultat = row.string("behandlingsresultat"),
                opprettet = row.localDateTime("opprettet"),
                beløp = row.bigDecimal("belop"),
                bestillingsreferanse = row.string("bestillingsreferanse")
            )
        }
    }
}
