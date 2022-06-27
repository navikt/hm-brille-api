package no.nav.hjelpemidler.brille

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.azuread.Token
import no.nav.hjelpemidler.brille.syfohelsenettproxy.Behandler
import kotlin.time.Duration.Companion.hours

class MockRoute(
    val url: String,
    val method: HttpMethod,
    val configure: MockRequestHandleScope.() -> HttpResponseData,
)

class MockEngineBuilder(private val routes: MutableList<MockRoute> = mutableListOf()) : List<MockRoute> by routes {
    private fun add(url: String, method: HttpMethod, configure: MockRequestHandleScope.() -> HttpResponseData) =
        routes.add(MockRoute(url = url, method = method, configure = configure))

    fun get(url: String, configure: MockRequestHandleScope.() -> HttpResponseData) =
        add(url = url, method = HttpMethod.Get, configure = configure)

    fun post(url: String, configure: MockRequestHandleScope.() -> HttpResponseData) =
        add(url = url, method = HttpMethod.Post, configure = configure)

    fun findOrElse(request: HttpRequestData, fallback: MockRequestHandleScope.() -> HttpResponseData): MockRoute =
        firstOrNull {
            it.url == request.url.encodedPath && it.method == request.method
        } ?: MockRoute(request.url.encodedPath, request.method, fallback)
}

object StubEngine {
    private val log = KotlinLogging.logger { }

    private fun <T> MockRequestHandleScope.respond(body: T): HttpResponseData =
        respond(
            jsonMapper.writeValueAsString(body),
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json")
        )

    private fun mockEngineBuilder(block: MockEngineBuilder.() -> Unit): MockEngineBuilder =
        MockEngineBuilder().apply(block)

    private fun mockEngine(block: MockEngineBuilder.() -> Unit): HttpClientEngine = MockEngine { request ->
        log.info { "Svarer på ${request.method.value} ${request.url}" }
        mockEngineBuilder(block)
            .findOrElse(request) { respondError(HttpStatusCode.NotFound) }
            .configure(this)
    }

    fun azureAd(): HttpClientEngine = mockEngine {
        post("/default/token") {
            respond(Token("", 1.hours.inWholeSeconds, "token"))
        }
    }

    fun tokenX(): HttpClientEngine = mockEngine {
        get("/default/.well-known/openid-configuration") {
            respond(
                mapOf(
                    "issuer" to "http://localhost:8080/default",
                    "jwks_uri" to "http://localhost:8080/default/jwks"
                )
            )
        }
    }

    fun syfohelsenettproxy(): HttpClientEngine = mockEngine {
        get("/syfohelsenettproxy/api/v2/behandler") {
            respond(
                Behandler(
                    godkjenninger = emptyList(),
                    fnr = null,
                    hprNummer = null,
                    fornavn = null,
                    mellomnavn = null,
                    etternavn = null
                )
            )
        }
    }
}
