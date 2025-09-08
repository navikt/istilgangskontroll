package no.nav.syfo.client.graphapi

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val CALL_GRAPHAPI_USER_GROUPS_PERSON_BASE = "${METRICS_NS}_call_graphapi_user_groups"
const val CALL_GRAPHAPI_USER_GROUPS_PERSON_SUCCESS = "${CALL_GRAPHAPI_USER_GROUPS_PERSON_BASE}_success_count"
const val CALL_GRAPHAPI_USER_GROUPS_PERSON_FAIL = "${CALL_GRAPHAPI_USER_GROUPS_PERSON_BASE}_fail_count"

// TODO: Skal disse tas i bruk i stedet for de nye som er definert?
val COUNT_CALL_GRAPHAPI_USER_GROUPS_PERSON_SUCCESS: Counter = Counter.builder(CALL_GRAPHAPI_USER_GROUPS_PERSON_SUCCESS)
    .description("Counts the number of successful calls to graph api user groups")
    .register(METRICS_REGISTRY)

val COUNT_CALL_GRAPHAPI_USER_GROUPS_PERSON_FAIL: Counter = Counter.builder(CALL_GRAPHAPI_USER_GROUPS_PERSON_FAIL)
    .description("Counts the number of failed calls to graph api user groups")
    .register(METRICS_REGISTRY)

const val CALL_MS_GRAPH_API_GRUPPE_BASE = "${METRICS_NS}_gruppe"
const val CALL_MS_GRAPH_API_GRUPPE_SUCCESS = "${CALL_MS_GRAPH_API_GRUPPE_BASE}_success_count"
const val CALL_MS_GRAPH_API_GRUPPE_FAIL = "${CALL_MS_GRAPH_API_GRUPPE_BASE}_fail_count"
const val CALL_MS_GRAPH_API_GRUPPE_CACHE_HIT = "${CALL_MS_GRAPH_API_GRUPPE_BASE}_cache_hit"
const val CALL_MS_GRAPH_API_GRUPPE_CACHE_MISS = "${CALL_MS_GRAPH_API_GRUPPE_BASE}_cache_miss"

val COUNT_CALL_MS_GRAPH_API_GRUPPE_SUCCESS: Counter = Counter
    .builder(CALL_MS_GRAPH_API_GRUPPE_SUCCESS)
    .description("Counts the number of successful calls to Microsoft Graph API - Gruppe")
    .register(METRICS_REGISTRY)
val COUNT_CALL_MS_GRAPH_API_GRUPPE_FAIL: Counter = Counter
    .builder(CALL_MS_GRAPH_API_GRUPPE_FAIL)
    .description("Counts the number of failed calls to Microsoft Graph API - Gruppe")
    .register(METRICS_REGISTRY)
val COUNT_CALL_MS_GRAPH_API_GRUPPE_CACHE_HIT: Counter = Counter
    .builder(CALL_MS_GRAPH_API_GRUPPE_CACHE_HIT)
    .description("Counts the number of cache hits Microsoft Graph API - Gruppe")
    .register(METRICS_REGISTRY)
val COUNT_CALL_MS_GRAPH_API_GRUPPE_CACHE_MISS: Counter = Counter
    .builder(CALL_MS_GRAPH_API_GRUPPE_CACHE_MISS)
    .description("Counts the number of cache miss Microsoft Graph API - Gruppe")
    .register(METRICS_REGISTRY)
