package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagDto

data class KravDto(
    val vilkårsgrunnlag: VilkårsgrunnlagDto,
    val bestillingsreferanse: String,
    val orgAdresse: String,
    val orgNavn: String,
)
