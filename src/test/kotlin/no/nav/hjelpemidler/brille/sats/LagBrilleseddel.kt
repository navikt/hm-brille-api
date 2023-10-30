package no.nav.hjelpemidler.brille.sats

import no.nav.hjelpemidler.brille.test.Builder

class BrilleseddelBuilder : Builder<Brilleseddel> {
    var høyreSfære: Double = 1.0
    var høyreSylinder: Double = 0.0
    var høyreAdd: Double = 0.0
    var venstreSfære: Double = 0.0
    var venstreSylinder: Double = 0.0
    var venstreAdd: Double = 0.0
    override fun build(): Brilleseddel = Brilleseddel(
        høyreSfære = høyreSfære,
        høyreSylinder = høyreSylinder,
        høyreAdd = høyreAdd,
        venstreSfære = venstreSfære,
        venstreSylinder = venstreSylinder,
        venstreAdd = venstreAdd,
    )
}

fun lagBrilleseddel(block: BrilleseddelBuilder.() -> Unit = {}): Brilleseddel =
    BrilleseddelBuilder().apply(block).build()
