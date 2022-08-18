package no.nav.hjelpemidler.brille.scheduler

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.*
import io.ktor.serialization.jackson.jackson
import no.nav.hjelpemidler.brille.StubEngine
import no.nav.hjelpemidler.brille.engineFactory
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.LocalDateTime

class LeaderElection(electorPath: String) {

    private val hostname = InetAddress.getLocalHost().hostName
    private var leader = ""
    private val electorUri = "http://" + electorPath
    private val engine: HttpClientEngine = engineFactory { StubEngine.leaderElection() }

    private val client = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
    }

    init {
        LOG.info("leader election initialized this hostname is $hostname")
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(LeaderElection::class.java)
    }

    suspend fun isLeader(): Boolean {
        return hostname == getLeader()
    }

    private suspend fun getLeader(): String {
        val response = client.get(electorUri)
        if (response.status == HttpStatusCode.OK) {
            leader = response.body<Elector>().name
            LOG.info("Running leader election getLeader is {} ", leader)
        }
        return leader
    }
}

data class Elector(val name: String)
