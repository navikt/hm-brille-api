package no.nav.hjelpemidler.brille.avtale

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.altinn.AltinnRolle
import no.nav.hjelpemidler.brille.altinn.AltinnRoller
import no.nav.hjelpemidler.brille.altinn.Rettighet
import no.nav.hjelpemidler.brille.extractFnr

private val log = KotlinLogging.logger { }

fun Route.avtaleApi(avtaleService: AvtaleService) {
    route("/avtale") {
        route("/virksomheter") {
            // hent alle avtaler
            get {
                val virksomheter = avtaleService.hentVirksomheter(
                    fnrInnsender = call.extractFnr(),
                    rettighet = Rettighet.OPPGJØRSAVTALE,
                    roller = AltinnRoller(AltinnRolle.HOVEDADMINISTRATOR)
                )
                call.respond(HttpStatusCode.OK, virksomheter)
            }
            // hent avtale for virksomhet
            get("/{orgnr}") {
                val orgnr = call.orgnr()
                val virksomhet = avtaleService.hentVirksomheter(
                    fnrInnsender = call.extractFnr(),
                    rettighet = Rettighet.OPPGJØRSAVTALE,
                    roller = AltinnRoller(AltinnRolle.HOVEDADMINISTRATOR)
                ).associateBy {
                    it.orgnr
                }[orgnr]
                if (virksomhet == null) {
                    call.response.status(HttpStatusCode.NotFound)
                    return@get
                }
                call.respond(HttpStatusCode.OK, virksomhet)
            }

            // opprett avtale
            post {
                val opprettAvtale = call.receive<OpprettAvtale>()
                val avtale = avtaleService.opprettAvtale(call.extractFnr(), opprettAvtale)
                call.respond(HttpStatusCode.Created, avtale)
            }
            // oppdater avtale
            put("/{orgnr}") {
                val orgnr = call.orgnr()
                val oppdaterAvtale = call.receive<OppdaterAvtale>()
                val avtale = avtaleService.oppdaterAvtale(call.extractFnr(), orgnr, oppdaterAvtale)
                call.respond(HttpStatusCode.OK, avtale)
            }

            // Egne endepunkter for rettighet utbetalingsrapport
            route("/regna") {
                get {
                    val virksomheter = avtaleService.hentVirksomheter(
                        fnrInnsender = call.extractFnr(),
                        rettighet = Rettighet.UTBETALINGSRAPPORT,
                        AltinnRoller(
                            AltinnRolle.HOVEDADMINISTRATOR,
                            AltinnRolle.REGNSKAPSMEDARBEIDER,
                            AltinnRolle.REGNSKAPSFØRER,
                        )
                    )
                    call.respond(HttpStatusCode.OK, virksomheter)
                }
                get("/{orgnr}") {
                    val orgnr = call.orgnr()
                    val virksomhet = avtaleService.hentVirksomheter(
                        fnrInnsender = call.extractFnr(),
                        rettighet = Rettighet.UTBETALINGSRAPPORT,
                        AltinnRoller(
                            AltinnRolle.HOVEDADMINISTRATOR,
                            AltinnRolle.REGNSKAPSMEDARBEIDER,
                            AltinnRolle.REGNSKAPSFØRER,
                        )
                    ).associateBy {
                        it.orgnr
                    }[orgnr]
                    if (virksomhet == null) {
                        call.response.status(HttpStatusCode.NotFound)
                        return@get
                    }
                    call.respond(HttpStatusCode.OK, virksomhet)
                }
            }
        }
    }
}

private fun ApplicationCall.orgnr(): String = requireNotNull(parameters["orgnr"]) {
    "Mangler orgnr i URL"
}
