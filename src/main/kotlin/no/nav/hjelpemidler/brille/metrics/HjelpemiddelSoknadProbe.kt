package no.nav.hjelpemidler.brille.metrics

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.kafka.KafkaProducer

private val log = KotlinLogging.logger {}

class HjelpemiddelSoknadProbe(
    private val kafkaProducer: KafkaProducer,
) {

//    private suspend fun finnFlereEnnEnHjelpemidler(soknad: Soknad) {
//        soknad.hjelpemidler.hjelpemiddelListe.forEach {
//            val antall = it.antall
//            val kategori = it.produkt?.kategori
//            val hmsNr = it.hmsNr
//
//            if (antall > 1 && kategori !== null) {
//                kafkaProducer.hendelseOpprettet(
//                    HJELPEMIDLER_FLERE_ENN_EN,
//                    mapOf("counter" to antall),
//                    mapOf("kategori" to kategori, "hmsNr" to hmsNr),
//                )
//            }
//        }
//    }

    companion object {
        private const val BRILLE = "hm-brille-api"
    }
}
