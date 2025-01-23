package no.nav.hjelpemidler.brille

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import no.nav.hjelpemidler.brille.avtale.AvtaleManglerTilgangException

private val log = KotlinLogging.logger {}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<SjekkOptikerPluginException> { call, e ->
            log.warn(e) { "Exception fra middleware med status: ${e.status.description}" }
            call.respond(e.status)
        }
        exception<AvtaleManglerTilgangException> { call, e ->
            log.warn { e.message }
            call.respond(HttpStatusCode.Forbidden, e.message ?: "")
        }
        exception<MismatchedInputException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest)
        }
        exception<PersonFinnesIkkeIHPRException> { call, _ ->
            call.respond(HttpStatusCode.Unauthorized)
        }
        exception<StatusCodeException> { call, e ->
            log.warn { e.message }
            call.respond(e.status)
        }
        exception<Exception> { call, cause ->
            when (cause) {
                is BadRequestException -> call.respond(HttpStatusCode.BadRequest)
                else -> {
                    log.error(cause) {
                        "Noe gikk galt! method: ${call.request.httpMethod}, url: ${call.request.uri}"
                    }
                    call.respondText(
                        "Noe gikk galt! Feilen har blitt logget og vil bli undersøkt.",
                        status = HttpStatusCode.InternalServerError,
                    )
                }
            }
        }
    }
}

class StatusCodeException(val status: HttpStatusCode, message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
