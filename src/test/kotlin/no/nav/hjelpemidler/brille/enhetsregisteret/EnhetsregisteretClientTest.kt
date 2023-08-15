package no.nav.hjelpemidler.brille.enhetsregisteret

import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.db.createDatabaseContext
import no.nav.hjelpemidler.brille.db.createDatabaseSessionContextWithMocks
import org.junit.Ignore
import kotlin.test.Test

@Ignore("Benyttes bare til manuell test av integrasjon mot enhetsregisteret")
internal class EnhetsregisteretClientTest {
    private val sessionContext = createDatabaseSessionContextWithMocks()
    private val databaseContext = createDatabaseContext(sessionContext)
    private val client = EnhetsregisteretClient(Configuration.enhetsregisteretProperties, databaseContext)
    private val service = EnhetsregisteretService(client, databaseContext)

    @Test
    internal fun `henter organisasjonsenhet`() = runBlocking {
        val orgnr = "929464958"

        every {
            sessionContext.enhetsregisteretStore.hentEnhet(any())
        } answers {
            Organisasjonsenhet(
                orgnr = orgnr,
                navn = "Hello",
            )
        }

        val enhet = service.hentOrganisasjonsenhet(orgnr)

        enhet?.orgnr shouldBe orgnr
    }

    /*
    private val mapper = ObjectMapper().let { mapper ->
        mapper.registerKotlinModule()
        mapper.registerModule(JavaTimeModule())
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        mapper
    }

    private val httpClient = createHttpClient {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 10 * 60 * 1000
        }
    }

    @Test
    fun `somesome`() {
        // API docs: https://data.brreg.no/enhetsregisteret/api/docs/index.html#enheter-lastned
        runBlocking {
            httpClient.prepareGet("http://localhost:4040/enhetsregisteret/api/enheter/lastned") {
                header(HttpHeaders.Accept, "application/vnd.brreg.enhetsregisteret.enhet.v1+gzip;charset=UTF-8")
            }.execute { httpResponse ->
                strømOgBlåsOpp<Organisasjonsenhet>(httpResponse) { enhet ->
                    println("Enhet: $enhet")
                    return@strømOgBlåsOpp true
                }
            }
        }
    }

    private suspend inline fun <reified T> strømOgBlåsOpp(httpResponse: HttpResponse, block: (enhet: T) -> Boolean) {
        val contentLength = (httpResponse.contentLength() ?: -1)
        val contentLengthMB = contentLength / 1024 / 1024
        println("Komprimert filstørrelse: $contentLengthMB MiB ($contentLength bytes)")

        val gunzipStream = GZIPInputStream(httpResponse.bodyAsChannel().toInputStream())
        mapper.factory.createParser(gunzipStream).use { jsonParser ->
            // Check the first token
            check(jsonParser.nextToken() === JsonToken.START_ARRAY) { "Expected content to be an array" }

            // Iterate over the tokens until the end of the array
            while (jsonParser.nextToken() !== JsonToken.END_ARRAY) {
                // Read a Organisasjonsenhet2 instance using ObjectMapper and do something with it
                val enhet: T = mapper.readValue(jsonParser)
                if (!block(enhet)) {
                    break
                }
            }
        }
    }
    */
}
