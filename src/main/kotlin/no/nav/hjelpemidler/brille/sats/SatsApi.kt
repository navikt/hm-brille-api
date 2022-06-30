package no.nav.hjelpemidler.brille.sats

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.satsApi() {
    post("/beregn-sats") {
        val dto = call.receive<BeregnSatsDto>()
        val satsKalkulator = SatsKalkulator(
            SatsGrunnlag(
                høyreSfære = dto.høyreSfære,
                høyreSylinder = dto.høyreSylinder,
                venstreSfære = dto.venstreSfære,
                venstreSylinder = dto.venstreSylinder
            )
        )
        val satsType = satsKalkulator.kalkuler()
        call.respond(BeregnetSatsDto(satsType, satsType.beskrivelse, satsType.beløp))
    }
}
