package no.nav.hjelpemidler.brille.vilkarsvurdering

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.hjelpemidler.brille.hotsak.HotsakVedtak
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Grunnlag for vilkårsvurdering gjort i Hotsak.
 */
data class VilkårsgrunnlagHotsakDto(
    val fnrBarn: String,
    val brilleseddel: Brilleseddel?,
    val bestillingsdato: LocalDate?,
    val brillepris: BigDecimal?,
    @Deprecated("Bruk vedtak når hm-saksbehandling går i produksjon")
    val eksisterendeBestillingsdato: LocalDate? = null,
    /**
     * Tidspunkt for når NAV mottok søknaden.
     */
    @JsonAlias("søknadMottatt")
    val mottaksdato: Instant = Instant.now(),
    /**
     * Innvilgede vedtak for barnebriller fra Hotsak.
     */
    val vedtak: List<HotsakVedtak> = emptyList(),
)
