package no.nav.syfo.client.pdl

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val CALL_PDL_BASE = "${METRICS_NS}_call_pdl"

const val CALL_PDL_PERSON_BASE = "${CALL_PDL_BASE}_person"
const val CALL_PDL_PERSON_SUCCESS = "${CALL_PDL_PERSON_BASE}_success_count"
const val CALL_PDL_PERSON_FAIL = "${CALL_PDL_PERSON_BASE}_fail_count"
const val CALL_PDL_PERSON_CACHE_HIT = "${CALL_PDL_PERSON_BASE}_cache_hit"
const val CALL_PDL_PERSON_CACHE_MISS = "${CALL_PDL_PERSON_BASE}_cache_miss"

val COUNT_CALL_PDL_PERSON_SUCCESS: Counter = Counter.builder(CALL_PDL_PERSON_SUCCESS)
    .description("Counts the number of successful calls to persondatalosning - person")
    .register(METRICS_REGISTRY)
val COUNT_CALL_PDL_PERSON_FAIL: Counter = Counter.builder(CALL_PDL_PERSON_FAIL)
    .description("Counts the number of failed calls to persondatalosning - person")
    .register(METRICS_REGISTRY)
val COUNT_CALL_PDL_PERSON_CACHE_HIT: Counter = Counter.builder(CALL_PDL_PERSON_CACHE_HIT)
    .description("Counts the number of cache hits for persondatalosning - person")
    .register(METRICS_REGISTRY)
val COUNT_CALL_PDL_PERSON_CACHE_MISS: Counter = Counter.builder(CALL_PDL_PERSON_CACHE_MISS)
    .description("Counts the number of cache miss for persondatalosning - person")
    .register(METRICS_REGISTRY)
