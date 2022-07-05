package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.sats.BeregnSatsDto
import java.math.BigDecimal
import java.time.LocalDate

data class VilkårsgrunnlagDto(
    val orgnr: String,
    val fnrBruker: String,
    val beregnSats: BeregnSatsDto,
    val bestillingsdato: LocalDate,
    val brillepris: BigDecimal,
)
