package no.nav.hjelpemidler.brille.pdl

import java.time.LocalDate

data class PersonDetaljerDto(
    val fnr: String,
    val fornavn: String,
    val etternavn: String,
    val adresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val alder: Int?, // todo -> fjern?
    val fodselsdato: LocalDate?,
    val kommunenummer: String?,
)
