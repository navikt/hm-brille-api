package no.nav.hjelpemidler.brille.avtale

import java.time.LocalDateTime

data class BruksvilkårGodtattDto(
    val orgnr: String,
    val avtaletype: String,
    val opprettet: LocalDateTime,
) {

    companion object {
        fun fromBruksvilkårGodtatt(bruksvilkårGodtatt: BruksvilkårGodtatt) =
            BruksvilkårGodtattDto(
                bruksvilkårGodtatt.orgnr,
                BRUKSVILKÅRTYPE.fromInt(bruksvilkårGodtatt.bruksvilkårDefinisjonId).name,
                bruksvilkårGodtatt.opprettet,
            )
    }
}
