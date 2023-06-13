package no.nav.hjelpemidler.brille.vilkarsvurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.admin.AdminService
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.sats.SatsKalkulator
import no.nav.hjelpemidler.brille.sats.SatsType

private val sikkerLog = KotlinLogging.logger("tjenestekall")

private val log = KotlinLogging.logger { }
fun Route.vilkårApi(
    vilkårsvurderingService: VilkårsvurderingService,
    adminService: AdminService,
    auditService: AuditService,
    kafkaService: KafkaService
) {
    post("/vilkarsgrunnlag") {
        // FIXME: TEST - Kristoffer
        return@post call.respond(HttpStatusCode.InternalServerError, "Nope")
        try {
            val vilkårsgrunnlag = call.receive<VilkårsgrunnlagDto>()
            auditService.lagreOppslag(
                fnrInnlogget = call.extractFnr(),
                fnrOppslag = vilkårsgrunnlag.fnrBarn,
                oppslagBeskrivelse = "[POST] /vilkarsgrunnlag - Sjekk om barn og bestilling oppfyller vilkår for støtte"
            )
            val vilkarsvurdering = vilkårsvurderingService.vurderVilkår(
                vilkårsgrunnlag.fnrBarn,
                vilkårsgrunnlag.brilleseddel,
                vilkårsgrunnlag.bestillingsdato,
                true,
            )
            val sats = when (vilkarsvurdering.utfall) {
                Resultat.JA -> SatsKalkulator(vilkårsgrunnlag.brilleseddel).kalkuler()
                else -> SatsType.INGEN
            }

            if (vilkarsvurdering.utfall != Resultat.JA) {
                sikkerLog.info {
                    "Vilkårsvurderingen ga negativt resultat:\n${vilkarsvurdering.toJson()}"
                }
                kafkaService.vilkårIkkeOppfylt(vilkårsgrunnlag, vilkarsvurdering)
                // Lagre avvisningsårsaker, hvem og hvorfor. Brukes i brille-admin.
                val årsaker = vilkarsvurdering.evaluering.barn
                    .filter { vilkar -> vilkar.resultat != Resultat.JA }
                    .map { vilkar -> vilkar.begrunnelse }
                adminService.lagreAvvisning(vilkårsgrunnlag.fnrBarn, call.extractFnr(), vilkårsgrunnlag.orgnr, årsaker)
            }

            val beløp = minOf(sats.beløp(vilkårsgrunnlag.bestillingsdato).toBigDecimal(), vilkårsgrunnlag.brillepris)

            val refInnsendersTidligereKrav =
                if (!vilkarsvurdering.harResultatJaForVilkår("HarIkkeVedtakIKalenderåret v1")) {
                    vilkarsvurdering.grunnlag.vedtakBarn.firstOrNull {
                        it.fnrInnsender == call.extractFnr() && it.bestillingsdato.year == vilkårsgrunnlag.bestillingsdato.year
                    }
                        ?.bestillingsreferanse
                } else {
                    null
                }

            call.respond(
                VilkårsvurderingDto(
                    resultat = vilkarsvurdering.utfall,
                    sats = sats,
                    satsBeskrivelse = sats.beskrivelse,
                    satsBeløp = sats.beløp(vilkårsgrunnlag.bestillingsdato),
                    beløp = beløp,
                    kravFraFørFraInnsender = refInnsendersTidligereKrav
                )
            )
        } catch (e: Exception) {
            log.error(e) { "Feil i vilkårsvurdering" }
            call.respond(HttpStatusCode.InternalServerError, "Feil i vilkårsvurdering")
        }
    }
}
