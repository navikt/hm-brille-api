package no.nav.hjelpemidler.brille.avtale

import java.time.LocalDateTime

data class Avtale(
    val orgnr: String,
    val navn: String,
    val harNavAvtale: Boolean,
    val kontonr: String? = null,
    val avtaleVersjon: String? = null,
    val opprettet: LocalDateTime? = null,
)
