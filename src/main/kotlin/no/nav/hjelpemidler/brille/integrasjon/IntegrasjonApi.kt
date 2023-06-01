package no.nav.hjelpemidler.brille.integrasjon

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.audit.AuditService
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.enhetsregisteret.Organisasjonsenhet
import no.nav.hjelpemidler.brille.nare.evaluering.Resultat
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.navn
import no.nav.hjelpemidler.brille.pdl.PdlService
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import no.nav.hjelpemidler.brille.sats.SatsKalkulator
import no.nav.hjelpemidler.brille.sats.SatsType
import no.nav.hjelpemidler.brille.syfohelsenettproxy.SyfohelsenettproxyClient
import no.nav.hjelpemidler.brille.tilgang.withTilgangContext
import no.nav.hjelpemidler.brille.utbetaling.UtbetalingService
import no.nav.hjelpemidler.brille.vedtak.Behandlingsresultat
import no.nav.hjelpemidler.brille.vedtak.KravDto
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakConflictException
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakInternalServerErrorException
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakService
import no.nav.hjelpemidler.brille.vedtak.SlettetAvType
import no.nav.hjelpemidler.brille.vedtak.VedtakService
import no.nav.hjelpemidler.brille.vedtak.toDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsgrunnlagExtrasDto
import no.nav.hjelpemidler.brille.vilkarsvurdering.VilkårsvurderingService
import no.nav.hjelpemidler.brille.virksomhet.Organisasjon
import no.nav.hjelpemidler.brille.virksomhet.enhetTilAdresseFor
import java.math.BigDecimal
import java.time.LocalDate

private val log = KotlinLogging.logger {}

fun Route.integrasjonApi(
    vilkårsvurderingService: VilkårsvurderingService,
    vedtakService: VedtakService,
    auditService: AuditService,
    enhetsregisteretService: EnhetsregisteretService,
    pdlService: PdlService,
    databaseContext: DatabaseContext,
    syfohelsenettproxyClient: SyfohelsenettproxyClient,
    utbetalingService: UtbetalingService,
    slettVedtakService: SlettVedtakService,
) {
    route("/integrasjon") {

        post("/sjekk-optiker") {

            data class Request(
                val fnrInnsender: String
            )

            data class Response(
                val erOptiker: Boolean
            )

            val request = call.receive<Request>()

            val behandler = syfohelsenettproxyClient.hentBehandler(request.fnrInnsender)
            val erOptiker = behandler?.godkjenninger?.any {
                it.helsepersonellkategori?.aktiv == true && it.helsepersonellkategori.verdi == "OP"
            } ?: false

            val response = Response(erOptiker = erOptiker)
            call.respond(response)
        }

        get("/virksomhet/{orgnr}") {
            val orgnr =
                call.parameters["orgnr"] ?: error("Mangler orgnr i url")

            val virksomhet =
                transaction(databaseContext) { ctx -> ctx.virksomhetStore.hentVirksomhetForOrganisasjon(orgnr) }
            val harAktivNavAvtale = virksomhet?.aktiv ?: false
            log.info("Søker etter $orgnr har aktiv NavAvtale: $harAktivNavAvtale")
            val enhet = enhetsregisteretService.hentOrganisasjonsenhet(orgnr)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Fant ikke organisasjonsenhet for orgnr: $orgnr")

            val response = Organisasjon(
                orgnr = enhet.orgnr,
                navn = enhet.navn,
                aktiv = harAktivNavAvtale,
                adresse = enhetTilAdresseFor(enhet),
            )

            call.respond(response)
        }

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
                val navReferanse: Long,
            )

            try {
                val req = call.receive<Request>()

                // TODO: hent optikers navn
                val navnInnsender = /*redisClient.optikerNavn(fnrInnsender) ?:*/ "<Ukjent>"

                // Slå opp orgnavn/-adresse fra enhetsregisteret
                val enhet: Organisasjonsenhet = enhetsregisteretService.hentOrganisasjonsenhet(req.virksomhetOrgnr)
                    ?: return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        "Fant ikke organisasjonsenhet for orgnr: ${req.virksomhetOrgnr}",
                    )

                // Slå opp barnets navn i PDL
                val barnPdl = pdlService.hentPerson(req.fnrBarn)
                    ?: return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        "Fant ikke barnet i pdl",
                    )

                // Audit logging
                auditService.lagreOppslag(
                    fnrInnlogget = req.ansvarligOptikersFnr,
                    fnrOppslag = req.fnrBarn,
                    oppslagBeskrivelse = "[POST] /krav - Innsending av krav"
                )

                // Kjør vilkårsvurdering og opprett vedtak
                val vedtak = vedtakService.lagVedtak(
                    req.ansvarligOptikersFnr, navnInnsender, KravDto(
                        vilkårsgrunnlag = VilkårsgrunnlagDto(
                            orgnr = req.virksomhetOrgnr,
                            fnrBarn = req.fnrBarn,
                            brilleseddel = req.brilleseddel,
                            bestillingsdato = req.bestillingsdato,
                            brillepris = req.brillepris,
                            extras = VilkårsgrunnlagExtrasDto(
                                orgNavn = enhet.navn,
                                bestillingsreferanse = req.bestillingsreferanse,
                            ),
                        ),
                        bestillingsreferanse = req.bestillingsreferanse,
                        brukersNavn = barnPdl.navn(),
                        orgAdresse = enhetTilAdresseFor(enhet),
                        orgNavn = enhet.navn,
                    )
                )
                val vedtakDto = vedtak.toDto()

                // Svar ut spørringen
                call.respond(
                    Response(
                        resultat = if (vedtakDto.behandlingsresultat == Behandlingsresultat.INNVILGET) {
                            Resultat.JA
                        } else {
                            Resultat.NEI
                        },
                        sats = vedtakDto.sats,
                        satsBeløp = vedtakDto.beløp,
                        navReferanse = vedtakDto.id,
                    )
                )
            } catch (e: Exception) {
                log.error(e) { "Feil i krav oppretting" }
                call.respond(HttpStatusCode.InternalServerError, "Feil i krav oppretting")
            }
        }

        delete("/krav/{id}") {

            data class Request(
                val fnrInnsender: String,
            )

            val vedtakId = call.parameters["id"]!!.toLong()
            val vedtak = vedtakService.hentVedtak(vedtakId)
                ?: return@delete call.respond(HttpStatusCode.NotFound, """{"error": "Fant ikke krav"}""")

            val req = call.receive<Request>()

            if (req.fnrInnsender != vedtak.fnrInnsender) {
                return@delete call.respond(HttpStatusCode.Unauthorized, """{"error": "Krav kan ikke slettes av deg"}""")
            }

            val utbetaling = utbetalingService.hentUtbetalingForVedtak(vedtakId)
            if (utbetaling != null) {
                return@delete call.respond(
                    HttpStatusCode.Unauthorized,
                    """{"error": "Krav kan ikke slettes fordi utbetaling er påstartet"}"""
                )
            }

            auditService.lagreOppslag(
                fnrInnlogget = req.fnrInnsender,
                fnrOppslag = vedtak.fnrBarn,
                oppslagBeskrivelse = "[DELETE] /krav - Sletting av krav $vedtakId"
            )

            try {
                slettVedtakService.slettVedtak(vedtak.id, req.fnrInnsender, SlettetAvType.INNSENDER)
                call.respond(HttpStatusCode.OK, "{}")
            } catch (e: SlettVedtakConflictException) {
                call.respond(HttpStatusCode.Conflict, e.message!!)
            } catch (e: SlettVedtakInternalServerErrorException) {
                call.respond(HttpStatusCode.InternalServerError, e.message!!)
            }
        }

    }


}
