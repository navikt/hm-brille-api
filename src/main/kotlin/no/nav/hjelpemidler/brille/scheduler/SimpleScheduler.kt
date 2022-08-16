package no.nav.hjelpemidler.brille.scheduler

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SimpleScheduler(private val leaderElection: LeaderElection) {

    suspend fun runTaskEvery(delayTimeMillis: Long, action: suspend () -> Unit) = coroutineScope {
        while (true) {
            delay(delayTimeMillis)
            if (leaderElection.isLeader()) launch { action() }
        }
    }
}
