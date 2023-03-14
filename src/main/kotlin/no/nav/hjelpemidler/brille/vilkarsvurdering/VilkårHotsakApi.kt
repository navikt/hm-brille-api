package no.nav.hjelpemidler.brille.vilkarsvurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.sats.SatsKalkulator
import no.nav.hjelpemidler.brille.tilgang.withTilgangContext

private val log = KotlinLogging.logger { }
fun Route.vilkårHotsakApi(
    vilkårsvurderingService: VilkårsvurderingService,
) {
    post("/ad/vilkarsgrunnlag") {
        try {
            val vilkårsgrunnlagInput = call.receive<VilkårsgrunnlagAdDto>()
            val vilkarsvurdering = withTilgangContext(call) {
                vilkårsvurderingService.vurderVilkår(
                    vilkårsgrunnlagInput.fnrBarn,
                    vilkårsgrunnlagInput.brilleseddel,
                    vilkårsgrunnlagInput.bestillingsdato
                )
            }
            val sats = SatsKalkulator(vilkårsgrunnlagInput.brilleseddel).kalkuler()

            val beløp = minOf(sats.beløp.toBigDecimal(), vilkårsgrunnlagInput.brillepris)

            call.respond(
                VilkårsvurderingHotsakDto(
                    resultat = vilkarsvurdering.utfall,
                    sats = sats,
                    satsBeskrivelse = sats.beskrivelse,
                    satsBeløp = sats.beløp,
                    beløp = beløp,
                    vilkårsgrunnlag = jsonMapper.valueToTree(vilkarsvurdering),
                    evaluering = vilkarsvurdering.evaluering
                )
            )
        } catch (e: Exception) {
            log.error(e) { "Feil i vilkårsvurdering" }
            call.respond(HttpStatusCode.InternalServerError, "Feil i vilkårsvurdering")
        }
    }
}
