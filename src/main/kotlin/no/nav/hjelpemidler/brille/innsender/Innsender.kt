package no.nav.hjelpemidler.brille.innsender

import java.time.LocalDateTime

data class Innsender(
    val fnrInnsender: String,
    val godtatt: Boolean,
    val opprettet: LocalDateTime = LocalDateTime.now(),
)
