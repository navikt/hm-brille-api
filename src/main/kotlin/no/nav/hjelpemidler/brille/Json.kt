package no.nav.hjelpemidler.brille

import com.fasterxml.jackson.databind.ObjectMapper

val jsonMapper: ObjectMapper = no.nav.hjelpemidler.serialization.jackson.jsonMapper

fun ObjectMapper.writePrettyString(value: Any?): String = writerWithDefaultPrettyPrinter().writeValueAsString(value)
