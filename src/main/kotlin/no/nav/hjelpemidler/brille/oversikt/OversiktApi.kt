package no.nav.hjelpemidler.brille.oversikt

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.alder
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.navn
import no.nav.hjelpemidler.brille.pdl.Person
import no.nav.hjelpemidler.brille.vedtak.OversiktVedtak
import no.nav.hjelpemidler.brille.vedtak.VedtakStore

private val log = KotlinLogging.logger {}

fun Route.oversiktApi(
    vedtakStore: VedtakStore,
    enhetsregisteretService: EnhetsregisteretService,
) {
    route("/oversikt") {
        // Detealjene for et gitt krav sendt inn av optiker
        get("/{vedtakId}") {
            val vedtakId = (call.parameters["vedtakId"] ?: error("Mangler vedtakId i url")).toLong()
            val fnrInnsender = call.extractFnr()
            val vedtak = vedtakStore.hentVedtakForOptiker(fnrInnsender, vedtakId)?.let { vedtak ->
                val person: Person = jsonMapper.readValue(vedtak.second)
                vedtak.first.barnsNavn = person.navn()
                vedtak.first.barnsAlder = person.alder() ?: -1
                vedtak.first.orgnavn = enhetsregisteretService.hentOrganisasjonsenhet(vedtak.first.orgnr)?.navn ?: "Ukjent"
                vedtak.first
            }
            if (vedtak == null) {
                call.respond(HttpStatusCode.NotFound, """{"error":"not found"}""")
                return@get
            }
            call.respond(vedtak)
        }

        // Alle krav sendt inn av innlogget optiker
        get("/") {
            val itemsPerPage = 10
            val page = (call.request.queryParameters["page"] ?: "1").toInt()
            val indexRange = (page - 1) * itemsPerPage until page * itemsPerPage

            val fnrInnsender = call.extractFnr()
            val alleVedtak = vedtakStore.hentAlleVedtakForOptiker(fnrInnsender)
            val totaltAntallVedtak = alleVedtak.count()
            val filtrerteVedtak = alleVedtak
                .mapIndexedNotNull { idx, vedtak ->
                    if (!indexRange.contains(idx)) {
                        null
                    } else {
                        val person: Person = jsonMapper.readValue(vedtak.second)
                        vedtak.first.barnsNavn = person.navn()
                        vedtak.first.barnsAlder = person.alder() ?: -1
                        // TODO: Lag en lokal map utenfor denne loopen som cache'er oppslag mot enhetsregisteret, så
                        //  slipper vi 10 kall mot enhetsregisteret (redis) på hver request når én ofte er nok
                        vedtak.first.orgnavn = enhetsregisteretService.hentOrganisasjonsenhet(vedtak.first.orgnr)?.navn ?: "Ukjent"
                        vedtak.first
                    }
                }

            data class Response(
                val numberOfPages: Int,
                val itemsPerPage: Int,
                val totalItems: Int,
                val items: List<OversiktVedtak>,
            )

            call.respond(
                Response(
                    numberOfPages = Math.ceil(totaltAntallVedtak.toDouble() / itemsPerPage.toDouble()).toInt(),
                    itemsPerPage = itemsPerPage,
                    totalItems = totaltAntallVedtak,
                    items = filtrerteVedtak,
                )
            )
        }
    }
}
