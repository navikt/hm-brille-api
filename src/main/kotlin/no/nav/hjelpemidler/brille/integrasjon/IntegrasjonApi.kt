package no.nav.hjelpemidler.brille.integrasjon

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.sats.SatsKalkulator
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.tilgang.withTilgangContext
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import java.math.BigDecimal
import java.time.LocalDate

private val log = KotlinLogging.logger {}

fun Route.integrasjonApi(vilkårsvurderingService: VilkårsvurderingService) {
    route("/integrasjon") {
        post("/vilkarsvurdering") {
            data class Request(
                val fnrBarn: String,
                val brilleseddel: Brilleseddel,
                val bestillingsdato: LocalDate,
                val brillepris: BigDecimal
            )

            data class Response(
                val resultat: Resultat,
                val sats: SatsType,
                val satsBeskrivelse: String,
                val satsBeløp: Int,
                val beløp: BigDecimal,
            )

            try {
                val vilkårsgrunnlagInput = call.receive<Request>()
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
                    Response(
                        resultat = vilkarsvurdering.utfall,
                        sats = sats,
                        satsBeskrivelse = sats.beskrivelse,
                        satsBeløp = sats.beløp,
                        beløp = beløp,
                    )
                )
            } catch (e: Exception) {
                log.error(e) { "Feil i vilkårsvurdering" }
                call.respond(HttpStatusCode.InternalServerError, "Feil i vilkårsvurdering")
            }
        }
    }
}
