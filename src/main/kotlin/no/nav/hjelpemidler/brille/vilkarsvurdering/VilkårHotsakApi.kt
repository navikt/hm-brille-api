package no.nav.hjelpemidler.brille.vilkarsvurdering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.joarkref.JoarkrefService
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.sats.SatsKalkulator
import no.nav.hjelpemidler.brille.tilgang.withTilgangContext
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.VedtakService
import java.math.BigDecimal
import java.time.LocalDateTime

private val log = KotlinLogging.logger { }
fun Route.vilkårHotsakApi(
    vilkårsvurderingService: VilkårsvurderingService,
    vedtakService: VedtakService,
    joarkrefService: JoarkrefService,
) {
    get("/ad/vilkarsspesifikasjon") {
        call.respond(Vilkårene.Brille)
    }
    post("/ad/vilkarsgrunnlag") {
        try {
            val vilkårsgrunnlag = call.receive<VilkårsgrunnlagAdDto>()
            val brilleseddel = vilkårsgrunnlag.brilleseddel ?: Brilleseddel.INGEN
            val bestillingsdato = vilkårsgrunnlag.bestillingsdato ?: MANGLENDE_DATO
            val brillepris = vilkårsgrunnlag.brillepris ?: BigDecimal.ZERO

            val vilkårsvurdering = withTilgangContext(call) {
                vilkårsvurderingService.vurderVilkår(
                    vilkårsgrunnlag.fnrBarn,
                    brilleseddel,
                    bestillingsdato,
                    vilkårsgrunnlag.eksisterendeBestillingsdato,
                )
            }

            val sats = SatsKalkulator(brilleseddel).kalkuler()

            val beløp = minOf(sats.beløp(bestillingsdato).toBigDecimal(), brillepris)

            call.respond(
                VilkårsvurderingHotsakDto(
                    resultat = vilkårsvurdering.utfall,
                    sats = sats,
                    satsBeskrivelse = sats.beskrivelse,
                    satsBeløp = sats.beløp(bestillingsdato),
                    beløp = beløp,
                    vilkårsgrunnlag = jsonMapper.valueToTree(vilkårsvurdering),
                    evaluering = vilkårsvurdering.evaluering,
                ),
            )
        } catch (e: Exception) {
            log.error(e) { "Feil i vilkårsvurdering" }
            call.respond(HttpStatusCode.InternalServerError, "Feil i vilkårsvurdering")
        }
    }

    post("/ad/krav-for-bruker") {
        data class Request(
            val fnr: String,
        )

        data class Response(
            val vedtakId: Long,
            val behandlingsresultat: Behandlingsresultat,
            val journalpostId: String,
            val dokumentIder: List<String>,
            val opprettet: LocalDateTime,
        )

        val req = call.receive<Request>()

        val vedtak = vedtakService.hentVedtakForBruker(req.fnr).map {
            val dokument = joarkrefService.hentJoarkRef(it.id)
            Response(
                vedtakId = it.id,
                behandlingsresultat = Behandlingsresultat.valueOf(it.behandlingsresultat),
                journalpostId = dokument?.journalpostId?.toString() ?: "",
                dokumentIder = dokument?.dokumentIder ?: listOf(),
                opprettet = it.opprettet,
            )
        }

        call.respond(vedtak)
    }
}
