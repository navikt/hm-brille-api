package no.nav.hjelpemidler.brille.pdl

import com.expediagroup.graphql.client.jackson.GraphQLClientJacksonSerializer
import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.header
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.StubEngine
import no.nav.hjelpemidler.brille.azuread.AzureAdClient
import no.nav.hjelpemidler.brille.engineFactory
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.pdl.generated.HentPerson
import no.nav.hjelpemidler.brille.pdl.generated.MedlemskapHentBarn
import no.nav.hjelpemidler.brille.pdl.generated.MedlemskapHentVergeEllerForelder
import no.nav.hjelpemidler.brille.writePrettyString
import java.net.URL
import java.util.UUID

private val log = KotlinLogging.logger { }

class PdlClient(
    baseUrl: String,
    private val scope: String,
    private val azureAdClient: AzureAdClient,
    engine: HttpClientEngine = engineFactory { StubEngine.pdl() },
) {
    private val client = GraphQLKtorClient(
        url = URL(baseUrl),
        httpClient = HttpClient(engine) {
            install(Auth) {
                bearer {
                    loadTokens { azureAdClient.getToken(scope).toBearerTokens() }
                    refreshTokens { null }
                    sendWithoutRequest { true }
                }
            }
        },
        serializer = GraphQLClientJacksonSerializer(),
    )

    private suspend fun <T : Any> execute(request: GraphQLClientRequest<T>): PdlOppslag<T?> {
        val response = client.execute(request) {
            header("Tema", "HJE")
            header("X-Correlation-ID", UUID.randomUUID().toString())
        }
        return when {
            response.errors != null -> throw PdlClientException(jsonMapper.writePrettyString(response.errors))
            response.data != null -> PdlOppslag(response.data, jsonMapper.valueToTree(response.data))
            else -> throw PdlClientException("Svar fra PDL mangler både data og errors")
        }
    }

    suspend fun hentPerson(fnr: String): PdlOppslag<HentPerson.Result?> =
        execute(HentPerson(HentPerson.Variables(fnr)))

    suspend fun medlemskapHentBarn(fnr: String): PdlOppslag<MedlemskapHentBarn.Result?> =
        execute(MedlemskapHentBarn(MedlemskapHentBarn.Variables(fnr)))

    suspend fun medlemskapHentVergeEllerForelder(fnr: String): PdlOppslag<MedlemskapHentVergeEllerForelder.Result?> =
        execute(MedlemskapHentVergeEllerForelder(MedlemskapHentVergeEllerForelder.Variables(fnr)))

    class PdlClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
}
