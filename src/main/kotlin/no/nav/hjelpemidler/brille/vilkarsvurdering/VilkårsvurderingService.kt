package no.nav.hjelpemidler.brille.vilkarsvurdering

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.hotsak.HotsakClient
import no.nav.hjelpemidler.brille.medlemskap.MedlemskapBarn
import no.nav.hjelpemidler.brille.nare.spesifikasjon.Spesifikasjon
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
    private val dagensDatoFactory: () -> LocalDate = { LocalDate.now() },
) {
    suspend fun vurderVilkår(
        fnrBarn: String,
        brilleseddel: Brilleseddel,
        bestillingsdato: LocalDate,
    ): Vilkårsvurdering<Vilkårsgrunnlag> {
        val eksisterendeVedtakDatoHotsak = hotsakClient.hentEksisterendeVedtakDato(fnrBarn, bestillingsdato)
        return vurderVilkår(fnrBarn, brilleseddel, bestillingsdato, eksisterendeVedtakDatoHotsak)
    }

    suspend fun vurderVilkår(
        fnrBarn: String,
        brilleseddel: Brilleseddel,
        bestillingsdato: LocalDate,
        eksisterendeVedtakDatoHotsak: LocalDate?,
    ): Vilkårsvurdering<Vilkårsgrunnlag> {
        val vedtakBarn =
            transaction(databaseContext) { ctx -> ctx.vedtakStore.hentVedtakForBarn(fnrBarn) }
        val pdlOppslagBarn = pdlClient.hentPerson(fnrBarn)
        val medlemskapResultat =
            medlemskapBarn.sjekkMedlemskapBarn(fnrBarn, bestillingsdato)
        val vilkårsgrunnlag = Vilkårsgrunnlag(
            vedtakBarn = vedtakBarn,
            eksisterendeVedtakDatoHotsak = eksisterendeVedtakDatoHotsak,
            pdlOppslagBarn = pdlOppslagBarn,
            medlemskapResultat = medlemskapResultat,
            brilleseddel = brilleseddel,
            bestillingsdato = bestillingsdato,
            dagensDato = dagensDatoFactory(),
        )

        val vilkårsvurdering = vurderVilkår(vilkårsgrunnlag, Vilkårene.Brille)

        if (Environment.current == GcpEnvironment.DEV) {
            log.info { "Resultat av vilkårsvurdering: ${vilkårsvurdering.toJson()}" }
        }
        return vilkårsvurdering
    }

    fun <T> vurderVilkår(grunnlag: T, spesifikasjon: Spesifikasjon<T>): Vilkårsvurdering<T> {
        return Vilkårsvurdering(grunnlag, spesifikasjon.evaluer(grunnlag))
    }
}
