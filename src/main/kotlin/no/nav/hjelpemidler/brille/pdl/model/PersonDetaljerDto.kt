package no.nav.hjelpemidler.brille.pdl.model

import com.fasterxml.jackson.annotation.JsonFormat

data class PersonDetaljerResponse(
    val personDetaljer: PersonDetaljerDto?,
    val feil: Error?
)

data class PersonDetaljerDto(
    val fnr: String,
    val fornavn: String,
    val etternavn: String,
    val adresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val alder: Int?,
    val kommunenummer: String?
)

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class Error(val errorCode: Int, val message: String) {
    UKJENT_FEIL(0, "Ukjent feil"),
    OPPSLAG_PAA_FORMIDLERS_FNR(10, "Formidler kan ikke søke om hjelpemidler til seg selv. Oppslag på eget fnr. er ikke tillatt."),
    ULIK_GEOGRAFISK_TILKNYTNING(20, "Formidler har kan ikke slå opp brukerdetaljer utenfor egen geografisk tilknytning")
}
