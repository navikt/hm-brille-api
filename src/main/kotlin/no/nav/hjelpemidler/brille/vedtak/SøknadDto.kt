package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagDto

data class SøknadDto(
    val vilkårsgrunnlag: VilkårsgrunnlagDto,
    val bestillingsreferanse: String,
    val brukersNavn: String,
    val orgAdresse: String,
    val orgNavn: String,
)
