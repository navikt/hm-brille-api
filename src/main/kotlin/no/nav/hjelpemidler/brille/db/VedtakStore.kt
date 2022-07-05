package no.nav.hjelpemidler.brille.db

import com.fasterxml.jackson.databind.JsonNode
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.hjelpemidler.brille.json
import no.nav.hjelpemidler.brille.model.TidligereBrukteOrgnrForOptiker
import no.nav.hjelpemidler.brille.pgObjectOf
import no.nav.hjelpemidler.brille.vedtak.Vedtak_v2
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.util.UUID
import javax.sql.DataSource

interface VedtakStore {
    fun harFåttBrilleDetteKalenderÅret(fnrBruker: String): Boolean
    fun hentTidligereBrukteOrgnrForOptikker(fnrOptiker: String): TidligereBrukteOrgnrForOptiker
    fun opprettVedtak(fnrBruker: String, fnrInnsender: String, orgnr: String, data: JsonNode)
    fun tellRader(): Int
    fun <T> hentVedtakIBestillingsdatoAr(fnrBruker: String, bestillingsdato: LocalDate): Vedtak_v2<T>?
    fun <T> lagreVedtak(vedtak: Vedtak_v2<T>): Vedtak_v2<T>
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

    override fun <T> hentVedtakIBestillingsdatoAr(fnrBruker: String, bestillingsdato: LocalDate): Vedtak_v2<T>? =
        using(sessionOf(ds)) { session ->
            @Language("PostgreSQL")
            val sql = """
            SELECT *
            FROM vedtak_v2
            WHERE
                fnr_bruker = :fnr_bruker AND
                DATE_PART('year', bestillingsdato) = :bestillingsdato_ar
            """.trimIndent()
            session.run(
                queryOf(
                    sql,
                    mapOf(
                        "fnr_bruker" to fnrBruker,
                        "bestillingsdato_ar" to bestillingsdato.year
                    )
                ).map { row ->
                    Vedtak_v2<T>(
                        id = row.int("id"),
                        fnrBruker = row.string("fnr_bruker"),
                        fnrInnsender = row.string("fnr_innsender"),
                        orgnr = row.string("orgnr"),
                        bestillingsdato = row.localDate("bestillingsdato"),
                        brillepris = row.bigDecimal("brillepris"),
                        bestillingsreferanse = row.string("bestillingsreferanse"),
                        vilkarsvurdering = row.json("vilkarsvurdering"),
                        status = row.string("status"),
                        opprettet = row.localDateTime("opprettet"),
                    )
                }.asSingle
            )
        }

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
        val resultat = using(sessionOf(ds)) { session ->
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
        val id = using(sessionOf(ds)) { session ->
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
        return using(sessionOf(ds)) { session ->
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
