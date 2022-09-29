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
import java.time.LocalDateTime

fun Route.adminApi(
    adminService: AdminService,
    slettVedtakService: SlettVedtakService,
    enhetsregisteretService: EnhetsregisteretService
) {
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
                val saker = adminService.hentVedtakListe(query)
                call.respond(HttpStatusCode.OK, saker)
            } else {
                // Saknr oppslag
                val vedtak = adminService.hentVedtak(query.toLong())
                    ?: return@post call.respond(HttpStatusCode.NotFound, """{"error": "Fant ikke saken"}""")

                call.respond(HttpStatusCode.OK, """{"sakId": "${vedtak.sakId}"}""")
            }
        }

        get("/detaljer/{sakId}") {
            val sakId = call.parameters["sakId"]!!.toLong()
            val vedtak = adminService.hentVedtak(sakId)
                ?: return@get call.respond(HttpStatusCode.NotFound, """{"error": "Fant ikke saken"}""")

            data class Response(
                val id: Long,
                val orgnr: String,
                val orgNavn: String,
                val opprettet: LocalDateTime,
                val utbetalt: LocalDateTime?,
                val slettet: LocalDateTime?,
                val slettetAvType: String?,
            )

            call.respond(
                Response(
                    id = vedtak.sakId,
                    orgnr = vedtak.orgnr,
                    orgNavn = enhetsregisteretService.hentOrganisasjonsenhet(vedtak.orgnr)?.navn ?: "<Ukjent>",
                    opprettet = vedtak.opprettet,
                    utbetalt = vedtak.utbetalingsdato,
                    slettet = vedtak.slettet,
                    slettetAvType = vedtak.slettetAvType,
                )
            )
        }

        delete("/detaljer/{sakId}") {
            val fnrInnsender = call.extractFnr()
            val sakId = call.parameters["sakId"]!!.toLong()
            val vedtak = adminService.hentVedtak(sakId)
                ?: return@delete call.respond(HttpStatusCode.NotFound, """{"error": "Fant ikke saken"}""")

            try {
                slettVedtakService.slettVedtak(fnrInnsender, vedtak.sakId, true)
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
