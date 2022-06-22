package no.nav.hjelpemidler.brille.pdl.service

import io.ktor.server.routing.get
import mu.KotlinLogging

private val LOG = KotlinLogging.logger {}

/* fun Route.pdlRoutes(
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
                // TODO: hjelpemiddelSoknadProbe.formidlerIkkeFunnetIPdl()
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
                    // TODO: hjelpemiddelSoknadProbe.validerNavnFeilet()
                }

                call.respond(ValiderNavnDto(navneValideringOk))
            } catch (e: Exception) {
                // TODO: hjelpemiddelSoknadProbe.brukerIkkeFunnetIPdl()
                call.respond(ValiderNavnDto(false))
            }
        }
    }
}

private data class ValiderNavnDto(val etternavnMatcher: Boolean) */
