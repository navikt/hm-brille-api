package no.nav.hjelpemidler.brille

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.uri
import no.nav.hjelpemidler.brille.exceptions.SjekkOptikerPluginException
import no.nav.hjelpemidler.brille.syfohelsenettproxy.SyfohelsenettproxyClient

val SjekkOptikerPlugin = createApplicationPlugin(
    name = "SjekkOptikerPlugin",
    createConfiguration = ::SjekkOptikerPluginConfiguration,
) {
    val syfohelsenettproxyClient = this.pluginConfig.syfohelsenettproxyClient!!
    onCall { call ->
        // Slipp igjennom kall for liveness/readiness/metrics
        if (call.request.uri.startsWith("/internal")) return@onCall

        val fnrOptiker = call.request.headers["x-optiker-fnr"] ?: runCatching { call.extractFnr() }.getOrElse {
            throw SjekkOptikerPluginException(HttpStatusCode.BadRequest, "finner ikke fnr i token")
        }

        val behandler = runCatching { syfohelsenettproxyClient.hentBehandler(fnrOptiker) }.getOrElse {
            throw SjekkOptikerPluginException(
                HttpStatusCode.InternalServerError,
                "Kunne ikke hente data fra syfohelsenettproxyClient: $it"
            )
        }

        // FIXME: Sjekker nå om man er lege hvis fnr kommer fra headeren i stede for idporten-session; dette er bare for testing
        // OP = Optiker (ref.: https://volven.no/produkt.asp?open_f=true&id=476764&catID=3&subID=8&subCat=61&oid=9060)
        val helsepersonellkategoriVerdi = if (call.request.headers["x-optiker-fnr"] == null) "OP" else "LE"
        val erOptiker = behandler.godkjenninger.filter {
            it.helsepersonellkategori?.aktiv == true && (
                    it.helsepersonellkategori.verdi
                        ?: ""
                    ) == helsepersonellkategoriVerdi
        }.isNotEmpty()

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
