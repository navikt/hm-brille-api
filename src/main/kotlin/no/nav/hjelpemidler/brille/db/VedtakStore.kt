package no.nav.hjelpemidler.brille.db

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.hjelpemidler.brille.model.TidligereBrukteOrgnrForOptiker
import java.time.LocalDateTime
import java.time.Month
import java.util.UUID
import javax.sql.DataSource

interface VedtakStore {
    fun harFåttBrilleDetteKalenderÅret(fnrBruker: String): Boolean
    fun hentTidligereBrukteOrgnrForOptikker(fnrOptiker: String): TidligereBrukteOrgnrForOptiker
    fun lagreVedtak(fnrBruker: String, fnrInnsender: String, orgnr: String, data: JsonNode)
}

internal class VedtakStorePostgres(private val ds: DataSource) : VedtakStore {
    override fun harFåttBrilleDetteKalenderÅret(fnrBruker: String): Boolean =
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    """
                        SELECT 1
                        FROM vedtak
                        WHERE
                            fnr_bruker = ? AND
                            opprettet > ?
                    """.trimIndent(),
                    fnrBruker,
                    LocalDateTime.of(LocalDateTime.now().year, Month.JANUARY, 1, 0, 0),
                ).map {
                    true
                }.asSingle
            )
        } ?: false

    override fun hentTidligereBrukteOrgnrForOptikker(fnrOptiker: String): TidligereBrukteOrgnrForOptiker {
        val resultater = using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    """
                        SELECT orgnr
                        FROM vedtak
                        WHERE fnr_innsender = ?
                        ORDER BY opprettet DESC
                    """.trimIndent(),
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

    override fun lagreVedtak(fnrBruker: String, fnrInnsender: String, orgnr: String, data: JsonNode) {
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    """
                        INSERT INTO vedtak (
                            id,
                            fnr_bruker,
                            fnr_innsender,
                            orgnr,
                            data,
                            opprettet
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        ;
                    """.trimIndent(),
                    UUID.randomUUID(),
                    fnrBruker,
                    fnrInnsender,
                    orgnr,
                    data,
                    LocalDateTime.now(),
                ).asUpdate
            )
        }
    }

    companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    private fun soknadToJsonString(soknad: JsonNode): String = objectMapper.writeValueAsString(soknad)
}
