package no.nav.hjelpemidler.brille.sats.kalkulator

import com.fasterxml.jackson.annotation.JsonCreator
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import java.time.LocalDate

data class Beregningsgrunnlag(
    val brilleseddel: Brilleseddel,
    val alder: Boolean,
    val strabisme: Boolean,
    val bestillingsdato: LocalDate = LocalDate.now(),
) {
    @JsonCreator
    constructor(
        høyreSfære: Double,
        høyreSylinder: Double,
        høyreAdd: Double?,
        venstreSfære: Double,
        venstreSylinder: Double,
        venstreAdd: Double?,
        alder: Boolean,
        strabisme: Boolean,
        bestillingsdato: LocalDate = LocalDate.now(),
    ) : this(
        brilleseddel = Brilleseddel(
            høyreSfære = høyreSfære,
            høyreSylinder = høyreSylinder,
            venstreSfære = venstreSfære,
            venstreSylinder = venstreSylinder,
            venstreAdd = venstreAdd ?: 0.0,
            høyreAdd = høyreAdd ?: 0.0,
        ),
        alder = alder,
        strabisme = strabisme,
        bestillingsdato = bestillingsdato,
    )
}
