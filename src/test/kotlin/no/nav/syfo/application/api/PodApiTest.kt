package no.nav.syfo.application.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.application.ApplicationState
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PodApiTest {

    private fun ApplicationTestBuilder.setupPodApi(applicationState: ApplicationState) {
        application {
            routing {
                registerPodApi(
                    applicationState = applicationState,
                )
            }
        }
    }

    @Nested
    @DisplayName("Successful liveness and readiness checks")
    inner class SuccessfulLivenessAndReadinessChecks {

        @Test
        fun `Returns ok on is_alive`() {
            testApplication {
                setupPodApi(
                    applicationState = ApplicationState(alive = true, ready = true),
                )
                val response = client.get("/internal/is_alive")
                assertTrue(response.status.isSuccess())
                assertNotNull(response.bodyAsText())
            }
        }

        @Test
        fun `Returns ok on is_ready`() {
            testApplication {
                setupPodApi(
                    applicationState = ApplicationState(alive = true, ready = true),
                )
                val response = client.get("/internal/is_ready")
                assertTrue(response.status.isSuccess())
                assertNotNull(response.bodyAsText())
            }
        }
    }

    @Nested
    @DisplayName("Unsuccessful liveness and readiness checks")
    inner class UnsuccessfulLivenessAndReadinessChecks {

        @Test
        fun `Returns internal server error when liveness check fails`() {
            testApplication {
                setupPodApi(
                    applicationState = ApplicationState(alive = false, ready = true),
                )
                val response = client.get("/internal/is_alive")
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertNotNull(response.bodyAsText())
            }
        }

        @Test
        fun `Returns internal server error when readiness check fails`() {
            testApplication {
                setupPodApi(
                    applicationState = ApplicationState(alive = true, ready = false),
                )
                val response = client.get("/internal/is_ready")
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertNotNull(response.bodyAsText())
            }
        }
    }
}
