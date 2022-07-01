package no.nav.hjelpemidler.brille.medlemskap

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.azuread.AzureAdClient
import java.time.LocalDate

private val log = KotlinLogging.logger {}

class MedlemskapClient(
    private val props: Configuration.MedlemskapOppslagProperties = Configuration.medlemskapOppslagProperties,
    private val azureAdClient: AzureAdClient,
    engine: HttpClientEngine = CIO.create(),
) {
    private val client = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        }
        install(Auth) {
            bearer {
                loadTokens { azureAdClient.getToken(props.scope).toBearerTokens() }
                refreshTokens { null }
                sendWithoutRequest { true }
            }
        }
    }

    fun slåOppMedlemskap(fnr: String): JsonNode = runBlocking {
        val now = LocalDate.now()
        val response = client.post(props.baseUrl) {
            contentType(Json)
            setBody(
                Request(
                    fnr = fnr,
                    førsteDagForYtelse = now,
                    periode = RequestPeriode(now, now),
                    brukerinput = RequestBrukerinfo(false),
                )
            )
        }
        response.body()
    }
}

private data class Request(
    val fnr: String,
    val førsteDagForYtelse: LocalDate,
    val periode: RequestPeriode,
    val brukerinput: RequestBrukerinfo,
)

private data class RequestPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
)

private data class RequestBrukerinfo(
    val arbeidUtenforNorge: Boolean,
)
