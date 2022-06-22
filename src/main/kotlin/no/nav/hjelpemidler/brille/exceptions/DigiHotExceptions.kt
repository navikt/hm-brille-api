package no.nav.hjelpemidler.brille.exceptions

import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import mu.KotlinLogging

private val LOG = KotlinLogging.logger {}

class PersonNotFoundInPdl(message: String) : RuntimeException(message)

class PersonNotAccessibleInPdl(message: String = "") : RuntimeException(message)

class PdlRequestFailedException(message: String = "") : RuntimeException("Request to PDL Failed $message")

fun Application.configureStatusPages() {
    install(StatusPages) {
        // PDL exceptions
        exception<PersonNotFoundInPdl> { call, _ ->
            call.respond(HttpStatusCode.NotFound)
        }
        exception<PersonNotAccessibleInPdl> { call, _ ->
            call.respond(HttpStatusCode.Forbidden)
        }
        exception<PdlRequestFailedException> { call, _ ->
            call.respond(HttpStatusCode.InternalServerError)
        }

        // Others
        exception<MissingKotlinParameterException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest)
        }
        exception<Exception> { call, cause ->
            when (cause) {
                is BadRequestException -> call.respond(HttpStatusCode.BadRequest)
                else -> {
                    LOG.error(
                        "Unhandled exception. Pls fix slik at den fanges og håndteres med riktig statuskode.",
                        cause
                    )
                    call.respondText(
                        "Noe gikk galt! Feilen har blitt logget og vil bli undersøkt.",
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }
        }
    }
}
