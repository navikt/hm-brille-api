package no.nav.hjelpemidler.brille.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.pdl.PdlService
import no.nav.hjelpemidler.brille.syfohelsenettproxy.SyfohelsenettproxyClient
import no.nav.hjelpemidler.brille.tss.TssIdentRiver
import org.slf4j.LoggerFactory

private val log = KotlinLogging.logger {}

fun Route.internalRoutes(
    selfTestService: SelfTestService,
    kafkaService: KafkaService,
    pdlService: PdlService,
    syfohelsenettproxyClient: SyfohelsenettproxyClient,
    enhetsregisteretService: EnhetsregisteretService,
) {
    route("/internal") {
        get("/is-alive") {
            if (kafkaService.isProducerClosed())
                call.respondText("Kafka producer is not running", status = HttpStatusCode.InternalServerError)
            else if (kafkaService.isConsumerClosed()) // Don't restart app, even consumer is closed.
                call.respondText("Kafka consumer is not running", status = HttpStatusCode.OK)
            else
                call.respondText("Application is alive!", status = HttpStatusCode.OK)
        }

        get("/is-ready") {
            if (kafkaService.isProducerClosed())
                call.respondText("Kafka producer is not running", status = HttpStatusCode.InternalServerError)
            else
                call.respondText("Application is ready!", status = HttpStatusCode.OK)
        }

        post("/deep-ping") {
            // Sjekk Rapid and rivers:
            if (kafkaService.isProducerClosed()) {
                return@post call.respond(HttpStatusCode.InternalServerError, "Kafka producer is closed in hm-brille-api")
            }
            if (kafkaService.isConsumerClosed()) {
                return@post call.respond(HttpStatusCode.InternalServerError, "Kafka consumer is closed in hm-brille-api")
            }

            // Sjekk Brille-api postgres database
            val dbTest = runCatching { selfTestService.sjekkDatabaseKobling() }.getOrElse { err ->
                log.error(err) { "Exception mens man sjekket database kobling som en del av en deep-ping" }
                return@getOrElse null
            }
            if (dbTest != true) return@post call.respond(HttpStatusCode.InternalServerError, "sjekk av databasekobling feilet")

            // TODO: Sjekk Saksbehandler/hotsak rekursivt

            // TODO: Sjekk Helsepersonellregisteret (HPR)

            // TODO: Sjekk Persondataløsningen (PDL)

            // Sjekk Enhetsregisteret
            runCatching { enhetsregisteretService.hentOrganisasjonsenhet("889640782") }.getOrElse { err ->
                log.error(err) { "Exception mens man sjekket enhetsregisteret som en del av en deep-ping" }
                return@getOrElse null
            } ?: return@post call.respond(HttpStatusCode.InternalServerError, "ingen kontakt med enhetsregisteret")

            // Ferdig
            call.respond(HttpStatusCode.OK, "Klar!")
        }
    }
}
