package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.pgObjectOf
import no.nav.hjelpemidler.brille.query
import no.nav.hjelpemidler.brille.queryList
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

interface VedtakStore {
    fun hentTidligereBrukteOrgnrForOptiker(fnrOptiker: String): List<String>
    fun hentVedtakForBruker(fnrBruker: String): List<EksisterendeVedtak>
    fun <T> lagreVedtak(vedtak: Vedtak<T>): Vedtak<T>
}

internal class VedtakStorePostgres(private val ds: DataSource) : VedtakStore {
    override fun hentVedtakForBruker(fnrBruker: String): List<EksisterendeVedtak> {
        @Language("PostgreSQL")
        val sql = """
            SELECT id, fnr_bruker, bestillingsdato, status, opprettet
            FROM vedtak_v2
            WHERE fnr_bruker = :fnr_bruker 
        """.trimIndent()
        return ds.queryList(sql, mapOf("fnr_bruker" to fnrBruker)) { row ->
            EksisterendeVedtak(
                id = row.int("id"),
                fnrBruker = row.string("fnr_bruker"),
                bestillingsdato = row.localDate("bestillingsdato"),
                status = row.string("status"),
                opprettet = row.localDateTime("opprettet"),
            )
        }
    }

    override fun hentTidligereBrukteOrgnrForOptiker(fnrOptiker: String): List<String> {
        @Language("PostgreSQL")
        val sql = """
            SELECT orgnr
            FROM vedtak
            WHERE fnr_innsender = :fnr_innsender
            ORDER BY opprettet DESC
        """.trimIndent()
        return ds.queryList(sql, mapOf("fnr_innsender" to fnrOptiker)) { row ->
            row.string("orgnr")
        }.toSet().toList()
    }

    override fun <T> lagreVedtak(vedtak: Vedtak<T>): Vedtak<T> {
        @Language("PostgreSQL")
        val sql = """
            INSERT INTO vedtak_v2 (
                fnr_bruker,
                fnr_innsender,
                orgnr,
                bestillingsdato,
                brillepris,
                bestillingsreferanse,
                vilkarsvurdering,
                status
            )
            VALUES (
                :fnr_bruker,
                :fnr_innsender,
                :orgnr,
                :bestillingsdato,
                :brillepris,
                :bestillingsreferanse,
                :vilkarsvurdering,
                :status
            )
            RETURNING id
        """.trimIndent()
        val id = ds.query(
            sql,
            mapOf(
                "fnr_bruker" to vedtak.fnrBruker,
                "fnr_innsender" to vedtak.fnrInnsender,
                "orgnr" to vedtak.orgnr,
                "bestillingsdato" to vedtak.bestillingsdato,
                "brillepris" to vedtak.brillepris,
                "bestillingsreferanse" to vedtak.bestillingsreferanse,
                "vilkarsvurdering" to pgObjectOf(vedtak.vilkårsvurdering),
                "status" to vedtak.status
            )
        ) { row ->
            row.int("id")
        }
        requireNotNull(id) { "Lagring av vedtak feilet, id var null" }
        return vedtak.copy(id = id)
    }
}
