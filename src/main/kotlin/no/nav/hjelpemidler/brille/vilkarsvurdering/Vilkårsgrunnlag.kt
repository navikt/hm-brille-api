package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.pdl.PdlOppslag
import no.nav.hjelpemidler.brille.pdl.fodselsdato
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.sats.Diopter
import no.nav.hjelpemidler.brille.vedtak.EksisterendeVedtak
import java.time.LocalDate

data class Vilkårsgrunnlag(
    val vedtakForBruker: List<EksisterendeVedtak>,
    val pdlOppslagBruker: PdlOppslag,
    val medlemskapResultat: MedlemskapResultat,
    val brilleseddel: Brilleseddel,
    val bestillingsdato: LocalDate,
    val dagensDato: LocalDate,
    val datoOrdningenStartet: LocalDate = DATO_ORDNINGEN_STARTET,
    val seksMånederSiden: LocalDate = dagensDato.minusMonths(6),
    val minsteSfære: Diopter = Diopter.ONE,
    val minsteSylinder: Diopter = Diopter.ONE,
) {
    val fodselsdatoBruker: LocalDate? get() = pdlOppslagBruker.pdlPersonResponse.data?.fodselsdato()
}
