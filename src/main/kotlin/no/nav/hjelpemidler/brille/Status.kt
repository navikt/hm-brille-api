package no.nav.hjelpemidler.brille

import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<SjekkOptikerPluginException> { call, e ->
            // TODO: Fjern når vi ikke trenger den lengre.
            log.warn(e) { "Exception fra middleware med status: ${e.status.description}" }
            call.respond(e.status)
        }
        exception<MissingKotlinParameterException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest)
        }
        exception<Exception> { call, cause ->
            when (cause) {
                is BadRequestException -> call.respond(HttpStatusCode.BadRequest)
                else -> {
                    log.error(cause) {
                        "Noe gikk galt!"
                    }
                    call.respondText(
                        "Noe gikk galt! Feilen har blitt logget og vil bli undersøkt.",
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }
        }
    }
}
