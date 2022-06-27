package no.nav.hjelpemidler.brille

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.AuthenticationRouteSelector
import io.ktor.server.request.uri
import io.ktor.server.routing.Route
import no.nav.hjelpemidler.brille.exceptions.SjekkOptikerPluginException
import no.nav.hjelpemidler.brille.syfohelsenettproxy.SyfohelsenettproxyClient

fun Route.SjekkOptikerPlugin(syfohelsenettproxyClient: SyfohelsenettproxyClient, build: Route.() -> Unit): Route {
    val authenticatedRoute = createChild(AuthenticationRouteSelector(listOf("sjekkOptikerPlugin")))
    authenticatedRoute.install(SjekkOptikerPluginInternal) {
        this.syfohelsenettproxyClient = syfohelsenettproxyClient
    }
    authenticatedRoute.build()
    return authenticatedRoute
}

val SjekkOptikerPluginInternal = createRouteScopedPlugin(
    name = "SjekkOptikerPlugin",
    createConfiguration = ::SjekkOptikerPluginConfiguration,
) {
    val syfohelsenettproxyClient = this.pluginConfig.syfohelsenettproxyClient!!
    on(AuthenticationChecked) { call ->
        // Slipp igjennom kall for liveness/readiness/metrics
        if (call.request.uri.startsWith("/internal/")) return@on

        val fnrOptiker = runCatching { call.extractFnr() }.getOrElse {
            throw SjekkOptikerPluginException(HttpStatusCode.BadRequest, "finner ikke optikers fnr i token")
        }

        val behandler = runCatching { syfohelsenettproxyClient.hentBehandler(fnrOptiker) }.getOrElse {
            throw SjekkOptikerPluginException(
                HttpStatusCode.InternalServerError,
                "Kunne ikke hente data fra syfohelsenettproxyClient: $it",
                it
            )
        }

        // OP = Optiker (ref.: https://volven.no/produkt.asp?open_f=true&id=476764&catID=3&subID=8&subCat=61&oid=9060)
        val erOptiker = behandler.godkjenninger.any {
            it.helsepersonellkategori?.aktiv == true && (
                it.helsepersonellkategori.verdi
                    ?: ""
                ) == "OP"
        }

        if (!erOptiker) {
            throw SjekkOptikerPluginException(
                HttpStatusCode.Unauthorized,
                "innlogget bruker er ikke registrert som optiker i HPR"
            )
        }
    }
}

class SjekkOptikerPluginConfiguration {
    var syfohelsenettproxyClient: SyfohelsenettproxyClient? = null
}
