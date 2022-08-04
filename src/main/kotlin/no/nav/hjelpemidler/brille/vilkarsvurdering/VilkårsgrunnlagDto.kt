package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.sats.Brilleseddel
import java.math.BigDecimal
import java.time.LocalDate

data class VilkårsgrunnlagDto(
    val orgnr: String,
    val fnrBarn: String,
    val brilleseddel: Brilleseddel,
    val bestillingsdato: LocalDate,
    val brillepris: BigDecimal,
    val extras: VilkårsgrunnlagExtrasDto // kun til statistikk o.l.
)

data class VilkårsgrunnlagExtrasDto(
    val orgNavn: String,
    val bestillingsreferanse: String
)
