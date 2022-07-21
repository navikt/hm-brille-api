package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.fodselsdato
import no.nav.hjelpemidler.brille.pdl.PdlOppslag
import no.nav.hjelpemidler.brille.pdl.Person
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.vedtak.EksisterendeVedtak
import java.time.LocalDate

data class Vilkårsgrunnlag(
    val vedtakForBarn: List<EksisterendeVedtak>,
    val pdlOppslagBarn: PdlOppslag<Person?>,
    val medlemskapResultat: MedlemskapResultat,
    val brilleseddel: Brilleseddel,
    val bestillingsdato: LocalDate,
    val dagensDato: LocalDate,
    val datoOrdningenStartet: LocalDate = DATO_ORDNINGEN_STARTET,
    val seksMånederSiden: LocalDate = dagensDato.minusMonths(6),
    val minsteSfære: Double = 1.0,
    val minsteSylinder: Double = 1.0,
) {
    val fodselsdatoBruker: LocalDate? get() = pdlOppslagBarn.data?.fodselsdato()
}
