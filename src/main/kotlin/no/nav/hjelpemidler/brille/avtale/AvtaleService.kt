package no.nav.hjelpemidler.brille.avtale

import no.nav.hjelpemidler.brille.altinn.AltinnService
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStore

class AvtaleService(private val virksomhetStore: VirksomhetStore, private val altinnService: AltinnService) {
    suspend fun hentVirksomheter(fnr: String): List<AvtaleVirksomhet> {
        val virksomheter = virksomhetStore.hentVirksomheterForInnsender(fnr).associateBy {
            it.orgnr
        }
        return altinnService.hentAvgivereHovedadministrator(fnr).map {
            AvtaleVirksomhet(
                orgnr = it.orgnr,
                navn = it.navn,
                harNavAvtale = virksomheter[it.orgnr]?.harNavAvtale ?: false,
                kontonr = virksomheter[it.orgnr]?.kontonr,
                avtaleVersjon = virksomheter[it.orgnr]?.avtaleVersjon,
            )
        }
    }
}

data class AvtaleVirksomhet(
    val orgnr: String,
    val navn: String,
    val harNavAvtale: Boolean,
    val kontonr: String? = null,
    val avtaleVersjon: String? = null,
)
