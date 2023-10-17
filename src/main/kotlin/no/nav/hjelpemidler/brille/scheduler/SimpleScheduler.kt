package no.nav.hjelpemidler.brille.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.hjelpemidler.brille.internal.MetricsConfig
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

/**
 * A very simple implementation of a scheduler using coroutines and leaderElection
 * An action that failed with an exception and is not caught will also cancel the job.
 */
abstract class SimpleScheduler(
    private val leaderElection: LeaderElection,
    private val delay: Duration = 1.minutes,
    private val metricsConfig: MetricsConfig,
    private val onlyWorkHours: Boolean = false,
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
                LOG.info("Running $mySchedulerName")
                launch {
                    try {
                        val time = System.currentTimeMillis()
                        action()
                        val duration = (System.currentTimeMillis() - time).milliseconds
                        metricsConfig.registry.counter("scheduler_duration_seconds", "name", mySchedulerName)
                            .increment(duration.toDouble(DurationUnit.SECONDS))
                        if (duration > delay) {
                            LOG.warn("$mySchedulerName spent $duration ms which is greater than delayTime: $delay")
                        }
                    } catch (e: Exception) {
                        LOG.error("Scheduler $mySchedulerName has failed with an exception, the scheduler will be stopped", e)
                        throw e
                    }
                }
            } else {
                LOG.info(
                    "NOT running $mySchedulerName: isLeader: ${leaderElection.isLeader()}" +
                        ", onlyWorkHours: $onlyWorkHours, isWorkingHours: ${
                            LocalDateTime.now().isWorkingHours()
                        }",
                )
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
    return !isWeekend() && isBetweenEightToFive()
}

fun LocalDateTime.isBetweenEightToFive(): Boolean = toLocalTime() in LocalTime.of(8, 0)..LocalTime.of(17, 0)
fun LocalDateTime.isWeekend(): Boolean = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
