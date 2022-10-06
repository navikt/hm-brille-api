package no.nav.hjelpemidler.brille.admin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakConflictException
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakInternalServerErrorException
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakNotAuthorizedException
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakNotFoundException
import no.nav.hjelpemidler.brille.vedtak.SlettVedtakService
import no.nav.hjelpemidler.brille.vedtak.SlettetAvType
import java.time.LocalDateTime

fun Route.adminApi(
    adminService: AdminService,
    slettVedtakService: SlettVedtakService,
    enhetsregisteretService: EnhetsregisteretService
) {
    authenticateAdminUser {
        route("/admin") {
            post("/sok") {
                data class Request(
                    val query: String,
                )

                val query = call.receive<Request>().query.trim()

                if (!Regex("\\d+").matches(query)) {
                    return@post call.respond(HttpStatusCode.BadRequest, """{"error": "Ugyldig format: bare tall"}""")
                }

                if (query.count() == 11) {
                    // Fnr
                    val krav = adminService.hentVedtakListe(query)
                    call.respond(HttpStatusCode.OK, krav)
                } else {
                    // vedtakId oppslag
                    val vedtak = adminService.hentVedtak(query.toLong())
                        ?: return@post call.respond(HttpStatusCode.NotFound, """{"error": "Fant ikke krav"}""")

                    call.respond(HttpStatusCode.OK, """{"vedtakId": "${vedtak.vedtakId}"}""")
                }
            }

            get("/detaljer/{vedtakId}") {
                val vedtakId = call.parameters["vedtakId"]!!.toLong()
                val vedtak = adminService.hentVedtak(vedtakId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, """{"error": "Fant ikke krav"}""")

                data class Response(
                    val vedtakId: Long,
                    val orgnr: String,
                    val orgNavn: String,
                    val barnsNavn: String,
                    val bestillingsreferanse: String,
                    val opprettet: LocalDateTime,
                    val utbetalt: LocalDateTime?,
                    val slettet: LocalDateTime?,
                    val slettetAvType: SlettetAvType?,
                )

                call.respond(
                    Response(
                        vedtakId = vedtak.vedtakId,
                        orgnr = vedtak.orgnr,
                        orgNavn = enhetsregisteretService.hentOrganisasjonsenhet(vedtak.orgnr)?.navn ?: "<Ukjent>",
                        barnsNavn = vedtak.barnsNavn,
                        bestillingsreferanse = vedtak.bestillingsreferanse,
                        opprettet = vedtak.opprettet,
                        utbetalt = vedtak.utbetalingsdato,
                        slettet = vedtak.slettet,
                        slettetAvType = vedtak.slettetAvType,
                    )
                )
            }

            delete("/detaljer/{vedtakId}") {
                val fnrInnsender = call.extractFnr()
                val vedtakId = call.parameters["vedtakId"]!!.toLong()
                val vedtak = adminService.hentVedtak(vedtakId)
                    ?: return@delete call.respond(HttpStatusCode.NotFound, """{"error": "Fant ikke krav"}""")

                try {
                    slettVedtakService.slettVedtak(fnrInnsender, vedtak.vedtakId, true)
                    call.respond(HttpStatusCode.OK, "{}")
                } catch (e: SlettVedtakNotAuthorizedException) {
                    call.respond(HttpStatusCode.Unauthorized, e.message!!)
                } catch (e: SlettVedtakConflictException) {
                    call.respond(HttpStatusCode.Conflict, e.message!!)
                } catch (e: SlettVedtakInternalServerErrorException) {
                    call.respond(HttpStatusCode.InternalServerError, e.message!!)
                } catch (e: SlettVedtakNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, e.message!!)
                }
            }
        }
    }
}
