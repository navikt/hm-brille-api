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
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.hotsak.HotsakClient
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.pdl.PdlService
import no.nav.hjelpemidler.brille.syfohelsenettproxy.SyfohelsenettproxyClient
import no.nav.hjelpemidler.brille.tss.TssIdentRiver
import org.slf4j.LoggerFactory

private val log = KotlinLogging.logger {}

fun Route.internalRoutes(
    databaseContext: DatabaseContext,
    kafkaService: KafkaService,
    hotsakClient: HotsakClient,
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
            runCatching { enhetsregisteretService.hentOrganisasjonsenhet("889640782", cacheBusting = true) }.onFailure { e ->
                log.error(e) { "Exception mens man sjekket enhetsregisteret som en del av en deep-ping" }
            }.getOrNull() ?: return@get call.respond(HttpStatusCode.InternalServerError, "ingen kontakt med enhetsregisteret")

            // Ferdig
            call.respond(HttpStatusCode.OK, "Klar!")
        }
    }
}
