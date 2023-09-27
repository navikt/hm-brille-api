package no.nav.hjelpemidler.brille.sats

import io.ktor.http.HttpStatusCode
import no.nav.hjelpemidler.brille.StatusCodeException

data class Brilleseddel(
    val høyreSfære: Double,
    val høyreSylinder: Double,
    val høyreAdd: Double = 0.0,
    val venstreSfære: Double,
    val venstreSylinder: Double,
    val venstreAdd: Double = 0.0,
) {
    init {
        val range = Diopter.MIN_SFÆRE..Diopter.MAX
        when {
            høyreSfære !in range -> throw StatusCodeException(
                HttpStatusCode.BadRequest,
                "høyreSfære må være innenfor $range",
            )

            høyreSylinder !in range -> throw StatusCodeException(
                HttpStatusCode.BadRequest,
                "høyreSylinder må være innenfor $range",
            )

            venstreSfære !in range -> throw StatusCodeException(
                HttpStatusCode.BadRequest,
                "venstreSfære må være innenfor $range",
            )

            venstreSylinder !in range -> throw StatusCodeException(
                HttpStatusCode.BadRequest,
                "venstreSylinder må være innenfor $range",
            )
        }
    }
}
