package no.nav.hjelpemidler.brille.model

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.util.UUID

data class SoknadData (
    val soknadsId: UUID,
    val fnrBruker: String,
    val fnrInnsender: String,
    val data: JsonNode,
    val opprettet: LocalDateTime,
)
