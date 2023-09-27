package no.nav.hjelpemidler.brille.sats

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.hjelpemidler.brille.sats.kalkulator.Beregningsgrunnlag
import no.nav.hjelpemidler.brille.sats.kalkulator.KalkulatorService

fun Route.satsApi(
    kalkulatorService: KalkulatorService
) {
    // fixme -> fjernes når konsumenter har endret til ny url for satsberegning
    post("/brillesedler") {
        call.respond(beregnSats(call.receive<SatsGrunnlag>()))
    }
    post("/satsgrunnlag") {
        call.respond(beregnSats(call.receive<SatsGrunnlag>()))
    }
    post("/kalkulator/beregningsgrunnlag") {
        call.respond(kalkulatorService.kalkuler(call.receive<Beregningsgrunnlag>()))
    }
}
