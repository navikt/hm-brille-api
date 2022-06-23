package no.nav.hjelpemidler.brille

import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.uri
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.exceptions.SjekkOptikerPluginUnauthorizedException
import no.nav.hjelpemidler.brille.syfohelsenettproxy.SyfohelsenettproxyClient

private val LOG = KotlinLogging.logger {}

val SjekkOptikerPlugin = createApplicationPlugin(
    name = "SjekkOptikerPlugin",
    createConfiguration = ::SjekkOptikerPluginConfiguration,
) {
    val syfohelsenettproxyClient = this.pluginConfig.syfohelsenettproxyClient!!
    onCall { call ->
        LOG.info("DEBUG: DEBUG: $call, ${call.request.headers}, ${call.request.headers["x-optiker-fnr"]}")

        // Slipp igjennom kall for liveness/readiness/metrics
        if (call.request.uri.startsWith("/internal")) return@onCall

        val fnrOptiker = call.request.headers["x-optiker-fnr"] ?: runCatching { call.extractFnr() }.getOrElse {
            throw SjekkOptikerPluginUnauthorizedException("finner ikke fnr i token")
        }

        val behandler = runCatching { syfohelsenettproxyClient.hentBehandler(fnrOptiker) }.getOrElse {
            throw SjekkOptikerPluginUnauthorizedException("Kunne ikke hente data fra syfohelsenettproxyClient: $it")
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

        LOG.info("DEBUG: DEBUG: 2: erOptiker=$erOptiker, helsepersonellkategoriVerdi=$helsepersonellkategoriVerdi, fnrOptiker=$fnrOptiker, behandler=$behandler")

        if (!erOptiker) {
            throw SjekkOptikerPluginUnauthorizedException("innlogget bruker er ikke registrert som optiker i HPR")
        }

        LOG.info("DEBUG: DEBUG: 3: erOptikker!")
    }
}

class SjekkOptikerPluginConfiguration {
    var syfohelsenettproxyClient: SyfohelsenettproxyClient? = null
}
