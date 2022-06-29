package no.nav.hjelpemidler.brille.db

import com.fasterxml.jackson.databind.JsonNode
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.hjelpemidler.brille.model.TidligereBrukteOrgnrForOptiker
import no.nav.hjelpemidler.brille.pgObjectOf
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.time.Month
import java.util.UUID
import javax.sql.DataSource

interface VedtakStore {
    fun harFåttBrilleDetteKalenderÅret(fnrBruker: String): Boolean
    fun hentTidligereBrukteOrgnrForOptikker(fnrOptiker: String): TidligereBrukteOrgnrForOptiker
    fun opprettVedtak(fnrBruker: String, fnrInnsender: String, orgnr: String, data: JsonNode)
}

internal class VedtakStorePostgres(private val ds: DataSource) : VedtakStore {
    override fun harFåttBrilleDetteKalenderÅret(fnrBruker: String): Boolean = using(sessionOf(ds)) { session ->
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

    override fun hentTidligereBrukteOrgnrForOptikker(fnrOptiker: String): TidligereBrukteOrgnrForOptiker {
        val resultater = using(sessionOf(ds)) { session ->
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
        return TidligereBrukteOrgnrForOptiker(
            resultater.getOrElse(0) { "" },
            resultater.toSet().toList()
        )
    }

    override fun opprettVedtak(fnrBruker: String, fnrInnsender: String, orgnr: String, data: JsonNode) {
        val result = using(sessionOf(ds)) { session ->
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
        if (result == 0) {
            throw RuntimeException("VedtakStore.opprettVedtak: feilet i å opprette vedtak (result=false)")
        }
    }
}
