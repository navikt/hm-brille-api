package no.nav.hjelpemidler.brille.aareg.model

data class UtenlandsoppholdDto(
    var periode: PeriodeDto? = null,
    var rapporteringsperiode: String? = null,
    var land: String? = null
)
