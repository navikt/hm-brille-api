package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.pgObjectOf
import no.nav.hjelpemidler.brille.store.Store
import no.nav.hjelpemidler.brille.store.query
import no.nav.hjelpemidler.brille.store.queryList
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

interface VedtakStore : Store {
    fun hentTidligereBrukteOrgnrForInnsender(fnrInnsender: String): List<String>
    fun hentVedtakForBarn(fnrBarn: String): List<EksisterendeVedtak>
    fun <T> lagreVedtak(vedtak: Vedtak<T>): Vedtak<T>
    fun hentKravlinjerForOrgNummer(orgNr: String): List<Kravlinje>
}

internal class VedtakStorePostgres(private val ds: DataSource) : VedtakStore {
    override fun hentVedtakForBarn(fnrBarn: String): List<EksisterendeVedtak> {
        @Language("PostgreSQL")
        val sql = """
            SELECT id, fnr_barn, bestillingsdato, behandlingsresultat, opprettet
            FROM vedtak_v1
            WHERE fnr_barn = :fnr_barn 
        """.trimIndent()
        return ds.queryList(sql, mapOf("fnr_barn" to fnrBarn)) { row ->
            EksisterendeVedtak(
                id = row.long("id"),
                fnrBarn = row.string("fnr_barn"),
                bestillingsdato = row.localDate("bestillingsdato"),
                behandlingsresultat = row.string("behandlingsresultat"),
                opprettet = row.localDateTime("opprettet"),
            )
        }
    }

    override fun hentTidligereBrukteOrgnrForInnsender(fnrInnsender: String): List<String> {
        @Language("PostgreSQL")
        val sql = """
            SELECT orgnr
            FROM vedtak_v1
            WHERE fnr_innsender = :fnr_innsender
            ORDER BY opprettet DESC
        """.trimIndent()
        return ds.queryList(sql, mapOf("fnr_innsender" to fnrInnsender)) { row ->
            row.string("orgnr")
        }.toSet().toList()
    }

    override fun <T> lagreVedtak(vedtak: Vedtak<T>): Vedtak<T> {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO vedtak_v1 (
                fnr_barn,
                fnr_innsender,
                orgnr,
                bestillingsdato,
                brillepris,
                bestillingsreferanse,
                vilkarsvurdering,
                behandlingsresultat,
                sats,
                sats_belop,
                sats_beskrivelse,
                belop,
                opprettet
            )
            VALUES (
                :fnr_barn,
                :fnr_innsender,
                :orgnr,
                :bestillingsdato,
                :brillepris,
                :bestillingsreferanse,
                :vilkarsvurdering,
                :behandlingsresultat,
                :sats,
                :sats_belop,
                :sats_beskrivelse,
                :belop,
                :opprettet
            )
            RETURNING id
        """.trimIndent()
        val id = ds.query(
            sql,
            mapOf(
                "fnr_barn" to vedtak.fnrBarn,
                "fnr_innsender" to vedtak.fnrInnsender,
                "orgnr" to vedtak.orgnr,
                "bestillingsdato" to vedtak.bestillingsdato,
                "brillepris" to vedtak.brillepris,
                "bestillingsreferanse" to vedtak.bestillingsreferanse,
                "vilkarsvurdering" to pgObjectOf(vedtak.vilkårsvurdering),
                "behandlingsresultat" to vedtak.behandlingsresultat.toString(),
                "sats" to vedtak.sats.toString(),
                "sats_belop" to vedtak.satsBeløp,
                "sats_beskrivelse" to vedtak.satsBeskrivelse,
                "belop" to vedtak.beløp,
                "opprettet" to vedtak.opprettet,
            )
        ) { row ->
            row.long("id")
        }
        requireNotNull(id) { "Lagring av vedtak feilet, id var null" }
        return vedtak.copy(id = id)
    }

    override fun hentKravlinjerForOrgNummer(orgNr: String): List<Kravlinje> {
        @Language("PostgreSQL")
        val sql = """
            SELECT id, bestillingsdato, behandlingsresultat, opprettet, belop
            FROM vedtak_v1
            WHERE orgnr = :orgNr 
        """.trimIndent()
        return ds.queryList(sql, mapOf("orgNr" to orgNr)) { row ->
            Kravlinje(
                id = row.long("id"),
                bestillingsdato = row.localDate("bestillingsdato"),
                behandlingsresultat = row.string("behandlingsresultat"),
                opprettet = row.localDateTime("opprettet"),
                beløp = row.bigDecimal("belop")
            )
        }
    }
}
