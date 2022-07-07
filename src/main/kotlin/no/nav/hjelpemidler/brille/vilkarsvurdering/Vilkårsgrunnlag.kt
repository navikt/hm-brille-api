package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.pdl.PdlOppslag
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.vedtak.EksisterendeVedtak
import java.time.LocalDate

data class Vilkårsgrunnlag(
    val vedtakForBruker: List<EksisterendeVedtak>,
    val pdlOppslagBruker: PdlOppslag,
    val brilleseddel: Brilleseddel,
    val bestillingsdato: LocalDate,
    val dagensDato: LocalDate = LocalDate.now(),
    val datoOrdningenStartet: LocalDate = DATO_ORDNINGEN_STARTET,
    val seksMånederSiden: LocalDate = LocalDate.now().minusMonths(6),
    val medlemskapResultat: MedlemskapResultat,
)
