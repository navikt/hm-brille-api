package no.nav.hjelpemidler.brille.enhetsregisteret

import com.fasterxml.jackson.annotation.JsonProperty

data class Organisasjonsenhet(
    @JsonProperty("organisasjonsnummer")
    val orgnr: String,
    val overordnetEnhet: String?,
    val navn: String,
    val forretningsadresse: Postadresse?, // orgenhet bruker forretningsadresse
    val beliggenhetsadresse: Postadresse?, // underenhet bruker beliggenhetsadresse
    val naeringskode1: Næringskode,
    val naeringskode2: Næringskode?,
    val naeringskode3: Næringskode?,
)

data class Postadresse(
    val postnummer: String,
    val poststed: String,
    val adresse: List<String>,
)

data class Næringskode(
    val beskrivelse: String,
    val kode: String,
)
