package no.nav.hjelpemidler.brille.pdl

import com.fasterxml.jackson.databind.JsonNode

data class PdlOppslag<T>(val data: T, val rawData: JsonNode)
