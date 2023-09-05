package no.nav.syfo.client.norg

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Counter.builder
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val CALL_NORG_ENHET_BASE = "${METRICS_NS}_call_norg_arbeidsfordeling"
const val CALL_NORG_ENHET_SUCCESS = "${CALL_NORG_ENHET_BASE}_success_count"
const val CALL_NORG_ENHET_FAIL = "${CALL_NORG_ENHET_BASE}_fail_count"

val COUNT_CALL_NORG_ENHET_SUCCESS: Counter = builder(CALL_NORG_ENHET_SUCCESS)
    .description("Counts the number of successful calls to Norg - Enhet")
    .register(METRICS_REGISTRY)

val COUNT_CALL_NORG_ENHET_FAIL: Counter = builder(CALL_NORG_ENHET_FAIL)
    .description("Counts the number of failed calls to Norg - Enhet")
    .register(METRICS_REGISTRY)
