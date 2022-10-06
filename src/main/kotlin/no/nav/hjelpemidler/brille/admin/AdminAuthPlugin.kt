package no.nav.hjelpemidler.brille.admin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.AuthenticationRouteSelector
import io.ktor.server.routing.Route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.extractEmail

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
    on(AuthenticationChecked) { call ->
        val email = call.extractEmail()
        if (email.isEmpty()) {
            throw AdminAuthException(
                HttpStatusCode.Forbidden,
                "innlogget bruker er ikke en admin"
            )
        }
    }
}

class AdminAuthException(val status: HttpStatusCode, message: String = "", cause: Throwable? = null) :
    RuntimeException(message, cause)

class AdminAuthPluginConfiguration
