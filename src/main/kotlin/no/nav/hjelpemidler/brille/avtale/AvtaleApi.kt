package no.nav.hjelpemidler.brille.avtale

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.extractFnr
import no.nav.hjelpemidler.brille.virksomhet.Virksomhet
import no.nav.hjelpemidler.brille.virksomhet.VirksomhetStore
import org.postgresql.util.PSQLException

private val log = KotlinLogging.logger { }

fun Route.avtaleApi(avtaleService: AvtaleService, virksomhetStore: VirksomhetStore) {
    route("/avtale") {
        route("/virksomheter") {
            get("/") {
                val virksomheter = avtaleService.hentVirksomheter(call.extractFnr())
                call.respond(HttpStatusCode.OK, virksomheter)
            }
            get("/{orgnr}") {
                val orgnr = call.parameters["orgnr"] ?: error("Mangler orgnr i URL")
                val virksomhet = avtaleService.hentVirksomheter(call.extractFnr()).associateBy {
                    it.orgnr
                }[orgnr]
                if (virksomhet == null) {
                    call.response.status(HttpStatusCode.NotFound)
                    return@get
                }
                call.respond(HttpStatusCode.OK, virksomhet)
            }
            post("/") {
                data class OpprettAvtale(
                    val orgnr: String,
                    val kontonr: String,
                )

                val opprettAvtale = call.receive<OpprettAvtale>()

                try {
                    virksomhetStore.lagreVirksomhet(
                        Virksomhet(
                            orgnr = opprettAvtale.orgnr,
                            kontonr = opprettAvtale.kontonr,
                            fnrInnsender = call.extractFnr(),
                            navnInnsender = "", // fixme
                            harNavAvtale = true,
                        )
                    )
                } catch (e: PSQLException) {
                    log.error(e) { "Lagring av virksomhet feilet" }
                    if (e.message?.contains("duplicate key") == true &&
                        e.message?.contains("virksomhet_pkey") == true
                    ) {
                        return@post call.response.status(HttpStatusCode.Conflict)
                    }
                    return@post call.response.status(HttpStatusCode.InternalServerError)
                }

                call.response.status(HttpStatusCode.Created)
            }
        }
    }
}
