package no.nav.hjelpemidler.brille.db

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import javax.sql.DataSource

internal interface VedtakStore {
    fun harFåttBrilleSisteÅret(fnrBruker: String): Boolean
}

internal class VedtakStorePostgres(private val ds: DataSource) : VedtakStore {
    override fun harFåttBrilleSisteÅret(fnrBruker: String): Boolean =
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    """
                        SELECT 1
                        FROM vedtak
                        WHERE
                            fnr_bruker = ? AND
                            opprettet > (NOW() - interval '1 years')
                    """.trimIndent(),
                    fnrBruker,
                ).map {
                    true
                }.asSingle
            )
        } ?: false

    companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    private fun soknadToJsonString(soknad: JsonNode): String = objectMapper.writeValueAsString(soknad)
}
