package no.nav.hjelpemidler.brille.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * A very simple implementation of a scheduler using coroutines and leaderElection
 * An action that failed with an exception and is not caught will also cancel the job.
 */
abstract class SimpleScheduler(
    private val leaderElection: LeaderElection,
    private val delay: Duration = 1.minutes,
    private val onlyWorkHours: Boolean = false
) {
    private val job: Job
    private val mySchedulerName: String = this.javaClass.simpleName

    companion object {
        private val LOG = LoggerFactory.getLogger(SimpleScheduler::class.java)
    }

    init {
        LOG.info("starting scheduler: $mySchedulerName with a delay of $delay")
        if (onlyWorkHours) LOG.info("$mySchedulerName task will only be launched during working hours.")
        job = CoroutineScope(Dispatchers.Default).launch {
            runTask()
        }
    }

    suspend fun runTask() = coroutineScope {
        while (true) {
            delay(delay)
            if (leaderElection.isLeader() && (!onlyWorkHours || LocalDateTime.now().isWorkingHours())) {
                launch {
                    val time = System.currentTimeMillis()
                    action()
                    val duration = System.currentTimeMillis() - time
                    if (duration > delay.inWholeMilliseconds) {
                        LOG.warn("$mySchedulerName spent $duration ms which is greater than delayTime: $delay")
                    }
                }
            }
        }
    }

    abstract suspend fun action()

    fun cancel() {
        LOG.info("cancel job $mySchedulerName")
        job.cancel()
    }
}

fun LocalDateTime.isWorkingHours(): Boolean {
    return !isWeekend() && isBetweenEightToFour()
}

fun LocalDateTime.isBetweenEightToFour(): Boolean = toLocalTime() in LocalTime.of(8, 0)..LocalTime.of(16, 0)
fun LocalDateTime.isWeekend(): Boolean = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
