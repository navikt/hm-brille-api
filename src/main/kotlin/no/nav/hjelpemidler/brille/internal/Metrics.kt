package no.nav.hjelpemidler.brille.internal

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.MeterBinder
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun Application.setupMetrics(metricsConfig: MetricsConfig) {
    install(MicrometerMetrics) {
        registry = metricsConfig.registry
        meterBinders = metricsConfig.meterbinders
    }

    routing {
        get("/internal/metrics") {
            call.respond(metricsConfig.registry.scrape())
        }
    }
}

data class MetricsConfig(
    val registry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
    val meterbinders: List<MeterBinder>,
)
