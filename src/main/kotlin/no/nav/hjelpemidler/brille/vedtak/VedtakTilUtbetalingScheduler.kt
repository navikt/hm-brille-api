package no.nav.hjelpemidler.brille.vedtak

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.hjelpemidler.brille.scheduler.SimpleScheduler

class VedtakTilUtbetalingScheduler(private val simpleScheduler: SimpleScheduler, private val vedtakService: VedtakService) {

    private fun hentVedtakForUtbetalingTask() = suspend {
        println("Vi henter vedtak her!!")
    }

    fun start() {
        CoroutineScope(Dispatchers.Unconfined).launch {
            simpleScheduler.runTaskEvery(5000L, hentVedtakForUtbetalingTask())
        }
    }
}
