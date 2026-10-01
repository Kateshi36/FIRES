package com.example.fires.util

import com.example.fires.data.model.FireSize
import com.example.fires.data.model.FireType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportValidatorsTest {

    private val goodText = "House on fire, smoke from the roof"

    @Test fun nothingFilled_everyRequiredFieldIsFlagged() {
        val errors = ReportValidators.validate(null, "", null, hasLocation = false)
        assertEquals(ReportValidators.MSG_TYPE_MISSING, errors.fireType)
        assertEquals(ReportValidators.MSG_DESCRIPTION_BLANK, errors.description)
        assertEquals(ReportValidators.MSG_SIZE_MISSING, errors.fireSize)
        assertEquals(ReportValidators.MSG_LOCATION_MISSING, errors.location)
        assertTrue(errors.hasErrors)
    }

    @Test fun everythingFilled_hasNoErrors() {
        val errors = ReportValidators.validate(FireType.STRUCTURAL, goodText, FireSize.MEDIUM, hasLocation = true)
        assertFalse(errors.hasErrors)
    }

    @Test fun description_blankAndWhitespace_isBlank() {
        assertEquals(ReportValidators.MSG_DESCRIPTION_BLANK, ReportValidators.description(""))
        assertEquals(ReportValidators.MSG_DESCRIPTION_BLANK, ReportValidators.description("     "))
    }

    @Test fun description_tooShort_isRejected() {
        assertEquals(ReportValidators.MSG_DESCRIPTION_SHORT, ReportValidators.description("Fire!"))
        // Spaces around the text do not count toward the length.
        assertEquals(ReportValidators.MSG_DESCRIPTION_SHORT, ReportValidators.description("   fire   "))
    }

    @Test fun description_exactlyMinimum_isAccepted() {
        assertNull(ReportValidators.description("a".repeat(ReportValidators.MIN_DESCRIPTION)))
        assertEquals(
            ReportValidators.MSG_DESCRIPTION_SHORT,
            ReportValidators.description("a".repeat(ReportValidators.MIN_DESCRIPTION - 1))
        )
    }

    @Test fun eachRequiredField_isCheckedOnItsOwn() {
        assertNull(ReportValidators.fireType(FireType.OTHER))
        assertNull(ReportValidators.fireSize(FireSize.SMALL))
        assertNull(ReportValidators.location(true))
        assertEquals(ReportValidators.MSG_LOCATION_MISSING, ReportValidators.location(false))
    }

    @Test fun onlyOneFieldMissing_onlyThatFieldIsFlagged() {
        val errors = ReportValidators.validate(FireType.VEHICLE, goodText, FireSize.LARGE, hasLocation = false)
        assertNull(errors.fireType)
        assertNull(errors.description)
        assertNull(errors.fireSize)
        assertEquals(ReportValidators.MSG_LOCATION_MISSING, errors.location)
        assertTrue(errors.hasErrors)
    }
}
