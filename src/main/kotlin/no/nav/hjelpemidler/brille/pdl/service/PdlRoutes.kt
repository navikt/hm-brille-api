package no.nav.hjelpemidler.brille.pdl.service

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.exceptions.PdlRequestFailedException
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.exceptions.PersonNotFoundInPdl
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.extractFnr
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.metrics.HjelpemiddelSoknadProbe
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.pdl.model.BrukerDetaljerRequest
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.pdl.model.ValiderNavnRequest
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.utils.sammenlignEtternavn

private val LOG = KotlinLogging.logger {}

fun Route.pdlRoutes(
    pdlService: PdlService,
    brukerdetaljerService: BrukerdetaljerService,
    hjelpemiddelSoknadProbe: HjelpemiddelSoknadProbe,
) {
    route("/pdl/userinfo") {

        get {
            try {
                val fnr = call.extractFnr()
                call.respond(pdlService.hentPerson(fnr))
            } catch (personNotFoundInPdl: PersonNotFoundInPdl) {
                hjelpemiddelSoknadProbe.formidlerIkkeFunnetIPdl()
                throw personNotFoundInPdl
            } catch (e: Exception) {
                LOG.error("Feil i kall mot PDL")
                throw PdlRequestFailedException()
            }
        }

        post("/brukerdetaljer-v2") {
            val fnrFormidler = call.extractFnr()
            val brukerDetaljerRequest = call.receive<BrukerDetaljerRequest>()
            val fnrBruker = brukerDetaljerRequest.fnr

            call.respond(brukerdetaljerService.hentBrukerDetaljer(fnrBruker, fnrFormidler))
        }

        post("/valider-navn") {
            val validerNavnRequest = call.receive<ValiderNavnRequest>()

            try {
                val person = pdlService.hentPerson(validerNavnRequest.fnr)
                val navneValideringOk = sammenlignEtternavn(validerNavnRequest.etternavn, person.etternavn)

                if (!navneValideringOk) {
                    hjelpemiddelSoknadProbe.validerNavnFeilet()
                }

                call.respond(ValiderNavnDto(navneValideringOk))
            } catch (e: Exception) {
                hjelpemiddelSoknadProbe.brukerIkkeFunnetIPdl()
                call.respond(ValiderNavnDto(false))
            }
        }
    }
}

private data class ValiderNavnDto(val etternavnMatcher: Boolean)
