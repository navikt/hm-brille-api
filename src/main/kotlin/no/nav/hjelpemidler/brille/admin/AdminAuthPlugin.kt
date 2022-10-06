package no.nav.hjelpemidler.brille.admin

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.AuthenticationRouteSelector
import io.ktor.server.routing.Route
import io.ktor.util.decodeBase64String
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.jsonMapper

private val log = KotlinLogging.logger { }

fun Route.authenticateAdminUser(
    build: Route.() -> Unit,
): Route {
    val authenticatedRoute = createChild(AuthenticationRouteSelector(listOf("adminAuthPlugin")))
    authenticatedRoute.install(AdminAuthPlugin)
    authenticatedRoute.build()
    return authenticatedRoute
}

val AdminAuthPlugin = createRouteScopedPlugin(
    name = "AdminAuthPlugin",
    createConfiguration = ::AdminAuthPluginConfiguration,
) {
    data class Payload(
        val preferred_username: String?,
        val name: String?,
    )

    fun decodePayload(call: ApplicationCall): Payload {
        val authHeader = call.request.headers["Authorization"] ?: ""
        log.info("Auth header: $authHeader")

        val payloadEncoded = authHeader.split(".").let {
            if (it.count() != 3) "" else it[1]
        }
        log.info("Payload encoded: $payloadEncoded")

        val payloadRaw = payloadEncoded.decodeBase64String()
        log.info("Payload raw: $payloadRaw")

        val payload = jsonMapper.readValue<Payload>(payloadRaw)
        log.info("Payload: $payload")

        return payload
    }

    fun sjekkErAdmin(call: ApplicationCall): Boolean {
        val payload = decodePayload(call)
        return !payload.preferred_username.isNullOrEmpty()
    }

    on(AuthenticationChecked) { call ->
        if (!sjekkErAdmin(call)) {
            log.info("IKKE ADMIN, stopp oss her!")
        }

        /*val fnrOptiker = runCatching { call.extractFnr() }.getOrElse {
            throw AdminAuthException(HttpStatusCode.BadRequest, "finner ikke optikers fnr i token")
        }

        if (!sjekkErAdmin()) {
            throw AdminAuthException(
                HttpStatusCode.Forbidden,
                "innlogget bruker er ikke en admin"
            )
        }*/
    }
}

class AdminAuthException(val status: HttpStatusCode, message: String = "", cause: Throwable? = null) :
    RuntimeException(message, cause)

class AdminAuthPluginConfiguration
