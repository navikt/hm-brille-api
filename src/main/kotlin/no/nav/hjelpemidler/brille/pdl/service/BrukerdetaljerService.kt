package no.nav.hjelpemidler.brille.pdl.service

import mu.KotlinLogging

private val LOG = KotlinLogging.logger {}
private val isDev = System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp"

/* class BrukerdetaljerService(
    private val pdlClient: PdlClient,
    private val hjelpemiddelSoknadProbe: HjelpemiddelSoknadProbe,
    // private val postnummerService: PostnummerService
    // private val enhetsregisteretService: EnhetsregisteretService,
    // private val allowlistService: AllowlistService,
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
} */
