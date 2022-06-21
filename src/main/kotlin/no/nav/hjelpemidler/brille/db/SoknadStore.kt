package no.nav.hjelpemidler.brille.db

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.hjelpemidler.brille.model.SoknadData
import org.postgresql.util.PGobject
import javax.sql.DataSource

internal interface SoknadStore {
    fun save(soknadData: SoknadData): Int
}

internal class SoknadStorePostgres(private val ds: DataSource) : SoknadStore {
    override fun save(soknadData: SoknadData): Int =
        using(sessionOf(ds)) { session ->
            session.transaction { transaction ->
                // Add the new status to the status table
                transaction.run(
                    queryOf(
                        "INSERT INTO soknad (soknads_id, fnr_bruker, fnr_innsender, data) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",
                        soknadData.soknadsId,
                        soknadData.fnrBruker,
                        soknadData.fnrInnsender,
                        PGobject().apply {
                            type = "jsonb"
                            value = soknadToJsonString(soknadData.data)
                        },
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
