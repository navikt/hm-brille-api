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
import java.util.Random

private val log = KotlinLogging.logger {}

fun Route.integrasjonApi(vilkårsvurderingService: VilkårsvurderingService) {
    route("/integrasjon") {
        post("/vilkarsvurdering") {
            data class Request(
                val fnrBarn: String,
                val brilleseddel: Brilleseddel,
                val bestillingsdato: LocalDate,
            )

            data class Response(
                val resultat: Resultat,
                val sats: SatsType,
                val satsBeløp: BigDecimal,
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
                call.respond(
                    Response(
                        resultat = vilkarsvurdering.utfall,
                        sats = sats,
                        satsBeløp = sats.beløp.toBigDecimal(),
                    )
                )
            } catch (e: Exception) {
                log.error(e) { "Feil i vilkårsvurdering" }
                call.respond(HttpStatusCode.InternalServerError, "Feil i vilkårsvurdering")
            }
        }

        post("/krav") {
            data class Request(
                val fnrBarn: String,
                val brilleseddel: Brilleseddel,
                val brillepris: BigDecimal,
                val bestillingsdato: LocalDate,
                val bestillingsreferanse: String,
                val virksomhetOrgnr: String,
                val ansvarligOptikersFnr: String,
            )

            data class Response(
                val resultat: Resultat,
                val sats: SatsType,
                val satsBeløp: BigDecimal,
                val navReferanse: Int,
            )

            try {
                val req = call.receive<Request>()

                // Kjør vilkårsvurdering
                val vilkarsvurdering = withTilgangContext(call) {
                    vilkårsvurderingService.vurderVilkår(
                        req.fnrBarn,
                        req.brilleseddel,
                        req.bestillingsdato
                    )
                }

                // Kalkuler sats
                val sats = SatsKalkulator(req.brilleseddel).kalkuler()

                // Opprett vedtak hvis vilkårsvurderingen har positivt svar
                // FIXME: db transaction hvor vedtak opprettes

                // Svar ut spørringen
                call.respond(
                    Response(
                        resultat = vilkarsvurdering.utfall,
                        sats = sats,
                        satsBeløp = sats.beløp.toBigDecimal(),
                        navReferanse = Random().nextInt(1000, 100000), // FIXME
                    )
                )
            } catch (e: Exception) {
                log.error(e) { "Feil i vilkårsvurdering" }
                call.respond(HttpStatusCode.InternalServerError, "Feil i vilkårsvurdering")
            }
        }
    }
}
