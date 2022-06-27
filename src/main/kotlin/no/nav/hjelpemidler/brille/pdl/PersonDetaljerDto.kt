package no.nav.hjelpemidler.brille.pdl

data class PersonDetaljerDto(
    val fnr: String,
    val fornavn: String,
    val etternavn: String,
    val adresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val alder: Int?,
    val kommunenummer: String?,
)
