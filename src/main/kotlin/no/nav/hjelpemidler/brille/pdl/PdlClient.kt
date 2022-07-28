package no.nav.hjelpemidler.brille.pdl

import com.expediagroup.graphql.client.jackson.GraphQLClientJacksonSerializer
import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientError
import com.expediagroup.graphql.client.types.GraphQLClientRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.header
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.StubEngine
import no.nav.hjelpemidler.brille.azuread.AzureAdClient
import no.nav.hjelpemidler.brille.engineFactory
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.pdl.generated.HentPerson
import no.nav.hjelpemidler.brille.pdl.generated.MedlemskapHentBarn
import no.nav.hjelpemidler.brille.pdl.generated.MedlemskapHentVergeEllerForelder
import java.net.URL
import java.util.UUID

private val log = KotlinLogging.logger { }

class PdlClient(
    props: Configuration.PdlProperties,
    engine: HttpClientEngine = engineFactory { StubEngine.pdl() },
) {
    private val baseUrl = props.baseUrl
    private val scope = props.scope
    private val client = GraphQLKtorClient(
        url = URL(baseUrl),
        httpClient = HttpClient(engine) {
            // Manuell håndtering av Azure Ad nødvendig her, da Auth-plugin'en bare fornyer tokens hvis den mottar
            // 401 fra serveren. PDL svarer bare 200-OK med en payload som har unauthenticated-code i error-feltet.
            /* install(Auth) {
                azureAd(scope)
            } */
        },
        serializer = GraphQLClientJacksonSerializer(),
    )
    private val azureAdClient: AzureAdClient = AzureAdClient(Configuration.azureAdProperties)

    private fun List<GraphQLClientError>.inneholderKode(kode: String) = this
        .map { it.extensions ?: emptyMap() }
        .map { it["code"] }
        .any { it == kode }

    private suspend fun <T : Any, R> execute(
        request: GraphQLClientRequest<T>,
        block: (T) -> PersonMedAdressebeskyttelse<R>,
    ): PdlOppslag<R?> {
        val token = azureAdClient.getTokenCached(scope).accessToken
        val response = client.execute(request) {
            header("Tema", "HJE")
            header("X-Correlation-ID", UUID.randomUUID().toString())
            header("Authorization", "Bearer $token")
        }
        return when {
            response.errors != null -> {
                val errors = response.errors!!
                when {
                    errors.inneholderKode(PdlNotFoundException.KODE) -> throw PdlNotFoundException()
                    errors.inneholderKode(PdlBadRequestException.KODE) -> throw PdlBadRequestException()
                    errors.inneholderKode(PdlUnauthenticatedException.KODE) -> throw PdlUnauthenticatedException()
                    else -> throw PdlClientException(errors)
                }
            }
            response.data != null -> {
                val data = response.data!!
                val personMedAdressebeskyttelse = block(data)
                if (personMedAdressebeskyttelse.harAdressebeskyttelse()) throw PdlHarAdressebeskyttelseException()
                PdlOppslag(personMedAdressebeskyttelse.person, jsonMapper.valueToTree(response.data))
            }
            else -> throw PdlClientException("Svar fra PDL mangler både data og errors")
        }
    }

    suspend fun hentPerson(fnr: String): PdlOppslag<Person?> =
        execute(HentPerson(HentPerson.Variables(fnr))) {
            val person = it.hentPerson
            PersonMedAdressebeskyttelse(person, person::harAdressebeskyttelse)
        }

    suspend fun medlemskapHentBarn(fnr: String): PdlOppslag<Barn?> =
        execute(MedlemskapHentBarn(MedlemskapHentBarn.Variables(fnr))) {
            val person = it.hentPerson
            PersonMedAdressebeskyttelse(person, person::harAdressebeskyttelse)
        }

    suspend fun medlemskapHentVergeEllerForelder(fnr: String): PdlOppslag<VergeEllerForelder?> =
        execute(MedlemskapHentVergeEllerForelder(MedlemskapHentVergeEllerForelder.Variables(fnr))) {
            val person = it.hentPerson
            PersonMedAdressebeskyttelse(person, person::harAdressebeskyttelse)
        }

    private data class PersonMedAdressebeskyttelse<T>(val person: T, val harAdressebeskyttelse: () -> Boolean)
}
