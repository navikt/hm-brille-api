package no.nav.hjelpemidler.brille.pdl.service

import jdk.internal.platform.Container.metrics
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.metrics.HjelpemiddelSoknadProbe
import no.nav.hjelpemidler.brille.pdl.client.PdlClient
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.allowlists.AllowlistService
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.enhetsregisteret.service.EnhetsregisteretService
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.exceptions.PersonNotAccessibleInPdl
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.metrics.HjelpemiddelSoknadProbe
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.pdl.client.PdlClient
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.pdl.model.Error
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.pdl.model.PdlPersonResponse
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.pdl.model.PersonDetaljerResponse
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.pdl.model.kommunenummer
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.pdl.model.toPersonDto
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.pdl.service.validerPdlOppslag
import no.nav.hjelpemidler.hjelpemidlerdigitalSoknadapi.postnummer.PostnummerService

private val LOG = KotlinLogging.logger {}
private val isDev = System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp"

class BrukerdetaljerService(
    private val pdlClient: PdlClient,
    private val enhetsregisteretService: EnhetsregisteretService,
    private val allowlistService: AllowlistService,
    private val hjelpemiddelSoknadProbe: HjelpemiddelSoknadProbe,
    private val postnummerService: PostnummerService
) {

    suspend fun hentBrukerDetaljer(fnrBruker: String, fnrFormidler: String): PersonDetaljerResponse {
        if (!isDev && fnrFormidler == fnrBruker) {
            LOG.info { "Formidler slo opp på sitt eget fnr" }
            return PersonDetaljerResponse(null, Error.OPPSLAG_PAA_FORMIDLERS_FNR)
        }

        return try {
            val pdlResponse = pdlClient.hentPersonDetaljer(fnrBruker)
            validerPdlOppslag(pdlResponse)

            if (formidlerHarSammeGeografiskTilknytningSomBruker(pdlResponse, fnrFormidler)) {
                val personDto = pdlResponse.toPersonDto(fnrBruker) { postnummerService.hentPoststed(it) }
                PersonDetaljerResponse(personDto, null)
            } else {
                PersonDetaljerResponse(null, Error.ULIK_GEOGRAFISK_TILKNYTNING)
            }
        } catch (e: Exception) {
            LOG.error("Klarte ikke å hente person fra PDL", e)
            throw e
        }
    }

    private suspend fun formidlerHarSammeGeografiskTilknytningSomBruker(pdlResponse: PdlPersonResponse, fnrFormidler: String): Boolean {
        val formidlersOrganisasjoner = allowlistService.hentGodkjenteOrgnummer(fnrFormidler)
        LOG.info("Formidlers organisasjoner i brukerdetaljer $formidlersOrganisasjoner")
        val formidlersGeografiskeTilknytninger =
            enhetsregisteretService.geografiskTilknytningFor(formidlersOrganisasjoner)
        val brukersGeografiskeTilknytning = pdlResponse.kommunenummer()
            ?: throw PersonNotAccessibleInPdl("Bruker mangler geografisk tilknytning i PDL")

        val geografiskTilknytningMatcher = formidlersGeografiskeTilknytninger.contains(brukersGeografiskeTilknytning)
        hjelpemiddelSoknadProbe.pdlOppslag(geografiskTilknytningMatcher)

        if (!geografiskTilknytningMatcher) {
            LOG.info("Kan ikke slå opp bruker i PDL da formidler har annen geografisk tilknyting ($formidlersGeografiskeTilknytninger) enn bruker ($brukersGeografiskeTilknytning)")
        }

        return geografiskTilknytningMatcher
    }
}
