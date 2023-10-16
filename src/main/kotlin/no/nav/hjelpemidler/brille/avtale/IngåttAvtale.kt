package no.nav.hjelpemidler.brille.avtale

import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import java.time.LocalDateTime

data class IngåttAvtale(
    val orgnr: String,
    val navn: String,
    val aktiv: Boolean,
    val kontonr: String? = null,
    val epost: String? = null,
    val avtaleversjon: String? = null,
    val bruksvilkår: Boolean? = false,
    val utvidetAvtaleOpprettet: LocalDateTime? = null,
    val opprettet: LocalDateTime? = null,
    val oppdatert: LocalDateTime? = null,
) {
    constructor(virksomhet: Virksomhet, navn: String) : this(
        orgnr = virksomhet.orgnr,
        navn = navn,
        aktiv = virksomhet.aktiv,
        kontonr = virksomhet.kontonr,
        epost = virksomhet.epost,
        avtaleversjon = virksomhet.avtaleversjon,
        bruksvilkår = virksomhet.bruksvilkår,
        utvidetAvtaleOpprettet = virksomhet.bruksvilkårGodtattDato,
        opprettet = virksomhet.opprettet,
        oppdatert = virksomhet.oppdatert,
    )
}
