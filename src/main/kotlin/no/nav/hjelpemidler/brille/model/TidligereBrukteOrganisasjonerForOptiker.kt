package no.nav.hjelpemidler.brille.model

data class TidligereBrukteOrganisasjonerForOptiker(
    val sistBrukteOrganisasjon: Organisasjon?,
    val tidligereBrukteOrganisasjoner: List<Organisasjon>,
)

data class Organisasjon(val orgnummer: String, val navn: String, val forretningadresse: String?, val beliggenhetsadresse: String?)
