package no.nav.hjelpemidler.brille.vilkarsvurdering

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.hotsak.HotsakClient
import no.nav.hjelpemidler.brille.hotsak.HotsakVedtak
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.configuration.GcpEnvironment
import java.time.LocalDate

private val log = KotlinLogging.logger {}

class VilkårsvurderingService(
    private val databaseContext: DatabaseContext,
    private val pdlClient: PdlClient,
    private val hotsakClient: HotsakClient,
    private val medlemskapBarn: MedlemskapBarn,
    private val dagensDatoFactory: () -> LocalDate = LocalDate::now,
) {
    suspend fun vurderVilkår(
        fnrBarn: String,
        brilleseddel: Brilleseddel,
        bestillingsdato: LocalDate,
    ): Vilkårsvurdering<Vilkårsgrunnlag> {
        val vedtakHotsak = hotsakClient.hentEksisterendeVedtak(fnrBarn, bestillingsdato)
        return vurderVilkår(
            fnrBarn = fnrBarn,
            brilleseddel = brilleseddel,
            bestillingsdato = bestillingsdato,
            mottaksdato = dagensDatoFactory(),
            vedtakHotsak = vedtakHotsak,
        )
    }

    suspend fun vurderVilkår(
        fnrBarn: String,
        brilleseddel: Brilleseddel,
        bestillingsdato: LocalDate,
        mottaksdato: LocalDate,
        vedtakHotsak: List<HotsakVedtak> = emptyList(),
    ): Vilkårsvurdering<Vilkårsgrunnlag> {
        val vedtakBarn = transaction(databaseContext) { ctx -> ctx.vedtakStore.hentVedtakForBarn(fnrBarn) }
        val pdlOppslagBarn = pdlClient.hentPerson(fnrBarn)
        val medlemskapResultat = medlemskapBarn.sjekkMedlemskapBarn(fnrBarn, bestillingsdato)
        val vilkårsgrunnlag = Vilkårsgrunnlag(
            vedtakBarn = vedtakBarn + vedtakHotsak,
            pdlOppslagBarn = pdlOppslagBarn,
            medlemskapResultat = medlemskapResultat,
            brilleseddel = brilleseddel,
            bestillingsdato = bestillingsdato,
            mottaksdato = mottaksdato,
            dagensDato = dagensDatoFactory(),
        )

        val vilkårsvurdering = Vilkårsvurdering(
            grunnlag = vilkårsgrunnlag,
            evaluering = Vilkårene.Brille.evaluer(vilkårsgrunnlag),
        )

        if (Environment.current == GcpEnvironment.DEV) {
            log.info { "Resultat av vilkårsvurdering: ${vilkårsvurdering.toJson()}" }
        }

        return vilkårsvurdering
    }
}
