package no.nav.hjelpemidler.brille.vilkarsvurdering

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.nare.spesifikasjon.Spesifikasjon
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import java.time.LocalDate

private val log = KotlinLogging.logger {}

class VilkårsvurderingService(
    private val databaseContext: DatabaseContext,
    private val pdlClient: PdlClient,
    private val medlemskapBarn: MedlemskapBarn,
    private val dagensDatoFactory: () -> LocalDate = { LocalDate.now() }
) {
    suspend fun vurderVilkår(
        fnrBarn: String,
        brilleseddel: Brilleseddel,
        bestillingsdato: LocalDate
    ): Vilkårsvurdering<Vilkårsgrunnlag> {
        val vedtakBarn =
            transaction(databaseContext) { ctx -> ctx.vedtakStore.hentVedtakForBarn(fnrBarn) }
        val pdlOppslagBarn = pdlClient.hentPerson(fnrBarn)
        val medlemskapResultat =
            medlemskapBarn.sjekkMedlemskapBarn(fnrBarn, bestillingsdato)
        val vilkårsgrunnlag = Vilkårsgrunnlag(
            vedtakBarn = vedtakBarn,
            pdlOppslagBarn = pdlOppslagBarn,
            medlemskapResultat = medlemskapResultat,
            brilleseddel = brilleseddel,
            bestillingsdato = bestillingsdato,
            dagensDato = dagensDatoFactory()
        )
        val vilkårsvurdering = vurderVilkår(vilkårsgrunnlag, Vilkårene.Brille)
        if (!Configuration.prod) {
            log.info {
                "Resultat av vilkårsvurdering: ${vilkårsvurdering.toJson()}"
            }
        }
        return vilkårsvurdering
    }

    fun <T> vurderVilkår(grunnlag: T, spesifikasjon: Spesifikasjon<T>): Vilkårsvurdering<T> {
        return Vilkårsvurdering(grunnlag, spesifikasjon.evaluer(grunnlag))
    }
}
