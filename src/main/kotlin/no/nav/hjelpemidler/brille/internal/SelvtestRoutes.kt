package no.nav.hjelpemidler.brille.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.selvtestRoutes() {
    route("/internal") {
        get("/isAlive") {
            try {
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
