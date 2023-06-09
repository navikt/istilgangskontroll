package no.nav.syfo.application.api

import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.application.ApplicationState
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PodApiSpek : Spek({

    describe("Successful liveness and readiness checks") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerPodApi(
                    applicationState = ApplicationState(
                        alive = true,
                        ready = true
                    ),
                )
            }

            it("Returns ok on is_alive") {
                with(handleRequest(HttpMethod.Get, "/internal/is_alive")) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
            it("Returns ok on is_ready") {
                with(handleRequest(HttpMethod.Get, "/internal/is_ready")) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }

    describe("Unsuccessful liveness and readiness checks") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerPodApi(
                    ApplicationState(
                        alive = false,
                        ready = false
                    ),
                )
            }

            it("Returns internal server error when liveness check fails") {
                with(handleRequest(HttpMethod.Get, "/internal/is_alive")) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }

            it("Returns internal server error when readiness check fails") {
                with(handleRequest(HttpMethod.Get, "/internal/is_ready")) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }
})
