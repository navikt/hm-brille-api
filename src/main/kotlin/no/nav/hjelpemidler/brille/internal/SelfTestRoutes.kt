package no.nav.hjelpemidler.brille.internal

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.hjelpemidler.brille.utbetaling.SendTilUtbetalingScheduler
import no.nav.hjelpemidler.brille.vedtak.VedtakTilUtbetalingScheduler

fun Route.internalRoutes(vedtakTilUtbetalingScheduler: VedtakTilUtbetalingScheduler, sendTilUtbetalingScheduler: SendTilUtbetalingScheduler) {
    route("/internal") {
        get("/is-alive") {
            try {
                call.respondText("Application is alive!", status = HttpStatusCode.OK)
            } catch (e: Exception) {
                call.respondText("Noe er galt", status = HttpStatusCode.InternalServerError)
            }
        }

        get("/is-ready") {
            call.respondText("Application is ready!", status = HttpStatusCode.OK)
        }

        get("/stop-send-til-utbetaling") {
            vedtakTilUtbetalingScheduler.cancel()
            sendTilUtbetalingScheduler.cancel()
            call.respondText("Stopping send til utbetaling schedulers", status = HttpStatusCode.OK)
        }
    }
}
