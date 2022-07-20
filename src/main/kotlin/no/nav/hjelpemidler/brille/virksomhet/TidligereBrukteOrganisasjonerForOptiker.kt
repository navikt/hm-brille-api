package no.nav.hjelpemidler.brille.virksomhet

data class TidligereBrukteOrganisasjonerForOptiker(
    val sistBrukteOrganisasjon: Organisasjon?,
    val tidligereBrukteOrganisasjoner: List<Organisasjon>,
)

data class Organisasjon(
    val orgnr: String,
    val aktiv: Boolean,
    val navn: String,
    val adresse: String?,
)
