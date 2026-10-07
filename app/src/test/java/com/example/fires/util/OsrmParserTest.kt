package com.example.fires.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OsrmParserTest {

    // A trimmed-down real-shaped OSRM answer: three points, three steps.
    private val sample = """
        {"code":"Ok","waypoints":[],"routes":[{
          "distance":2412.6,"duration":361.4,
          "geometry":{"type":"LineString","coordinates":[[120.98,14.59],[120.981,14.595],[120.9842,14.5995]]},
          "legs":[{"steps":[
            {"distance":1200.2,"name":"Rizal Street","maneuver":{"type":"depart","modifier":"north"}},
            {"distance":1212.4,"name":"Mabini Street","maneuver":{"type":"turn","modifier":"left"}},
            {"distance":0,"name":"","maneuver":{"type":"arrive"}}
          ]}]
        }]}
    """.trimIndent()

    @Test fun readsDistanceDurationAndLine() {
        val route = OsrmParser.parse(sample)
        assertNotNull(route)
        route!!
        assertEquals(2413, route.distanceMeters)
        assertEquals(361, route.durationSeconds)
        assertEquals(3, route.points.size)
        // GeoJSON is [longitude, latitude]; the app uses latitude first.
        assertEquals(14.59, route.points.first().latitude, 0.00001)
        assertEquals(120.98, route.points.first().longitude, 0.00001)
    }

    @Test fun readsTheSteps() {
        val steps = OsrmParser.parse(sample)!!.steps
        assertEquals(3, steps.size)
        assertEquals("Head out on Rizal Street", steps[0].instruction)
        assertEquals(1200, steps[0].distanceMeters)
        assertEquals("Turn left onto Mabini Street", steps[1].instruction)
        assertEquals("Arrive at the scene", steps[2].instruction)
    }

    @Test fun noRouteAnswerIsNull() {
        assertNull(OsrmParser.parse("""{"code":"NoRoute","message":"Impossible route"}"""))
    }

    @Test fun okWithoutRoutesIsNull() {
        assertNull(OsrmParser.parse("""{"code":"Ok","routes":[]}"""))
    }

    @Test fun brokenOrMissingPartsAreNullNotACrash() {
        assertNull(OsrmParser.parse("not json at all"))
        assertNull(OsrmParser.parse("""{"code":"Ok","routes":[{"distance":1,"duration":1}]}"""))
        assertNull(
            OsrmParser.parse(
                """{"code":"Ok","routes":[{"distance":1,"duration":1,"geometry":{"coordinates":[[120.9,14.5]]}}]}"""
            )
        )
    }
}
