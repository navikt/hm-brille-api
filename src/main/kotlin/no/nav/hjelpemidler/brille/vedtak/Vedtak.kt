package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.vilkarsvurdering.Vilkårsvurdering
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class Vedtak<T>(
    val id: Int = -1,
    val fnrBruker: String,
    val fnrInnsender: String,
    val orgnr: String,
    val bestillingsdato: LocalDate,
    val brillepris: BigDecimal,
    val bestillingsreferanse: String,
    val vilkårsvurdering: Vilkårsvurdering<T>,
    val status: String = "INNVILGET",
    val opprettet: LocalDateTime = LocalDateTime.now(),
)

data class EksisterendeVedtak(
    val id: Int,
    val fnrBruker: String,
    val bestillingsdato: LocalDate,
    val status: String,
    val opprettet: LocalDateTime,
)
