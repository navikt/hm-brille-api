package no.nav.hjelpemidler.brille.vedtak

import com.fasterxml.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class Vedak_v2(
    val id: Int,
    val fnrBruker: String,
    val fnrInnsender: String,
    val orgnr: String,
    val bestillingsdato: LocalDate,
    val brillepris: BigDecimal,
    val bestillingsref: String,
    val vilkarsvurdering: JsonNode,
    val status: String,
    val opprettet: LocalDateTime,
)
