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

data class OrganisasjonMedBruksvilkår(
    val orgnr: String,
    val aktiv: Boolean,
    val navn: String,
    val adresse: String?,
    val bruksvilkår: Boolean,
)
