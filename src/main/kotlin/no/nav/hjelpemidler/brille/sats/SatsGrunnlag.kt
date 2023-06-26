package no.nav.hjelpemidler.brille.sats

import com.fasterxml.jackson.annotation.JsonCreator
import java.time.LocalDate

data class SatsGrunnlag(
    val brilleseddel: Brilleseddel,
    val bestillingsdato: LocalDate = LocalDate.now(),
) {
    @JsonCreator
    constructor(
        høyreSfære: Double,
        høyreSylinder: Double,
        venstreSfære: Double,
        venstreSylinder: Double,
        bestillingsdato: LocalDate = LocalDate.now(),
    ) : this(
        brilleseddel = Brilleseddel(
            høyreSfære = høyreSfære,
            høyreSylinder = høyreSylinder,
            venstreSfære = venstreSfære,
            venstreSylinder = venstreSylinder
        ),
        bestillingsdato = bestillingsdato,
    )
}
