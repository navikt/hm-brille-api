package no.nav.hjelpemidler.brille.utbetaling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.logging.teamInfo

private val log = KotlinLogging.logger {}

interface PacketListenerWithOnError : River.PacketListener {
    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.teamInfo { "Feil under validering av melding: '${problems.toExtendedReport()}'" }
        error("Feil under validering av melding, se teamLog for detaljer")
    }
}
