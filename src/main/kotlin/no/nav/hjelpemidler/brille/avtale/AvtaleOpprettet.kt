package no.nav.hjelpemidler.brille.avtale

import java.time.LocalDateTime


data class AvtaleOpprettet(
    val orgnr: String,
    val avtaletype: String,
    val opprettet: LocalDateTime
) {

    companion object {
        fun fromAvtale(avtale: Avtale) =
            AvtaleOpprettet(
                avtale.orgnr,
                AVTALETYPE.fromInt(avtale.avtaleId).name,
                avtale.opprettet
            )
    }

}
