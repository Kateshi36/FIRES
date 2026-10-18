package com.example.fires.util

import com.example.fires.data.model.FireSize
import com.example.fires.data.model.FireType
import com.example.fires.data.model.Severity
import com.example.fires.data.model.VulnerableGroup

/**
 * Automatic severity (D4). Turns the report form's answers into Low / Medium / High / Critical.
 * Pure Kotlin, so it runs as a plain JVM test.
 *
 * This is the PROPOSED scoring from the project plan, to be tuned with B-FLARE. Every number is a
 * named constant below, so tuning means changing this file and nothing else:
 *
 *   trapped                       +4
 *   vulnerable persons present    +2   (children, elderly, pregnant or PWD: any of them, once)
 *   people at risk                1 to 5: +1,  6 or more: +2
 *   fire size                     small 0,  medium +1,  large +3
 *   structural fire               +1
 *
 *   total 7 or more = Critical,  4 to 6 = High,  2 to 3 = Medium,  otherwise Low
 *
 * A responder can override the result later (severitySource = "responder"). This only sets the
 * first value, saved with severitySource = "auto".
 */
object SeverityCalculator {

    const val POINTS_TRAPPED = 4
    const val POINTS_VULNERABLE = 2
    const val POINTS_PEOPLE_FEW = 1      // 1 to PEOPLE_MANY_FROM - 1
    const val POINTS_PEOPLE_MANY = 2     // PEOPLE_MANY_FROM or more
    const val PEOPLE_MANY_FROM = 6
    const val POINTS_SIZE_MEDIUM = 1
    const val POINTS_SIZE_LARGE = 3
    const val POINTS_STRUCTURAL = 1

    const val CRITICAL_FROM = 7
    const val HIGH_FROM = 4
    const val MEDIUM_FROM = 2

    data class Result(val score: Int, val severity: Severity)

    fun score(
        fireType: FireType,
        fireSize: FireSize,
        peopleAtRisk: Int,
        vulnerable: Collection<VulnerableGroup>,
        trapped: Boolean
    ): Int {
        var total = 0
        if (trapped) total += POINTS_TRAPPED
        if (vulnerable.isNotEmpty()) total += POINTS_VULNERABLE
        total += when {
            peopleAtRisk >= PEOPLE_MANY_FROM -> POINTS_PEOPLE_MANY
            peopleAtRisk >= 1 -> POINTS_PEOPLE_FEW
            else -> 0
        }
        total += when (fireSize) {
            FireSize.SMALL -> 0
            FireSize.MEDIUM -> POINTS_SIZE_MEDIUM
            FireSize.LARGE -> POINTS_SIZE_LARGE
        }
        if (fireType == FireType.STRUCTURAL) total += POINTS_STRUCTURAL
        return total
    }

    fun levelFor(score: Int): Severity = when {
        score >= CRITICAL_FROM -> Severity.CRITICAL
        score >= HIGH_FROM -> Severity.HIGH
        score >= MEDIUM_FROM -> Severity.MEDIUM
        else -> Severity.LOW
    }

    fun calculate(
        fireType: FireType,
        fireSize: FireSize,
        peopleAtRisk: Int,
        vulnerable: Collection<VulnerableGroup>,
        trapped: Boolean
    ): Result {
        val total = score(fireType, fireSize, peopleAtRisk, vulnerable, trapped)
        return Result(total, levelFor(total))
    }
}
