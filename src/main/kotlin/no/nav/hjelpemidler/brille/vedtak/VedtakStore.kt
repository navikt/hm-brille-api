package no.nav.hjelpemidler.brille.vedtak

import com.fasterxml.jackson.databind.JsonNode
import kotliquery.queryOf
import no.nav.hjelpemidler.brille.execute
import no.nav.hjelpemidler.brille.pgObjectOf
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.time.Month
import java.util.UUID
import javax.sql.DataSource

interface VedtakStore {
    fun harFåttBrilleDetteKalenderÅret(fnrBruker: String): Boolean
    fun hentTidligereBrukteOrgnrForOptikker(fnrOptiker: String): List<String>
    fun opprettVedtak(fnrBruker: String, fnrInnsender: String, orgnr: String, data: JsonNode)
    fun tellRader(): Int
    fun hentVedtakForBruker(fnrBruker: String): List<EksisterendeVedtak>
    fun <T> lagreVedtak(vedtak: Vedtak_v2<T>): Vedtak_v2<T>
}

internal class VedtakStorePostgres(private val ds: DataSource) : VedtakStore {
    override fun harFåttBrilleDetteKalenderÅret(fnrBruker: String): Boolean = ds.execute { session ->
        @Language("PostgreSQL")
        val sql = """
            SELECT 1
            FROM vedtak
            WHERE
                fnr_bruker = ? AND
                opprettet > ?
        """.trimIndent()
        session.run(
            queryOf(
                sql,
                fnrBruker,
                LocalDateTime.of(LocalDateTime.now().year, Month.JANUARY, 1, 0, 0),
            ).map {
                true
            }.asSingle
        )
    } ?: false

    override fun hentVedtakForBruker(fnrBruker: String): List<EksisterendeVedtak> = ds.execute { session ->
        @Language("PostgreSQL")
        val sql = """
            SELECT id, fnr_bruker, bestillingsdato, status, opprettet
            FROM vedtak_v2
            WHERE fnr_bruker = :fnr_bruker 
        """.trimIndent()
        session.run(
            queryOf(
                sql,
                mapOf("fnr_bruker" to fnrBruker)
            ).map { row ->
                EksisterendeVedtak(
                    id = row.int("id"),
                    fnrBruker = row.string("fnr_bruker"),
                    bestillingsdato = row.localDate("bestillingsdato"),
                    status = row.string("status"),
                    opprettet = row.localDateTime("opprettet"),
                )
            }.asList
        )
    }

    override fun hentTidligereBrukteOrgnrForOptikker(fnrOptiker: String): List<String> {
        val resultater = ds.execute { session ->
            @Language("PostgreSQL")
            val sql = """
                SELECT orgnr
                FROM vedtak
                WHERE fnr_innsender = ?
                ORDER BY opprettet DESC
            """.trimIndent()
            session.run(
                queryOf(
                    sql,
                    fnrOptiker,
                ).map {
                    it.string("orgnr")
                }.asList
            )
        }
        return resultater.toSet().toList()
    }

    override fun opprettVedtak(fnrBruker: String, fnrInnsender: String, orgnr: String, data: JsonNode) {
        val resultat = ds.execute { session ->
            @Language("PostgreSQL")
            val sql = """
                INSERT INTO vedtak (id,
                                    fnr_bruker,
                                    fnr_innsender,
                                    orgnr,
                                    data,
                                    opprettet)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
            session.run(
                queryOf(
                    sql,
                    UUID.randomUUID(),
                    fnrBruker,
                    fnrInnsender,
                    orgnr,
                    pgObjectOf(data),
                    LocalDateTime.now(),
                ).asUpdate
            )
        }
        if (resultat == 0) {
            throw RuntimeException("VedtakStore.opprettVedtak: feilet i å opprette vedtak (resultat=0)")
        }
    }

    override fun <T> lagreVedtak(vedtak: Vedtak_v2<T>): Vedtak_v2<T> {
        val id = ds.execute { session ->
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
            session.run(
                queryOf(
                    sql,
                    mapOf(
                        "fnr_bruker" to vedtak.fnrBruker,
                        "fnr_innsender" to vedtak.fnrInnsender,
                        "orgnr" to vedtak.orgnr,
                        "bestillingsdato" to vedtak.bestillingsdato,
                        "brillepris" to vedtak.brillepris,
                        "bestillingsreferanse" to vedtak.bestillingsreferanse,
                        "vilkarsvurdering" to pgObjectOf(vedtak.vilkarsvurdering),
                        "status" to vedtak.status
                    )
                ).map {
                    it.int("id")
                }.asSingle
            )
        }
        requireNotNull(id) { "Lagring av vedtak feilet" }
        return vedtak.copy(id = id)
    }

    override fun tellRader(): Int {
        return ds.execute { session ->
            @Language("PostgreSQL")
            val sql = """
                SELECT COUNT (id) AS count
                FROM vedtak
            """.trimIndent()
            session.run(
                queryOf(
                    sql
                ).map { row -> row.int("count") }.asSingle
            )
        } ?: throw RuntimeException("VedtakStore.countRows: feilet i å telle rader")
    }
}
