package no.nav.hjelpemidler.brille.oversikt

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.alder
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.navn
import no.nav.hjelpemidler.brille.pdl.PdlService
import no.nav.hjelpemidler.brille.vedtak.VedtakStore

private val log = KotlinLogging.logger {}

fun Route.oversiktApi(
    vedtakStore: VedtakStore,
    enhetsregisteretService: EnhetsregisteretService,
    pdlService: PdlService,
) {
    route("/oversikt") {
        // Detealjene for et gitt krav sendt inn av optiker
        get("/{vedtakId}") {
            val vedtakId = (call.parameters["vedtakId"] ?: error("Mangler vedtakId i url")).toLong()
            val fnrInnsender = call.extractFnr()
            val vedtak = vedtakStore.hentVedtakForOptiker(fnrInnsender, vedtakId)?.let { vedtak ->
                pdlService.hentPerson(vedtak.barnsFnr)?.let {
                    vedtak.barnsNavn = it.navn()
                    vedtak.barnsAlder = it.alder() ?: -1
                }
                vedtak.orgnavn = enhetsregisteretService.hentOrganisasjonsenhet(vedtak.orgnr)?.navn ?: "Ukjent"
                vedtak
            }
            if (vedtak == null) {
                call.respond(HttpStatusCode.NotFound, """{"error":"not found"}""")
                return@get
            }
            call.respond(vedtak)
        }

        // Alle krav sendt inn av innlogget optiker
        get("/") {
            val fnrInnsender = call.extractFnr()
            call.respond(
                vedtakStore.hentAlleVedtakForOptiker(fnrInnsender)
                    .map { vedtak ->
                        pdlService.hentPerson(vedtak.barnsFnr)?.let {
                            vedtak.barnsNavn = it.navn()
                            vedtak.barnsAlder = it.alder() ?: -1
                        }
                        vedtak.orgnavn = enhetsregisteretService.hentOrganisasjonsenhet(vedtak.orgnr)?.navn ?: "Ukjent"
                        vedtak
                    }
            )
        }
    }
}
