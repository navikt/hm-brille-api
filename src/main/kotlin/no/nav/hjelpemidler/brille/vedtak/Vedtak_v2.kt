package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingResultat
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class Vedtak_v2(
    val id: Int,
    val fnrBruker: String,
    val fnrInnsender: String,
    val orgnr: String,
    val bestillingsdato: LocalDate,
    val brillepris: BigDecimal,
    val bestillingsref: String,
    val vilkarsvurdering: VilkårsvurderingResultat,
    val status: String,
    val opprettet: LocalDateTime,
)
