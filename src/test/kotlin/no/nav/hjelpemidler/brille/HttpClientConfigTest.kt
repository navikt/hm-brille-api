package no.nav.hjelpemidler.brille

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.IdentityProvider
import no.nav.hjelpemidler.http.openid.Texas
import no.nav.hjelpemidler.http.openid.TokenSet
import no.nav.hjelpemidler.http.openid.onBehalfOf
import no.nav.hjelpemidler.http.openid.openId
import no.nav.hjelpemidler.http.openid.target
import no.nav.hjelpemidler.security.AutentisertPerson
import no.nav.hjelpemidler.security.AuthenticationContext
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

class HttpClientConfigTest {
    @Test
    fun `Entra ID uses application tokens even with a user in the request and context`() = runTest {
        val texas = mockk<Texas> {
            every { entraId(any()) } answers { callOriginal() }
        }
        val user = mockk<AutentisertPerson> {
            every { userToken } returns "context-user-token"
        }
        val scope = "api://downstream/.default"
        coEvery { texas.token(IdentityProvider.ENTRA_ID, scope) } returns TokenSet("application-token", 1.hours)
        val engine = MockEngine { request ->
            request.headers[HttpHeaders.Authorization] shouldBe "Bearer application-token"
            respond("")
        }
        createHttpClient(engine) {
            openId(texas.entraIdApplication(scope))
        }.use { client ->
            withContext(AuthenticationContext(user)) {
                client.get("https://downstream.test") {
                    onBehalfOf("request-user-token")
                }
            }
        }
        coVerify(exactly = 1) { texas.token(IdentityProvider.ENTRA_ID, scope) }
        coVerify(exactly = 0) { texas.exchange(any(), any(), any()) }
    }

    @Test
    fun `application token provider preserves request target overrides`() = runTest {
        val texas = mockk<Texas> {
            every { entraId(any()) } answers { callOriginal() }
        }
        val scope = "api://overridden/.default"
        coEvery { texas.token(IdentityProvider.ENTRA_ID, scope) } returns TokenSet("application-token", 1.hours)
        val engine = MockEngine { respond("") }
        createHttpClient(engine) {
            openId(texas.entraIdApplication("api://default/.default"))
        }.use { client ->
            client.get("https://downstream.test") {
                target(scope)
            }
        }
        coVerify(exactly = 1) { texas.token(IdentityProvider.ENTRA_ID, scope) }
        coVerify(exactly = 0) { texas.exchange(any(), any(), any()) }
    }
}
