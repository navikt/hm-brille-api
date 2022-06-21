package no.nav.hjelpemidler.brille.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.apache.kafka.clients.consumer.KafkaConsumer

fun Route.selvtestRoutes(
    kafkaConsumer: KafkaConsumer<String, String>,
) {
    route("/internal") {
        get("/isAlive") {
            try {
                if (kafkaConsumer.listTopics().isEmpty()) {
                    throw RuntimeException()
                }
                call.respondText("Application is alive!", status = HttpStatusCode.OK)
            } catch (e: Exception) {
                call.respondText("Noe er galt", status = HttpStatusCode.InternalServerError)
            }
        }

        get("/isReady") {
            call.respondText("Application is ready!", status = HttpStatusCode.OK)
        }
    }
}
