package no.nav.hjelpemidler.brille.pdl

import java.time.LocalDate

data class PersonDto(
    val fnr: String,
    val fornavn: String,
    val etternavn: String,
    val alder: Int?,
    val fodselsdato: LocalDate?,
)
