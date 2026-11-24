package no.nav.hjelpemidler.brille

import no.nav.hjelpemidler.serialization.jackson.JacksonObjectMapperProvider
import no.nav.hjelpemidler.serialization.jackson.defaultJsonMapper
import no.nav.hjelpemidler.service.LoadOrder
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.cfg.EnumFeature

fun ObjectMapper.writePrettyString(value: Any?): String = writerWithDefaultPrettyPrinter().writeValueAsString(value)

@LoadOrder(0)
class ApplicationJacksonObjectMapperProvider : JacksonObjectMapperProvider {
    override fun invoke(): ObjectMapper = defaultJsonMapper {
        configureForJackson2()
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        disable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
        disable(StreamReadFeature.AUTO_CLOSE_SOURCE) // KTOR-8016
    }
}
