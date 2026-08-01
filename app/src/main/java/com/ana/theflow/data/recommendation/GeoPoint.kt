// Just a plain lat/lng pair we use across the recommendation engine, instead of pulling in a
// full maps SDK type everywhere.
package com.ana.theflow.data.recommendation

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    // Checks the coordinates are actually within real-world ranges - catches the classic bug
    // where an uninitialized point defaults to (0,0) and gets treated as a real location.
    fun isValid(): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}
