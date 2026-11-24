package no.nav.hjelpemidler.brille

import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.LocalDateTime

class ApplicationJacksonObjectMapperProviderTest {
    private val expectedJson =
        """{"årsak":"test","dato":"2026-01-02","tidspunkt":"2026-01-02T03:04:05","resultat":"AKTIV","valgfri":null,"antall":0}"""
    private val inputJson = expectedJson.dropLast(1).replace(""""antall":0""", """"antall":null""") + ""","ukjent":true}"""
    private val value = JsonContract(
        årsak = "test",
        dato = LocalDate.of(2026, 1, 2),
        tidspunkt = LocalDateTime.of(2026, 1, 2, 3, 4, 5),
        resultat = Resultat.AKTIV,
    )

    @Test
    fun `JSON preserves Norwegian property names dates enums nulls and property order`() {
        jsonMapper.writeValueAsString(value) shouldBe expectedJson
        jsonMapper.readValue<JsonContract>(expectedJson) shouldBe value
    }

    @Test
    fun `JSON still ignores unknown fields and accepts null for primitive values`() {
        jsonMapper.readValue<JsonContract>(inputJson) shouldBe value
    }

    @Test
    fun `HTTP serialization preserves the same JSON contract`() = testApplication {
        application {
            configure()
            routing {
                post("/json-contract") {
                    call.respond(call.receive<JsonContract>())
                }
            }
        }
        val response = client.post("/json-contract") {
            contentType(ContentType.Application.Json)
            setBody(inputJson)
        }
        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldBe expectedJson
    }

    data class JsonContract(
        val årsak: String,
        val dato: LocalDate,
        val tidspunkt: LocalDateTime,
        val resultat: Resultat,
        val valgfri: String? = null,
        val antall: Int = 0,
    )

    enum class Resultat {
        AKTIV,
        ;

        override fun toString(): String = "aktiv"
    }
}
