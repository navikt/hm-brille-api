package no.nav.hjelpemidler.brille.pdl

import com.expediagroup.graphql.client.jackson.types.JacksonGraphQLResponse
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import no.nav.hjelpemidler.brille.pdl.generated.HentPerson
import no.nav.hjelpemidler.brille.test.TestTokenSetProvider
import no.nav.hjelpemidler.brille.tilgang.InnloggetBruker
import no.nav.hjelpemidler.brille.tilgang.TilgangContextElement
import no.nav.hjelpemidler.brille.tilgang.withTilgangContext
import no.nav.hjelpemidler.serialization.jackson.jsonMapper
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.Test

class PdlClientTest {
    @Test
    fun `happy case`() = test("/mock/pdl.json") { client ->
        val oppslag = client.hentPerson("07121410995")
        oppslag.shouldNotBeNull()
    }

    @Test
    fun `not found`() = test("/mock/pdl_not_found.json") { client ->
        assertThrows<PdlNotFoundException> {
            client.hentPerson("07121410995")
        }
    }

    @Test
    fun `bad request`() = test("/mock/pdl_bad_request.json") { client ->
        assertThrows<PdlBadRequestException> {
            client.hentPerson("07121410995")
        }
    }

    @Test
    fun `person har adressebeskyttelse`() = test("/mock/pdl_har_adressebeskyttelse.json") { client ->
        assertThrows<PdlHarAdressebeskyttelseException> {
            client.hentPerson("07121410995")
        }
    }

    @Test
    fun `skal kunne hente person med adressebeskyttelse hvis azure ad systembruker for saksbehandling`() =
        test(
            "/mock/pdl_har_adressebeskyttelse.json",
            InnloggetBruker.AzureAd.SystembrukerSaksbehandling(UUID.randomUUID()),
        ) { client ->
            assertDoesNotThrow {
                client.hentPerson("07121410995")
            }
        }

    @Test
    fun `skal ikke kunne hente person med adressebeskyttelse hvis azure ad systembruker for brille integrasjon`() =
        test(
            "/mock/pdl_har_adressebeskyttelse.json",
            InnloggetBruker.AzureAd.SystembrukerBrilleIntegrasjon(UUID.randomUUID()),
        ) { client ->
            assertThrows<PdlHarAdressebeskyttelseException> {
                client.hentPerson("07121410995")
            }
        }

    private fun test(
        name: String,
        currentUser: InnloggetBruker = InnloggetBruker.Ingen,
        block: suspend (PdlClient) -> Unit,
    ) {
        runTest {
            withTilgangContext(TilgangContextElement(currentUser)) {
                block(
                    PdlClient(
                        TestTokenSetProvider(),
                        engine = javaClass.getResourceAsStream(name).use {
                            val response = requireNotNull(it).bufferedReader().readText()
                            MockEngine {
                                respond(response)
                            }
                        },
                    ),
                )
            }
        }
    }
}

fun lagMockPdlOppslag(fødselsdato: String): PdlOppslagPerson {
    val pdlPersonResponse = PdlClientTest::class.java.getResourceAsStream("/mock/pdl.json").use {
        val json = requireNotNull(it).bufferedReader().readText().replace("2014-08-15", fødselsdato)
        jsonMapper.readValue<JacksonGraphQLResponse<HentPerson.Result?>>(json)
    }
    return PdlOppslagPerson(pdlPersonResponse.data?.hentPerson, jsonMapper.nullNode())
}
