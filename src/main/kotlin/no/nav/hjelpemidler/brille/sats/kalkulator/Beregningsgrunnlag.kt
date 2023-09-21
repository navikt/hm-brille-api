package no.nav.hjelpemidler.brille.sats.kalkulator

import com.fasterxml.jackson.annotation.JsonCreator
import no.nav.hjelpemidler.brille.sats.Brilleseddel
import java.time.LocalDate

data class Beregningsgrunnlag(
    val brilleseddel: Brilleseddel,
    val alder: Boolean,
    val vedtak: Boolean,
    val folketrygden: Boolean,
    val strabisme: Boolean,
    val bestillingsdato: LocalDate = LocalDate.now(),
) {
    @JsonCreator
    constructor(
        høyreSfære: Double,
        høyreSylinder: Double,
        venstreSfære: Double,
        venstreSylinder: Double,
        alder: Boolean,
        vedtak: Boolean,
        folketrygden: Boolean,
        strabisme: Boolean,
        bestillingsdato: LocalDate = LocalDate.now(),
    ) : this(
        brilleseddel = Brilleseddel(
            høyreSfære = høyreSfære,
            høyreSylinder = høyreSylinder,
            venstreSfære = venstreSfære,
            venstreSylinder = venstreSylinder,
        ),
        alder = alder,
        vedtak = vedtak,
        folketrygden = folketrygden,
        strabisme = strabisme,
        bestillingsdato = bestillingsdato,
    )
}
