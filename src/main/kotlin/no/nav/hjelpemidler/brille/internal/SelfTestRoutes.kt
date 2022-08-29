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
            if (kafkaService.isAlive())
                call.respondText("Application is alive!", status = HttpStatusCode.OK)
            else
                call.respondText("Kafka er nede", status = HttpStatusCode.InternalServerError)
        }

        get("/is-ready") {
            if (kafkaService.isReady())
                call.respondText("Application is ready!", status = HttpStatusCode.OK)
            else
                call.respondText("Kafka er ikke ready()", status = HttpStatusCode.InternalServerError)
        }
    }
}
