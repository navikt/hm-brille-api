package no.nav.hjelpemidler.brille.pdl

import no.nav.hjelpemidler.brille.exceptions.PdlRequestFailedException
import no.nav.hjelpemidler.brille.exceptions.PersonNotAccessibleInPdl
import no.nav.hjelpemidler.brille.exceptions.PersonNotFoundInPdl

fun validerPdlOppslag(pdlRespons: PdlPersonResponse) {
    if (pdlRespons.harFeilmeldinger()) {
        val feilmeldinger = pdlRespons.feilmeldinger()
        if (pdlRespons.feilType() == PdlFeiltype.IKKE_FUNNET) {
            throw PersonNotFoundInPdl("Fant ikke person i PDL $feilmeldinger.")
        } else {
            throw PdlRequestFailedException(feilmeldinger)
        }
    } else if (pdlRespons.harDiskresjonskode()) {
        throw PersonNotAccessibleInPdl()
    }
}
