package no.nav.syfo.client.graphapi

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val CALL_GRAPHAPI_USER_GROUPS_PERSON_BASE = "${METRICS_NS}call_graphapi_user_groups"
const val CALL_GRAPHAPI_USER_GROUPS_PERSON_SUCCESS = "${CALL_GRAPHAPI_USER_GROUPS_PERSON_BASE}_success_count"
const val CALL_GRAPHAPI_USER_GROUPS_PERSON_FAIL = "${CALL_GRAPHAPI_USER_GROUPS_PERSON_BASE}_fail_count"

val COUNT_CALL_GRAPHAPI_USER_GROUPS_PERSON_SUCCESS: Counter = Counter.builder(CALL_GRAPHAPI_USER_GROUPS_PERSON_SUCCESS)
    .description("Counts the number of successful calls to graph api user groups")
    .register(METRICS_REGISTRY)

val COUNT_CALL_GRAPHAPI_USER_GROUPS_PERSON_FAIL: Counter = Counter.builder(CALL_GRAPHAPI_USER_GROUPS_PERSON_FAIL)
    .description("Counts the number of failed calls to graph api user groups")
    .register(METRICS_REGISTRY)
