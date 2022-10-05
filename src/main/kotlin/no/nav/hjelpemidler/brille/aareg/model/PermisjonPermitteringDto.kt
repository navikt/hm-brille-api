package no.nav.hjelpemidler.brille.aareg.model

data class PermisjonPermitteringDto(
    var periode: PeriodeDto? = null,
    var type: String? = null,
    var prosent: String? = null
)
