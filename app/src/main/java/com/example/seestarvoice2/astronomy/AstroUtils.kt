package com.example.seestarvoice2.astronomy

import java.util.Calendar
import kotlin.math.*

data class AltAz(val altitude: Double, val azimuth: Double)

object AstroUtils {

    fun parseRA(raString: String): Double {
        return try {
            val parts = raString.split(":")
            val hours = parts[0].toDouble()
            val minutes = parts[1].toDouble()
            val seconds = parts[2].toDouble()
            (hours + minutes / 60.0 + seconds / 3600.0) * 15.0
        } catch (e: Exception) {
            0.0
        }
    }

    fun parseDec(decString: String): Double {
        return try {
            val isNegative = decString.startsWith("-")
            val cleanStr = decString.removePrefix("+").removePrefix("-")
            val parts = cleanStr.split(":")
            val degrees = parts[0].toDouble()
            val minutes = parts[1].toDouble()
            val seconds = parts[2].toDouble()
            val decimalDegrees = degrees + minutes / 60.0 + seconds / 3600.0
            if (isNegative) -decimalDegrees else decimalDegrees
        } catch (e: Exception) {
            0.0
        }
    }

    fun calculateAltAz(ra: Double, dec: Double, lat: Double, lon: Double, time: Calendar): AltAz {
        val raRad = Math.toRadians(ra)
        val decRad = Math.toRadians(dec)
        val latRad = Math.toRadians(lat)
        
        // Calculate Julian Date
        val jd = getJulianDate(time)
        val d = jd - 2451545.0
        
        // Mean Sidereal Time in degrees
        var lst = (280.46061837 + 360.98564736629 * d + lon) % 360
        if (lst < 0) lst += 360
        
        val lstRad = Math.toRadians(lst)
        
        // Hour Angle
        val haRad = lstRad - raRad
        
        // Formulas for Alt/Az
        val sinAlt = sin(decRad) * sin(latRad) + cos(decRad) * cos(latRad) * cos(haRad)
        val altRad = asin(sinAlt)
        
        val cosAz = (sin(decRad) - sin(altRad) * sin(latRad)) / (cos(altRad) * cos(latRad))
        var azRad = acos(max(-1.0, min(1.0, cosAz)))
        
        if (sin(haRad) > 0) {
            azRad = 2 * PI - azRad
        }
        
        return AltAz(Math.toDegrees(altRad), Math.toDegrees(azRad))
    }

    /**
     * Calculates approximate Sun coordinates.
     */
    fun getSunRaDec(time: Calendar): Pair<Double, Double> {
        val jd = getJulianDate(time)
        val d = jd - 2451545.0
        
        // Mean longitude of the Sun
        var l = (280.460 + 0.9856474 * d) % 360
        if (l < 0) l += 360
        
        // Mean anomaly of the Sun
        var g = (357.528 + 0.9856003 * d) % 360
        if (g < 0) g += 360
        
        // Ecliptic longitude
        val lambda = l + 1.915 * sin(Math.toRadians(g)) + 0.020 * sin(Math.toRadians(2 * g))
        
        // Obliquity of the ecliptic
        val epsilon = 23.439 - 0.0000004 * d
        
        val lambdaRad = Math.toRadians(lambda)
        val epsilonRad = Math.toRadians(epsilon)
        
        var ra = Math.toDegrees(atan2(cos(epsilonRad) * sin(lambdaRad), cos(lambdaRad)))
        if (ra < 0) ra += 360
        
        val dec = Math.toDegrees(asin(sin(epsilonRad) * sin(lambdaRad)))
        
        return Pair(ra, dec)
    }

    /**
     * Determines if an object is visible to the SeeStar telescope.
     */
    fun isVisibleToSeeStar(
        objRa: Double,
        objDec: Double,
        objType: String,
        objMag: Double?,
        lat: Double,
        lon: Double,
        time: Calendar,
        bortle: Int
    ): Pair<Boolean, String> {
        val objAltAz = calculateAltAz(objRa, objDec, lat, lon, time)
        if (objAltAz.altitude <= 0) return Pair(false, "below the horizon")
        
        val sunCoords = getSunRaDec(time)
        val sunAltAz = calculateAltAz(sunCoords.first, sunCoords.second, lat, lon, time)
        
        val isDaylight = sunAltAz.altitude > -6.0 // Civil twilight
        val isDark = sunAltAz.altitude < -12.0 // Nautical darkness
        
        val type = objType.uppercase()
        val isSpecial = type == "SUN" || type == "MOON" || type == "PLANET" || type == "STAR"
        
        if (type == "SUN") return Pair(true, "visible")
        if (type == "MOON") return Pair(true, "visible")
        
        if (isDaylight) {
            // During the day, only Sun and Moon are reliably visible to SeeStar.
            // Brightest planets like Venus/Jupiter might be visible but we'll be conservative.
            if (type == "PLANET" && (objMag ?: 10.0) < -3.0) return Pair(true, "visible but challenging due to daylight")
            return Pair(false, "drowned out by daylight")
        }
        
        // Check for Light Pollution / Brightness
        if (!isDark) {
            // Twilight - DSOs are difficult
            if (!isSpecial && (objMag ?: 10.0) > 6.0) return Pair(false, "not visible in twilight")
        }
        
        // Bortle scale limiting mag check (approximate for SeeStar)
        val limitingMag = 15.0 - (bortle * 0.5) // SeeStar is sensitive
        if ((objMag ?: 0.0) > limitingMag) {
            return Pair(false, "too faint for your light pollution level (Bortle $bortle)")
        }

        return Pair(true, "visible")
    }

    fun parseTimeSpec(timeSpec: String?): Calendar? {
        if (timeSpec == null) return null
        return try {
            val now = Calendar.getInstance()
            val time = timeSpec.lowercase().trim()
            
            // Handle "11 PM", "10:30 AM", etc.
            val regex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?")
            val match = regex.find(time) ?: return null
            
            var hours = match.groupValues[1].toInt()
            val minutes = if (match.groupValues[2].isNotEmpty()) match.groupValues[2].toInt() else 0
            val amPm = match.groupValues[3]
            
            if (amPm == "pm" && hours < 12) hours += 12
            if (amPm == "am" && hours == 12) hours = 0
            
            val result = now.clone() as Calendar
            result.set(Calendar.HOUR_OF_DAY, hours)
            result.set(Calendar.MINUTE, minutes)
            result.set(Calendar.SECOND, 0)
            
            // If the time is in the past for today, assume they mean the next occurrence (tomorrow)
            // unless it's very close. For simplicity, just use today's requested hour.
            
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun getJulianDate(calendar: Calendar): Double {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        
        var y = year.toDouble()
        var m = month.toDouble()
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100).toInt()
        val b = 2 - a + floor(a / 4.0).toInt()
        
        val jd = floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
        val dayFraction = (hour + minute / 60.0 + second / 3600.0) / 24.0
        
        return jd + dayFraction
    }
}
