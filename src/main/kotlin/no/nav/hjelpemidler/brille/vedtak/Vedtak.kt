package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class Vedtak<T>(
    val id: Long = -1,
    val fnrBarn: String,
    val fnrInnsender: String,
    val orgnr: String,
    val bestillingsdato: LocalDate,
    val brillepris: BigDecimal,
    val bestillingsreferanse: String,
    val vilkårsvurdering: Vilkårsvurdering<T>,
    val behandlingsresultat: Behandlingsresultat,
    val sats: SatsType,
    val satsBeløp: BigDecimal,
    val satsBeskrivelse: String,
    val beløp: BigDecimal,
    val opprettet: LocalDateTime = LocalDateTime.now(),
)

data class EksisterendeVedtak(
    val id: Long,
    val fnrBarn: String,
    val bestillingsdato: LocalDate,
    val behandlingsresultat: String,
    val opprettet: LocalDateTime,
)

enum class Behandlingsresultat {
    INNVILGET
}
