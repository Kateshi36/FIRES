package com.example.fires.util

import com.example.fires.data.model.Assignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssignmentRulesTest {

    @Test fun a_unit_from_the_list_is_required() {
        assertEquals(AssignmentRules.MSG_UNIT_REQUIRED, AssignmentRules.validateUnit(null))
        assertEquals(AssignmentRules.MSG_UNIT_REQUIRED, AssignmentRules.validateUnit(""))
        assertEquals(AssignmentRules.MSG_UNIT_REQUIRED, AssignmentRules.validateUnit("Somebody else"))
        AssignmentRules.UNITS.forEach { assertNull(it, AssignmentRules.validateUnit(it)) }
    }

    @Test fun the_unit_list_matches_the_plan() {
        assertEquals(listOf("B-FLARE", "BDRRMO", "BFP", "Neighboring barangay"), AssignmentRules.UNITS)
        assertEquals(listOf("Fire truck", "Water tanker"), AssignmentRules.RESOURCES)
    }

    @Test fun toggle_adds_then_removes() {
        val on = AssignmentRules.toggle(emptySet(), "Fire truck")
        assertEquals(setOf("Fire truck"), on)
        assertEquals(emptySet<String>(), AssignmentRules.toggle(on, "Fire truck"))
    }

    @Test fun saved_resources_follow_the_list_order_not_the_tap_order() {
        assertEquals(
            listOf("Fire truck", "Water tanker"),
            AssignmentRules.orderedResources(setOf("Water tanker", "Fire truck"))
        )
    }

    @Test fun unknown_resources_are_not_saved() {
        assertEquals(listOf("Fire truck"), AssignmentRules.orderedResources(setOf("Fire truck", "Helicopter")))
    }

    @Test fun labels_handle_no_resources() {
        assertEquals(AssignmentRules.NO_RESOURCES, AssignmentRules.resourcesLabel(emptyList()))
        assertEquals("BFP · Fire truck, Water tanker",
            AssignmentRules.label(Assignment(unit = "BFP", assignedResources = listOf("Fire truck", "Water tanker"))))
        assertEquals("BDRRMO · " + AssignmentRules.NO_RESOURCES,
            AssignmentRules.label(Assignment(unit = "BDRRMO")))
    }
}
