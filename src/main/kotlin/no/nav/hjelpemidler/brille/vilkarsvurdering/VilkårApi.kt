package no.nav.hjelpemidler.brille.vilkarsvurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.sats.SatsKalkulator
import no.nav.hjelpemidler.brille.sats.SatsType

private val sikkerLog = KotlinLogging.logger("tjenestekall")

private val log = KotlinLogging.logger { }
fun Route.vilkårApi(vilkårsvurderingService: VilkårsvurderingService, auditService: AuditService) {
    post("/vilkarsgrunnlag") {
        try {
            val vilkårsgrunnlag = call.receive<VilkårsgrunnlagDto>()
            auditService.lagreOppslag(
                fnrInnlogget = call.extractFnr(),
                fnrOppslag = vilkårsgrunnlag.fnrBarn,
                oppslagBeskrivelse = "[POST] /vilkarsgrunnlag - Sjekk om barn og bestilling oppfyller vilkår for støtte"
            )
            val vilkarsvurdering = vilkårsvurderingService.vurderVilkår(vilkårsgrunnlag)
            val sats = when (vilkarsvurdering.utfall) {
                Resultat.JA -> SatsKalkulator(vilkårsgrunnlag.brilleseddel).kalkuler()
                else -> SatsType.INGEN
            }

            if (vilkarsvurdering.utfall != Resultat.JA) {
                sikkerLog.info {
                    "Vilkårsvurderingen ga negativt resultat:\n${vilkarsvurdering.toJson()}"
                }
            }

            val beløp = minOf(sats.beløp.toBigDecimal(), vilkårsgrunnlag.brillepris)

            call.respond(
                VilkårsvurderingDto(
                    resultat = vilkarsvurdering.utfall,
                    sats = sats,
                    satsBeskrivelse = sats.beskrivelse,
                    satsBeløp = sats.beløp,
                    beløp = beløp
                )
            )
        } catch (e: Exception) {
            log.error(e) { "Feil i vilkårsvurdering" }
            call.respond(HttpStatusCode.InternalServerError, "Feil i vilkårsvurdering")
        }
    }
}
