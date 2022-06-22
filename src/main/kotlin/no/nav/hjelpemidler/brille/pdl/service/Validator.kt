package no.nav.hjelpemidler.brille.pdl.service

import no.nav.hjelpemidler.brille.exceptions.PdlRequestFailedException
import no.nav.hjelpemidler.brille.exceptions.PersonNotAccessibleInPdl
import no.nav.hjelpemidler.brille.exceptions.PersonNotFoundInPdl
import no.nav.hjelpemidler.brille.pdl.model.PdlFeiltype
import no.nav.hjelpemidler.brille.pdl.model.PdlPersonResponse
import no.nav.hjelpemidler.brille.pdl.model.feilType
import no.nav.hjelpemidler.brille.pdl.model.feilmeldinger
import no.nav.hjelpemidler.brille.pdl.model.harDiskresjonskode
import no.nav.hjelpemidler.brille.pdl.model.harFeilmeldinger

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
