package com.example.fires.util

import com.example.fires.data.model.FireSize
import com.example.fires.data.model.FireType
import com.example.fires.data.model.Severity
import com.example.fires.data.model.VulnerableGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class SeverityCalculatorTest {

    private fun score(
        type: FireType = FireType.OTHER,
        size: FireSize = FireSize.SMALL,
        people: Int = 0,
        vulnerable: Set<VulnerableGroup> = emptySet(),
        trapped: Boolean = false
    ) = SeverityCalculator.score(type, size, people, vulnerable, trapped)

    private fun level(
        type: FireType = FireType.OTHER,
        size: FireSize = FireSize.SMALL,
        people: Int = 0,
        vulnerable: Set<VulnerableGroup> = emptySet(),
        trapped: Boolean = false
    ) = SeverityCalculator.calculate(type, size, people, vulnerable, trapped).severity

    // ---------- Each rule on its own ----------

    @Test fun nothingRisky_scoresZero_andIsLow() {
        assertEquals(0, score())
        assertEquals(Severity.LOW, level())
    }

    @Test fun trapped_adds4() = assertEquals(4, score(trapped = true))

    @Test fun vulnerable_adds2_onceNoMatterHowManyGroups() {
        assertEquals(2, score(vulnerable = setOf(VulnerableGroup.CHILDREN)))
        assertEquals(2, score(vulnerable = VulnerableGroup.entries.toSet()))
    }

    @Test fun people_bands() {
        assertEquals(0, score(people = 0))
        assertEquals(1, score(people = 1))
        assertEquals(1, score(people = 5))
        assertEquals(2, score(people = 6))
        assertEquals(2, score(people = 99))
    }

    @Test fun people_negative_isTreatedAsNone() = assertEquals(0, score(people = -3))

    @Test fun size_points() {
        assertEquals(0, score(size = FireSize.SMALL))
        assertEquals(1, score(size = FireSize.MEDIUM))
        assertEquals(3, score(size = FireSize.LARGE))
    }

    @Test fun structural_adds1_otherTypesAddNothing() {
        assertEquals(1, score(type = FireType.STRUCTURAL))
        listOf(FireType.ELECTRICAL, FireType.RUBBISH, FireType.VEHICLE, FireType.OTHER)
            .forEach { assertEquals(0, score(type = it)) }
    }

    // ---------- Level boundaries ----------

    @Test fun levelFor_boundaries() {
        assertEquals(Severity.LOW, SeverityCalculator.levelFor(0))
        assertEquals(Severity.LOW, SeverityCalculator.levelFor(1))
        assertEquals(Severity.MEDIUM, SeverityCalculator.levelFor(2))
        assertEquals(Severity.MEDIUM, SeverityCalculator.levelFor(3))
        assertEquals(Severity.HIGH, SeverityCalculator.levelFor(4))
        assertEquals(Severity.HIGH, SeverityCalculator.levelFor(6))
        assertEquals(Severity.CRITICAL, SeverityCalculator.levelFor(7))
        assertEquals(Severity.CRITICAL, SeverityCalculator.levelFor(12))
    }

    // ---------- Whole reports ----------

    @Test fun smallRubbishFire_nobodyInDanger_isLow() =
        assertEquals(Severity.LOW, level(type = FireType.RUBBISH))

    @Test fun mediumFireWithOnePerson_isMedium() {
        assertEquals(2, score(size = FireSize.MEDIUM, people = 1))
        assertEquals(Severity.MEDIUM, level(size = FireSize.MEDIUM, people = 1))
    }

    @Test fun someoneTrapped_aloneIsHigh() = assertEquals(Severity.HIGH, level(trapped = true))

    @Test fun trappedPlusVulnerable_isStillHigh_andStructuralTipsItToCritical() {
        val kids = setOf(VulnerableGroup.CHILDREN)
        assertEquals(6, score(trapped = true, vulnerable = kids))
        assertEquals(Severity.HIGH, level(trapped = true, vulnerable = kids))
        assertEquals(7, score(type = FireType.STRUCTURAL, trapped = true, vulnerable = kids))
        assertEquals(Severity.CRITICAL, level(type = FireType.STRUCTURAL, trapped = true, vulnerable = kids))
    }

    @Test fun largeStructuralFireWithPeople_isCritical() =
        assertEquals(Severity.CRITICAL, level(type = FireType.STRUCTURAL, size = FireSize.LARGE, people = 6))

    @Test fun everythingAtOnce_isTheMaximumOf12() {
        val total = score(
            type = FireType.STRUCTURAL, size = FireSize.LARGE, people = 10,
            vulnerable = VulnerableGroup.entries.toSet(), trapped = true
        )
        assertEquals(12, total)
        assertEquals(Severity.CRITICAL, SeverityCalculator.levelFor(total))
    }

    @Test fun calculate_returnsTheScoreAndTheMatchingLevel() {
        val r = SeverityCalculator.calculate(
            FireType.ELECTRICAL, FireSize.LARGE, 2, emptySet(), trapped = false
        )
        assertEquals(4, r.score) // large 3 + a few people 1
        assertEquals(Severity.HIGH, r.severity)
    }
}
