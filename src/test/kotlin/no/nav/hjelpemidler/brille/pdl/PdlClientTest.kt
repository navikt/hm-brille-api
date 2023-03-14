package no.nav.hjelpemidler.brille.pdl

import com.expediagroup.graphql.client.jackson.types.JacksonGraphQLResponse
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.common.runBlocking
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.pdl.generated.HentPerson
import no.nav.hjelpemidler.brille.tilgang.TilgangContextElement
import no.nav.hjelpemidler.brille.tilgang.UserPrincipal
import no.nav.hjelpemidler.brille.tilgang.withTilgangContext
import no.nav.hjelpemidler.http.openid.TokenSet
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

internal class PdlClientTest {
    @Test
    internal fun `happy case`() = test("/mock/pdl.json") { client ->
        val oppslag = client.hentPerson("07121410995")
        oppslag.shouldNotBeNull()
    }

    @Test
    internal fun `not found`() = test("/mock/pdl_not_found.json") { client ->
        assertThrows<PdlNotFoundException> {
            client.hentPerson("07121410995")
        }
    }

    @Test
    internal fun `bad request`() = test("/mock/pdl_bad_request.json") { client ->
        assertThrows<PdlBadRequestException> {
            client.hentPerson("07121410995")
        }
    }

    @Test
    internal fun `person har adressebeskyttelse`() = test("/mock/pdl_har_adressebeskyttelse.json") { client ->
        assertThrows<PdlHarAdressebeskyttelseException> {
            client.hentPerson("07121410995")
        }
    }

    @Test
    fun `skal kunne hente person med adressebeskyttelse hvis azure ad systembruker`() =
        test("/mock/pdl_har_adressebeskyttelse.json", UserPrincipal.AzureAd.Systembruker(UUID.randomUUID())) { client ->
            assertDoesNotThrow {
                client.hentPerson("07121410995")
            }
        }

    private fun test(
        name: String,
        currentUser: UserPrincipal = UserPrincipal.Ingen,
        block: suspend (PdlClient) -> Unit,
    ) {
        runBlocking {
            withTilgangContext(TilgangContextElement(currentUser)) {
                block(
                    PdlClient(
                        props = Configuration.PdlProperties("http://localhost:1234", "test"),
                        engine = javaClass.getResourceAsStream(name).use {
                            val response = requireNotNull(it).bufferedReader().readText()
                            MockEngine {
                                when {
                                    it.url.toString().endsWith("/token") -> respond(
                                        jsonMapper.writeValueAsString(TokenSet.bearer(1.hours, "")),
                                        headers = headersOf("Content-Type", "application/json")
                                    )

                                    else -> respond(response)
                                }
                            }
                        }
                    )
                )
            }
        }
    }
}

fun lagMockPdlOppslag(fødselsdato: String): PdlOppslag<Person?> {
    val pdlPersonResponse = PdlClientTest::class.java.getResourceAsStream("/mock/pdl.json").use {
        val json = requireNotNull(it).bufferedReader().readText().replace("2014-08-15", fødselsdato)
        jsonMapper.readValue<JacksonGraphQLResponse<HentPerson.Result?>>(json)
    }

    return PdlOppslag(pdlPersonResponse.data?.hentPerson, jsonMapper.nullNode())
}
