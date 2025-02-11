package no.nav.hjelpemidler.brille

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.hjelpemidler.serialization.jackson.JacksonObjectMapperProvider
import no.nav.hjelpemidler.serialization.jackson.defaultJsonMapper
import no.nav.hjelpemidler.service.LoadOrder

fun ObjectMapper.writePrettyString(value: Any?): String = writerWithDefaultPrettyPrinter().writeValueAsString(value)

@LoadOrder(0)
class ApplicationJacksonObjectMapperProvider : JacksonObjectMapperProvider {
    override fun invoke(): ObjectMapper = defaultJsonMapper {
        disable(JsonParser.Feature.AUTO_CLOSE_SOURCE) // KTOR-8016
    }
}
