package no.nav.hjelpemidler.brille.tss

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.hjelpemidler.brille.utbetaling.PacketListenerWithOnError
import java.util.UUID

private val log = KotlinLogging.logger {}

class TssIdentRiver(
    rapidsConnection: RapidsConnection,
    private val tssIdentService: TssIdentService,
) : PacketListenerWithOnError {
    private val eventName = "hm-utbetaling-tss-optiker-svar"

    init {
        log.info { "registering ${this.javaClass.simpleName}" }
        River(rapidsConnection).apply {
            validate {
                it.demandValue("eventName", eventName)
            }
            validate {
                it.requireKey(
                    "eventId",
                    "orgnr",
                    "kontonr",
                    "tssIdent",
                    "opprettet",
                )
            }
        }.register(this)
    }

    private val JsonMessage.eventId get() = this["eventId"].textValue().let { UUID.fromString(it) }!!
    private val JsonMessage.orgnr get() = this["orgnr"].textValue()!!
    private val JsonMessage.kontonr get() = this["kontonr"].textValue()!!
    private val JsonMessage.tssIdent get() = this["tssIdent"].textValue()!!
    private val JsonMessage.opprettet get() = this["opprettet"].textValue()!!

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info { "Kvittering for oppdatering av TSS mottatt: eventId=${packet.eventId}, orgnr=${packet.orgnr}, kontonr=${packet.kontonr}, tssIdent=${packet.tssIdent}, opprettet=${packet.opprettet}" }
        runBlocking(Dispatchers.IO) {
            tssIdentService.settTssIdent(packet.orgnr, packet.kontonr, packet.tssIdent)
            log.info { "Kontonr synkronisert til TSS: orgnr=${packet.orgnr}, kontonr=${packet.kontonr}, tssIdent=${packet.tssIdent}, kvittert=${packet.opprettet}" }
        }
    }
}
