package no.nav.hjelpemidler.brille.vedtak

import no.nav.hjelpemidler.brille.sats.SatsType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class VedtakDto(
    val id: Long,
    val orgnr: String,
    val bestillingsdato: LocalDate,
    val brillepris: BigDecimal,
    val bestillingsreferanse: String,
    val behandlingsresultat: Behandlingsresultat,
    val sats: SatsType,
    val satsBeløp: Int,
    val satsBeskrivelse: String,
    val beløp: BigDecimal,
    val opprettet: LocalDateTime,
)

fun <T> Vedtak<T>.toDto(): VedtakDto = VedtakDto(
    id = id,
    orgnr = orgnr,
    bestillingsdato = bestillingsdato,
    brillepris = brillepris,
    bestillingsreferanse = bestillingsreferanse,
    behandlingsresultat = behandlingsresultat,
    sats = sats,
    satsBeløp = satsBeløp,
    satsBeskrivelse = satsBeskrivelse,
    beløp = beløp,
    opprettet = opprettet,
)
