package no.nav.hjelpemidler.brille.oversikt

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.enhetsregisteret.EnhetsregisteretService
import no.nav.hjelpemidler.brille.extractFnr

private val log = KotlinLogging.logger {}

fun Route.oversiktApi(
    databaseContext: DatabaseContext,
    enhetsregisteretService: EnhetsregisteretService,
) {
    route("/oversikt") {
        // Detealjene for et gitt krav sendt inn av optiker
        get("/{vedtakId}") {
            val vedtakId = (call.parameters["vedtakId"] ?: error("Mangler vedtakId i url")).toLong()
            val fnrInnsender = call.extractFnr()
            val vedtak = transaction(databaseContext) { ctx ->
                ctx.vedtakStore.hentVedtakForOptiker(
                    fnrInnsender,
                    vedtakId,
                )
            }?.let { vedtak ->
                vedtak.orgnavn = enhetsregisteretService.hentOrganisasjonsenhet(vedtak.orgnr)?.navn ?: "Ukjent"
                vedtak
            }

            if (vedtak == null) {
                call.respond(HttpStatusCode.NotFound, """{"error":"not found"}""")
                return@get
            }
            call.respond(vedtak)
        }

        // Alle krav sendt inn av innlogget optiker
        get("/") {
            val page = (call.request.queryParameters["page"] ?: "1").toInt()
            val fnrInnsender = call.extractFnr()

            val resultat = transaction(databaseContext) { ctx ->
                ctx.vedtakStore.hentAlleVedtakForOptiker(fnrInnsender, page)
            }

            // In-mem cache av enhetsregister-oppslag slik at vi ikke hit'er redis 10 gang på rad per request
            val enhetsregisterOppslagCache = mutableMapOf<String, String>()

            resultat.items = resultat.items.map { vedtak ->
                vedtak.orgnavn = enhetsregisterOppslagCache[vedtak.orgnr].let {
                    if (it != null) {
                        it
                    } else {
                        val orgnavn =
                            enhetsregisteretService.hentOrganisasjonsenhet(vedtak.orgnr)?.navn ?: "<Ukjent>"
                        enhetsregisterOppslagCache[vedtak.orgnr] = orgnavn
                        orgnavn
                    }
                }
                vedtak
            }
            call.respond(resultat)
        }
    }
}
