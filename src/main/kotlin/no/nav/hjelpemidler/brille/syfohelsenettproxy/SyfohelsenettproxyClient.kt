package no.nav.hjelpemidler.brille.syfohelsenettproxy

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.MDC_CORRELATION_ID
import no.nav.hjelpemidler.brille.SjekkOptikerPluginException
import no.nav.hjelpemidler.brille.StubEngine
import no.nav.hjelpemidler.brille.engineFactory
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import no.nav.hjelpemidler.http.openid.openID
import no.nav.hjelpemidler.logging.secureInfo
import org.slf4j.MDC

private val log = KotlinLogging.logger { }

class SyfohelsenettproxyClient(
    tokenSetProvider: TokenSetProvider,
    engine: HttpClientEngine = engineFactory(StubEngine::syfohelsenettproxy),
) {
    private val baseUrl = Configuration.SYFOHELSENETTPROXY_API_URL
    private val client = createHttpClient(engine) {
        expectSuccess = true
        openID(tokenSetProvider)
    }

    suspend fun ping() {
        try {
            val url = "$baseUrl/api/v2/ping"
            val uid = MDC.get(MDC_CORRELATION_ID)
            log.info { "Ping'er HPR med url: $url (reuqestId=$uid)" }
            val response = client.get(url) {
                expectSuccess = true
                header("requestId", uid)
                header(HttpHeaders.XCorrelationId, uid)
            }
            log.info { "Har fått response fra HPR med status: ${response.status}" }
        } catch (clientReqException: ClientRequestException) {
            throw SyfohelsenettproxyClientException("Feil under ping av hpr", clientReqException)
        } catch (e: Exception) {
            throw SyfohelsenettproxyClientException("Ukjent feil under ping av hpr", e)
        }
    }

    suspend fun hentBehandler(fnr: String): Behandler? {
        try {
            val url = "$baseUrl/api/v2/behandler"
            log.info { "Henter behandler data med url: $url" }
            val response = client.get(url) {
                headers["behandlerFnr"] = fnr
            }
            log.info { "Har fått response fra HPR med status: ${response.status}" }
            when (response.status) {
                HttpStatusCode.OK -> {
                    val behandler = response.body<Behandler>()
                    log.secureInfo { "Fikk svar fra HPR: $behandler" }
                    return behandler
                }
            }
            throw SyfohelsenettproxyClientException("Uventet svar fra tjeneste: ${response.status}", null)
        } catch (clientReqException: ClientRequestException) {
            if (clientReqException.message.contains("Fant ikke behandler")) {
                return null
            } else {
                throw SyfohelsenettproxyClientException("Feil under henting av behandler data", clientReqException)
            }
        } catch (e: Exception) {
            throw SyfohelsenettproxyClientException("Ukjent feil under henting av behandler data", e)
        }
    }

    suspend fun hentBehandlerMedHprNummer(hprnr: String): Behandler = runCatching {
        val url = "$baseUrl/api/v2/behandlerMedHprNummer"
        log.info { "Henter behandler data med url: $url" }
        val response = client.get(url) {
            headers["hprNummer"] = hprnr
        }
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        }
        throw SyfohelsenettproxyClientException("Uventet svar fra tjeneste: ${response.status}", null)
    }.getOrElse { throw SyfohelsenettproxyClientException("Feil under henting av behandler data", it) }
}

fun Route.sjekkErOptiker(syfohelsenettproxyClient: SyfohelsenettproxyClient) {
    post("/admin/sjekkErOptikerMedHprnr/{hprnr}") {
        kotlin.runCatching {
            val hprnr = call.parameters["hprnr"] ?: error("Mangler hprnr i url")

            val behandler =
                runCatching { syfohelsenettproxyClient.hentBehandlerMedHprNummer(hprnr) }.getOrElse {
                    log.error(it) { "Feil oppstod ved kall mot HPR" }
                    throw SjekkOptikerPluginException(
                        HttpStatusCode.InternalServerError,
                        "Kunne ikke hente data fra syfohelsenettproxyClient: $it",
                        it,
                    )
                }

            data class Response(
                val behandler: Behandler,
                val erOptiker: Boolean,
            )

            call.respond(
                Response(
                    behandler = behandler,
                    erOptiker = behandler.godkjenninger.any {
                        it.helsepersonellkategori?.aktiv == true && it.helsepersonellkategori.verdi == "OP"
                    },
                ),
            )
        }.getOrElse {
            log.error(it) { "sjekkErOptikerMedHprnr feilet!" }
            call.respond(HttpStatusCode.InternalServerError, it.stackTraceToString())
        }
    }
    post("/admin/sjekkErOptikerMedFnre") {
        kotlin.runCatching {
            val fnre = call.receive<List<String>>().toSet().toList()

            data class ResponseItem(
                val fnr: String,
                val behandler: Behandler?,
                val erOptiker: Boolean?,
                val navnFnrKombo: String,
            )

            data class Response(
                val items: List<ResponseItem>,
            )

            val responseItems = fnre.map { fnr ->
                val behandler =
                    runCatching { syfohelsenettproxyClient.hentBehandler(fnr) }.getOrElse {
                        log.error(it) { "Feil oppstod ved kall mot HPR" }
                        throw SjekkOptikerPluginException(
                            HttpStatusCode.InternalServerError,
                            "Kunne ikke hente data fra syfohelsenettproxyClient: $it",
                            it,
                        )
                    }

                ResponseItem(
                    fnr = fnr,
                    behandler = behandler,
                    erOptiker = behandler?.godkjenninger?.any {
                        it.helsepersonellkategori?.aktiv == true && it.helsepersonellkategori.verdi == "OP"
                    },
                    navnFnrKombo = "fnr=$fnr navn=${behandler?.navn()}",
                )
            }

            call.respond(
                Response(
                    items = responseItems,
                ),
            )
        }.getOrElse {
            log.error(it) { "sjekkErOptikerMedFnre feilet!" }
            call.respond(HttpStatusCode.InternalServerError, it.stackTraceToString())
        }
    }
}
