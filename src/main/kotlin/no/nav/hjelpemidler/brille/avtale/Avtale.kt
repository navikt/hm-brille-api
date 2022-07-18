package no.nav.hjelpemidler.brille.avtale

import java.time.LocalDateTime

data class Avtale(
    val orgnr: String,
    val navn: String,
    val aktiv: Boolean,
    val kontonr: String? = null,
    val avtaleversjon: String? = null,
    val opprettet: LocalDateTime? = null,
    val oppdatert: LocalDateTime? = null,
)
