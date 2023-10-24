package no.nav.hjelpemidler.brille.avtale

import java.time.LocalDateTime

data class BruksvilkårGodtattDto(
    val orgnr: String,
    val avtaletype: String,
    val opprettet: LocalDateTime,
    val navn: String,
) {

    companion object {
        fun fromBruksvilkårGodtatt(bruksvilkårGodtatt: BruksvilkårGodtatt, navn: String) =
            BruksvilkårGodtattDto(
                bruksvilkårGodtatt.orgnr,
                BRUKSVILKÅRTYPE.fromInt(bruksvilkårGodtatt.bruksvilkårDefinisjonId).name,
                bruksvilkårGodtatt.opprettet,
                navn,
            )
    }
}
