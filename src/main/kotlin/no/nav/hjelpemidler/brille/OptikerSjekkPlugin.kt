package no.nav.hjelpemidler.brille

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.AuthenticationRouteSelector
import io.ktor.server.routing.Route
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.brille.exceptions.SjekkOptikerPluginException
import no.nav.hjelpemidler.brille.redis.RedisClient
import no.nav.hjelpemidler.brille.syfohelsenettproxy.SyfohelsenettproxyClient

fun Route.authenticateOptiker(
    syfohelsenettproxyClient: SyfohelsenettproxyClient,
    redisClient: RedisClient,
    build: Route.() -> Unit,
): Route {
    val authenticatedRoute = createChild(AuthenticationRouteSelector(listOf("sjekkOptikerPlugin")))
    authenticatedRoute.install(SjekkOptikerPlugin) {
        this.syfohelsenettproxyClient = syfohelsenettproxyClient
        this.redisClient = redisClient
    }
    authenticatedRoute.build()
    return authenticatedRoute
}

val SjekkOptikerPlugin = createRouteScopedPlugin(
    name = "SjekkOptikerPlugin",
    createConfiguration = ::SjekkOptikerPluginConfiguration,
) {
    val syfohelsenettproxyClient = this.pluginConfig.syfohelsenettproxyClient!!
    val redisClient: RedisClient = this.pluginConfig.redisClient!!

    fun sjekkErOptiker(fnrOptiker: String): Boolean {
        val cachedErOptiker = redisClient.erOptiker(fnrOptiker)

        if (cachedErOptiker != null) {
            return cachedErOptiker
        }

        val behandler =
            runCatching { runBlocking { syfohelsenettproxyClient.hentBehandler(fnrOptiker) } }.getOrElse {
                throw SjekkOptikerPluginException(
                    HttpStatusCode.InternalServerError,
                    "Kunne ikke hente data fra syfohelsenettproxyClient: $it",
                    it
                )
            }

        // OP = Optiker (ref.: https://volven.no/produkt.asp?open_f=true&id=476764&catID=3&subID=8&subCat=61&oid=9060)
        val erOptiker = behandler.godkjenninger.any {
            it.helsepersonellkategori?.aktiv == true && it.helsepersonellkategori.verdi == "OP"
        }

        redisClient.setErOptiker(fnrOptiker, erOptiker)

        return erOptiker
    }

    on(AuthenticationChecked) { call ->
        val fnrOptiker = runCatching { call.extractFnr() }.getOrElse {
            throw SjekkOptikerPluginException(HttpStatusCode.BadRequest, "finner ikke optikers fnr i token")
        }

        if (!sjekkErOptiker(fnrOptiker)) {
            throw SjekkOptikerPluginException(
                HttpStatusCode.Unauthorized,
                "innlogget bruker er ikke registrert som optiker i HPR"
            )
        }
    }
}

class SjekkOptikerPluginConfiguration {
    var syfohelsenettproxyClient: SyfohelsenettproxyClient? = null
    var redisClient: RedisClient? = null
}
