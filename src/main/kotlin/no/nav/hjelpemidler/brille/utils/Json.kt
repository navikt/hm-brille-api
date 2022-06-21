package no.nav.hjelpemidler.brille

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row

val jsonMapper: JsonMapper = jacksonMapperBuilder()
    .addModule(JavaTimeModule())
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .build()

inline fun <reified T> Row.json(columnLabel: String): T = string(columnLabel).let {
    jsonMapper.readValue(it)
}

inline fun <reified T> Row.jsonOrNull(columnLabel: String): T? = stringOrNull(columnLabel)?.let {
    jsonMapper.readValue(it)
}
