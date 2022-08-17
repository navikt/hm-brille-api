package no.nav.hjelpemidler.brille.innsyn

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.vedtak.VedtakStore

private val log = KotlinLogging.logger {}

fun Route.innsynApi(
    vedtakStore: VedtakStore,
) {
    route("/innsyn") {
        // Detealjene for et gitt krav sendt inn av optiker
        get("/{vedtakId}") {
            val vedtakId = (call.parameters["vedtakId"] ?: error("Mangler vedtakId i url")).toLong()
            val fnrInnsender = call.extractFnr()
            val vedtak = vedtakStore.hentVedtak(fnrInnsender, vedtakId)
            if (vedtak == null) {
                call.respond(HttpStatusCode.NotFound, """{"error":"not found"}""")
                return@get
            }
            call.respond(vedtak)
        }

        // Alle krav sendt inn av innlogget optiker
        get("/") {
            val fnrInnsender = call.extractFnr()
            call.respond(vedtakStore.hentAlleVedtak(fnrInnsender))
        }
    }
}
