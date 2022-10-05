package no.nav.hjelpemidler.brille.aareg.model

data class AnsettelsesperiodeDto(
    val periode: PeriodeDto? = null,
    val varslingskode: String? = null,
    var sluttaarsak: String? = null
)
