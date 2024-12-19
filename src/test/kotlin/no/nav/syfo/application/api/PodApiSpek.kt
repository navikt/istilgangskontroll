package no.nav.syfo.application.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.application.ApplicationState
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PodApiSpek : Spek({

    fun ApplicationTestBuilder.setupPodApi(applicationState: ApplicationState) {
        application {
            routing {
                registerPodApi(
                    applicationState = applicationState,
                )
            }
        }
    }

    describe("Successful liveness and readiness checks") {
        it("Returns ok on is_alive") {
            testApplication {
                setupPodApi(
                    applicationState = ApplicationState(alive = true, ready = true),
                )
                val response = client.get("/internal/is_alive")
                response.status.isSuccess() shouldBeEqualTo true
                response.bodyAsText() shouldNotBeEqualTo null
            }
        }
        it("Returns ok on is_ready") {
            testApplication {
                setupPodApi(
                    applicationState = ApplicationState(alive = true, ready = true),
                )
                val response = client.get("/internal/is_ready")
                response.status.isSuccess() shouldBeEqualTo true
                response.bodyAsText() shouldNotBeEqualTo null
            }
        }
    }

    describe("Unsuccessful liveness and readiness checks") {

        it("Returns internal server error when liveness check fails") {
            testApplication {
                setupPodApi(
                    applicationState = ApplicationState(alive = false, ready = true),
                )
                val response = client.get("/internal/is_alive")
                response.status shouldBeEqualTo HttpStatusCode.InternalServerError
                response.bodyAsText() shouldNotBeEqualTo null
            }
        }

        it("Returns internal server error when readiness check fails") {
            testApplication {
                setupPodApi(
                    applicationState = ApplicationState(alive = true, ready = false),
                )
                val response = client.get("/internal/is_ready")
                response.status shouldBeEqualTo HttpStatusCode.InternalServerError
                response.bodyAsText() shouldNotBeEqualTo null
            }
        }
    }
})
