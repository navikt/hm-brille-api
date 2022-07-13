package no.nav.hjelpemidler.brille.model

data class TidligereBrukteOrganisasjonerForOptiker(
    val sistBrukteOrganisasjon: Organisasjon?,
    val tidligereBrukteOrganisasjoner: List<Organisasjon>,
)

data class Organisasjon(
    val orgnr: String,
    val navn: String,
    val forretningsadresse: String?,
    val beliggenhetsadresse: String?,
)
