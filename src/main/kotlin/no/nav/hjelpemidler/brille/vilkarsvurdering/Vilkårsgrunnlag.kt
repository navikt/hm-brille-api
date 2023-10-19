package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.fødselsdato
import no.nav.hjelpemidler.brille.pdl.PdlOppslag
import no.nav.hjelpemidler.brille.pdl.Person
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.vedtak.EksisterendeVedtak
import no.nav.hjelpemidler.nare.evaluering.Resultat
import java.time.LocalDate

data class Vilkårsgrunnlag(
    val vedtakBarn: List<EksisterendeVedtak>,
    val eksisterendeVedtakDatoHotsak: LocalDate?,
    val pdlOppslagBarn: PdlOppslag<Person?>,
    val medlemskapResultat: MedlemskapResultat,
    val brilleseddel: Brilleseddel,
    val bestillingsdato: LocalDate,

    // standardverdier
    val dagensDato: LocalDate,
    val datoOrdningenStartet: LocalDate = DATO_ORDNINGEN_STARTET,
    val seksMånederSiden: LocalDate = dagensDato.minusMonths(6),
    val minsteSfære: Double = 1.0,
    val minsteSylinder: Double = 1.0,
) {
    val barnetsFødselsdato: LocalDate? get() = pdlOppslagBarn.data?.fødselsdato()
    val barnetsAlderPåBestillingsdato: Int? get() = barnetsFødselsdato alderPå bestillingsdato
    val barnetsAlderIDag: Int? get() = barnetsFødselsdato alderPå dagensDato
}

/**
 * Null-verdi for dato.
 */
val MANGLENDE_DATO: LocalDate = LocalDate.MAX

fun LocalDate?.mangler(): Boolean = this == null || this === MANGLENDE_DATO

infix fun LocalDate?.alderPå(dato: LocalDate): Int? = this?.until(dato)?.years

fun Int?.erUnder18(): Boolean = this != null && this < 18

fun Vilkårsvurdering<Vilkårsgrunnlag>.harResultatJaForVilkår(identifikator: String): Boolean =
    this.evaluering.barn.find { it.identifikator.startsWith(identifikator) }!!.resultat == Resultat.JA
