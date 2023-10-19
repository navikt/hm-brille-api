package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.sats.Brilleseddel
import java.math.BigDecimal
import java.time.LocalDate

data class VilkårsgrunnlagAdDto(
    val fnrBarn: String,
    val brilleseddel: Brilleseddel?,
    val bestillingsdato: LocalDate?,
    val brillepris: BigDecimal?,
    val eksisterendeBestillingsdato: LocalDate? = null,
)
