package no.nav.hjelpemidler.brille.pdl

import com.expediagroup.graphql.client.jackson.GraphQLClientJacksonSerializer
import com.expediagroup.graphql.client.ktor.GraphQLKtorClient
import com.expediagroup.graphql.client.types.GraphQLClientError
import com.expediagroup.graphql.client.types.GraphQLClientRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.http.HttpHeaders
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.MDC_CORRELATION_ID
import no.nav.hjelpemidler.brille.StubEngine
import no.nav.hjelpemidler.brille.engineFactory
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.pdl.generated.HentPerson
import no.nav.hjelpemidler.brille.pdl.generated.MedlemskapHentBarn
import no.nav.hjelpemidler.brille.tilgang.innloggetBruker
import no.nav.hjelpemidler.http.openid.azureAD
import org.slf4j.MDC
import java.net.URL
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class PdlClient(
    props: Configuration.PdlProperties,
    val engine: HttpClientEngine = engineFactory { StubEngine.pdl() },
) {
    private val baseUrl = props.baseUrl
    private val client = GraphQLKtorClient(
        url = URL(baseUrl),
        httpClient = HttpClient(engine) {
            azureAD(scope = props.scope) {
                cache(leeway = 10.seconds)
            }
            defaultRequest {
                header("behandlingsnummer", "B152")
                header("X-Correlation-ID", UUID.randomUUID().toString())
            }
        },
        serializer = GraphQLClientJacksonSerializer(),
    )

    private fun List<GraphQLClientError>.inneholderKode(kode: String) = this
        .map { it.extensions ?: emptyMap() }
        .map { it["code"] }
        .any { it == kode }

    private suspend fun <Request : Any, Person, T : PdlOppslag<Person>> execute(
        request: GraphQLClientRequest<Request>,
        block: (Request) -> T,
    ): T {
        val response = client.execute(request)
        val data = response.data
        val errors = response.errors
        return when {
            errors != null -> {
                when {
                    errors.inneholderKode(PdlNotFoundException.KODE) -> throw PdlNotFoundException()
                    errors.inneholderKode(PdlBadRequestException.KODE) -> throw PdlBadRequestException()
                    errors.inneholderKode(PdlUnauthenticatedException.KODE) -> throw PdlUnauthenticatedException()
                    else -> throw PdlClientException(errors)
                }
            }

            data != null -> {
                val oppslag = block(data)
                if (oppslag.harAdressebeskyttelse() && !innloggetBruker().kanBehandlePersonerMedAdressebeskyttelse()) {
                    throw PdlHarAdressebeskyttelseException()
                }
                oppslag
            }

            else -> throw PdlClientException("Svar fra PDL mangler både data og errors")
        }
    }

    suspend fun hentPerson(fnr: String): PdlOppslagPerson =
        execute(HentPerson(HentPerson.Variables(fnr))) {
            PdlOppslagPerson(it.hentPerson, jsonMapper.valueToTree(it))
        }

    suspend fun medlemskapHentBarn(fnr: String): PdlOppslagBarn =
        execute(MedlemskapHentBarn(MedlemskapHentBarn.Variables(fnr))) {
            PdlOppslagBarn(it.hentPerson, jsonMapper.valueToTree(it))
        }

    suspend fun helseSjekk() {
        // Bruker en throw-away klient for helesjekken (ingen connection pooling, auth, etc.)
        val uid = MDC.get(MDC_CORRELATION_ID)
        val throwAwayClient = HttpClient(engine) {
            expectSuccess = true
            defaultRequest {
                header("Tema", "HJE")
                header(HttpHeaders.XCorrelationId, uid)
            }
        }
        // Kjør en "OPTIONS" spørring mot graphql api-endepunkt, og kast exception om svaret er non-2xx
        // ref.: https://pdldocs-navno.msappproxy.net/ekstern/index.html#pdlapi-ping-og-helsesjekk
        throwAwayClient.options(baseUrl)
    }
}
