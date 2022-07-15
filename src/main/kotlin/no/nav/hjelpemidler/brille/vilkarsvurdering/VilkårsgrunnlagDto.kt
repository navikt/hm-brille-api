package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.sats.BrilleseddelDto
import java.math.BigDecimal
import java.time.LocalDate

data class VilkårsgrunnlagDto(
    val orgnr: String,
    val fnrBarn: String,
    val brilleseddel: BrilleseddelDto,
    val bestillingsdato: LocalDate,
    val brillepris: BigDecimal,
)
