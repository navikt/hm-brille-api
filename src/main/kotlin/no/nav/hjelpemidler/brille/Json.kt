package no.nav.hjelpemidler.brille

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row

val jsonMapper: ObjectMapper = no.nav.hjelpemidler.serialization.jackson.jsonMapper

fun ObjectMapper.writePrettyString(value: Any?): String = writerWithDefaultPrettyPrinter().writeValueAsString(value)

inline fun <reified T> Row.json(columnLabel: String): T = string(columnLabel).let {
    jsonMapper.readValue(it)
}

inline fun <reified T> Row.jsonOrNull(columnLabel: String): T? = stringOrNull(columnLabel)?.let {
    jsonMapper.readValue(it)
}
