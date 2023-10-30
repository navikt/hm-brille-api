package no.nav.hjelpemidler.brille.vilkarsvurdering

import no.nav.hjelpemidler.brille.medlemskap.MedlemskapResultat
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.fødselsdato
import no.nav.hjelpemidler.brille.pdl.PdlOppslag
import no.nav.hjelpemidler.brille.pdl.Person
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.tid.alderPå
import no.nav.hjelpemidler.nare.evaluering.Resultat
import java.time.Instant
import java.time.LocalDate

data class Vilkårsgrunnlag(
    /**
     * Komplett liste med vedtak barnet fra tidligere, inkludert Hotsak.
     */
    val vedtakBarn: List<Vedtak>,
    val pdlOppslagBarn: PdlOppslag<Person?>,
    val medlemskapResultat: MedlemskapResultat,
    val brilleseddel: Brilleseddel,
    val bestillingsdato: LocalDate,

    /**
     * Dato for når vi mottok kravet eller søknaden.
     */
    val mottaksdato: LocalDate,
    val dagensDato: LocalDate,
) {
    val seksMånederSiden: LocalDate = mottaksdato.minusMonths(6)
    val tidligsteMuligeBestillingsdato: LocalDate = seksMånederSiden
    val minsteSfære: Double = 1.0
    val minsteSylinder: Double = 1.0

    val barnetsFødselsdato: LocalDate? get() = pdlOppslagBarn.data?.fødselsdato()
    val barnetsAlderPåBestillingsdato: Int? get() = barnetsFødselsdato alderPå bestillingsdato
    val barnetsAlderPåMottaksdato: Int? get() = barnetsFødselsdato alderPå mottaksdato
    val barnetsAlderIDag: Int? get() = barnetsFødselsdato alderPå dagensDato

    fun senesteVedtak(): Vedtak? = vedtakBarn.maxByOrNull { it.vedtaksdato }

    interface Vedtak {
        val vedtaksdato: Instant
        val bestillingsdato: LocalDate
    }
}

fun Int?.erUnder18(): Boolean = this != null && this < 18

fun Vilkårsvurdering<Vilkårsgrunnlag>.harResultatJaForVilkår(identifikator: String): Boolean =
    this.evaluering.barn.find { it.identifikator.startsWith(identifikator) }!!.resultat == Resultat.JA
