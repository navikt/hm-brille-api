package no.nav.hjelpemidler.brille.innsender

class InnsenderService(private val innsenderStore: InnsenderStore) {
    fun godtaAvtale(fnrInnsender: String): Innsender {
        val innsender = Innsender(fnrInnsender = fnrInnsender, godtatt = true)
        innsenderStore.lagreInnsender(innsender)
        return innsender
    }

    fun hentInnsender(fnrInnsender: String): Innsender =
        innsenderStore.hentInnsender(fnrInnsender) ?: Innsender(fnrInnsender = fnrInnsender, godtatt = false)
}
