package no.nav.hjelpemidler.brille.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.hjelpemidler.brille.altinn.Altinn3Client
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.hotsak.HotsakClient
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.pdl.PdlService
import no.nav.hjelpemidler.brille.syfohelsenettproxy.SyfohelsenettproxyClient

private val log = KotlinLogging.logger {}

fun Route.internalRoutes(
    databaseContext: DatabaseContext,
    kafkaService: KafkaService,
    hotsakClient: HotsakClient,
    pdlService: PdlService,
    syfohelsenettproxyClient: SyfohelsenettproxyClient,
    enhetsregisteretService: EnhetsregisteretService,
    altinn3Client: Altinn3Client,
) {
    route("/internal") {
        get("/is-alive") {
            if (kafkaService.isProducerClosed()) {
                call.respondText("Kafka producer is not running", status = HttpStatusCode.InternalServerError)
            } else if (kafkaService.isConsumerClosed()) {
                // Don't restart app, even consumer is closed.
                call.respondText("Kafka consumer is not running", status = HttpStatusCode.OK)
            } else {
                call.respondText("Application is alive!", status = HttpStatusCode.OK)
            }
        }

        get("/is-ready") {
            if (kafkaService.isProducerClosed()) {
                call.respondText("Kafka producer is not running", status = HttpStatusCode.InternalServerError)
            } else {
                call.respondText("Application is ready!", status = HttpStatusCode.OK)
            }
        }

        get("/deep-ping") {
            // Sjekk Rapid and rivers:
            if (kafkaService.isProducerClosed()) {
                return@get call.respond(HttpStatusCode.InternalServerError, "Kafka producer is closed in hm-brille-api")
            }
            if (kafkaService.isConsumerClosed()) {
                return@get call.respond(HttpStatusCode.InternalServerError, "Kafka consumer is closed in hm-brille-api")
            }

            // Sjekk Brille-api postgres database
            val dbTest = runCatching { databaseContext.dataSource.connection!!.use { it.isValid(5) } }.onFailure { e ->
                log.error(e) { "Exception mens man sjekket database kobling som en del av en deep-ping" }
            }.getOrNull()
            if (dbTest != true) return@get call.respond(HttpStatusCode.InternalServerError, "sjekk av databasekobling feilet")

            // Sjekk Saksbehandler/hotsak rekursivt
            runCatching { hotsakClient.deepPing() }.getOrElse { e ->
                log.error(e) { "Exception mens man sjekket hotsak som en del av en deep-ping" }
                return@get call.respond(HttpStatusCode.InternalServerError, "sjekk av hotsak feilet")
            }

            // Sjekk Helsepersonellregisteret (HPR)
            runCatching { syfohelsenettproxyClient.ping() }.getOrElse { e ->
                log.error(e) { "Exception mens man sjekket HPR som en del av en deep-ping" }
                return@get call.respond(HttpStatusCode.InternalServerError, "sjekk av HPR feilet")
            }

            // Sjekk Persondataløsningen (PDL)
            runCatching { pdlService.helseSjekk() }.getOrElse { e ->
                log.error(e) { "Exception mens man sjekket PDL som en del av en deep-ping" }
                return@get call.respond(HttpStatusCode.InternalServerError, "sjekk av pdl feilet")
            }

            // Sjekk Enhetsregisteret
            runCatching { enhetsregisteretService.hentOrganisasjonsenhet("889640782") }.onFailure { e ->
                log.error(e) { "Exception mens man sjekket enhetsregisteret som en del av en deep-ping" }
            }.getOrNull() ?: return@get call.respond(HttpStatusCode.InternalServerError, "ingen kontakt med enhetsregisteret")

            // Ferdig
            call.respond(HttpStatusCode.OK, "Klar!")
        }

        post("/sync-brreg") {
            runCatching {
                enhetsregisteretService.oppdaterMirrorHvisUtdatert(oppdaterUansett = true)
            }.onFailure { e ->
                log.error(e) { "sync-brreg endpoint: Feil under oppdatering av vår kopi av enhetsregisteret" }
            }.getOrNull() ?: return@post call.respond(HttpStatusCode.InternalServerError, "feil under manuell sync brreg")

            // Ferdig
            call.respond(HttpStatusCode.OK, "Done!")
        }

        route("/enhetsregisteret") {
            get("/mirror-med-fallback/{orgnr}") {
                val orgnr = call.parameters["orgnr"]!!.trim()
                val enhet = runCatching { enhetsregisteretService.hentOrganisasjonsenhet(orgnr) }.onFailure { e ->
                    log.error(e) { "henting av organisasjonsenhet feilet" }
                }.getOrNull() ?: return@get call.respond(HttpStatusCode.InternalServerError, "feilet i å hente organisasjonsenhet")
                call.respond(enhet)
            }
            get("/slettet-eller-ei/{orgnr}") {
                val orgnr = call.parameters["orgnr"]!!.trim()
                val enhet = runCatching { enhetsregisteretService.organisasjonSlettet(orgnr) }.onFailure { e ->
                    log.error(e) { "henting av organisasjons slettet feilet" }
                }.getOrNull() ?: return@get call.respond(HttpStatusCode.InternalServerError, "feilet i å hente organisasjon slettet")
                call.respond(enhet)
            }
            get("/slettet-dato/{orgnr}") {
                val orgnr = call.parameters["orgnr"]!!.trim()
                val enhet = runCatching { enhetsregisteretService.organisasjonSlettetNår(orgnr) }.onFailure { e ->
                    log.error(e) { "henting av organisasjons slettet når feilet" }
                }.getOrNull() ?: return@get call.respond(HttpStatusCode.InternalServerError, "feilet i å hente organisasjon slettet når")
                call.respond(enhet)
            }
        }

        post("/test-altinn3") {
            data class Request(
                val fnr: String,
            )
            altinn3Client.test(call.receive<Request>().fnr)
        }
    }
}
