package no.nav.hjelpemidler.brille.pdl.service

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
            throw RuntimeException("Fant ikke person i PDL $feilmeldinger.")
        } else {
            throw RuntimeException(feilmeldinger)
        }
    } else if (pdlRespons.harDiskresjonskode()) {
        throw RuntimeException("Person not accessible in pdl")
    }
}
