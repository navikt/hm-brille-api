package no.nav.hjelpemidler.brille.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.hjelpemidler.brille.kafka.KafkaService

fun Route.internalRoutes(kafkaService: KafkaService) {
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
    }
}
