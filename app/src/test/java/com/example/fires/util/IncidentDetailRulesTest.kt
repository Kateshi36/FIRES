package com.example.fires.util

import com.example.fires.data.model.Incident
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncidentDetailRulesTest {

    private fun inc(id: String, seconds: Long?) =
        Incident(id = id, submittedAt = seconds?.let { Timestamp(it, 0) })

    @Test fun contact_blank_says_not_provided() {
        assertEquals(IncidentDetailRules.NOT_PROVIDED, IncidentDetailRules.contactLabel("   "))
        assertEquals("0917 123 4567", IncidentDetailRules.contactLabel(" 0917 123 4567 "))
    }

    @Test fun dial_uri_drops_spaces_and_dashes() {
        assertEquals("tel:09171234567", IncidentDetailRules.dialUri("0917 123-4567"))
    }

    @Test fun dial_uri_keeps_leading_plus() {
        assertEquals("tel:+639171234567", IncidentDetailRules.dialUri(" +63 917 123 4567"))
    }

    @Test fun dial_uri_is_null_without_a_number() {
        assertNull(IncidentDetailRules.dialUri(""))
        assertNull(IncidentDetailRules.dialUri("n/a"))
        assertNull(IncidentDetailRules.dialUri("12"))
    }

    @Test fun people_at_risk_wording() {
        assertEquals(IncidentDetailRules.NONE_REPORTED, IncidentDetailRules.peopleAtRiskLabel(0))
        assertEquals("1 person", IncidentDetailRules.peopleAtRiskLabel(1))
        assertEquals("5 people", IncidentDetailRules.peopleAtRiskLabel(5))
    }

    @Test fun hazards_and_vulnerable_use_labels_and_keep_unknown_values() {
        assertEquals(
            "LPG / gas tank, Chemicals, mystery",
            IncidentDetailRules.hazardsLabel(listOf("lpg_tank", "chemicals", "mystery"))
        )
        assertEquals("Children, Elderly", IncidentDetailRules.vulnerableLabel(listOf("children", "elderly")))
        assertEquals(IncidentDetailRules.NONE_REPORTED, IncidentDetailRules.hazardsLabel(emptyList()))
        assertEquals(IncidentDetailRules.NONE_REPORTED, IncidentDetailRules.vulnerableLabel(emptyList()))
    }

    @Test fun severity_source_wording() {
        assertEquals("Calculated automatically", IncidentDetailRules.severitySourceLabel("auto"))
        assertEquals("Set by a responder", IncidentDetailRules.severitySourceLabel("responder"))
    }

    @Test fun location_source_wording() {
        assertEquals("Phone GPS", IncidentDetailRules.locationSourceLabel("gps"))
        assertEquals("Pin placed by the reporter", IncidentDetailRules.locationSourceLabel("pin"))
    }

    @Test fun coordinates_have_five_decimals_and_a_dot() {
        assertEquals("14.59950, 120.98420", IncidentDetailRules.coordinatesLabel(14.5995, 120.9842))
    }

    @Test fun duplicates_are_oldest_first_and_never_include_the_report_itself() {
        val sorted = IncidentDetailRules.sortDuplicates(
            listOf(inc("c", 300), inc("self", 50), inc("a", 100), inc("b", 200)),
            selfId = "self"
        )
        assertEquals(listOf("a", "b", "c"), sorted.map { it.id })
    }

    @Test fun a_duplicate_without_server_time_goes_last() {
        val sorted = IncidentDetailRules.sortDuplicates(listOf(inc("new", null), inc("old", 100)), "x")
        assertEquals(listOf("old", "new"), sorted.map { it.id })
    }
}
