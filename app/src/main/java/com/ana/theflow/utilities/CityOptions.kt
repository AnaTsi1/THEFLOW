package com.ana.theflow.utilities

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView

object CityOptions {

    data class CityOption(
        val id: String,
        val displayName: String,
        val latitude: Double,
        val longitude: Double,
        val aliases: List<String> = emptyList()
    )

    val cityOptions = listOf(
        CityOption("tel_aviv", "Tel Aviv", 32.0853, 34.7818, listOf("Tel-Aviv", "Tel Aviv-Yafo", "תל אביב", "תל אביב יפו")),
        CityOption("jerusalem", "Jerusalem", 31.7683, 35.2137, listOf("ירושלים")),
        CityOption("haifa", "Haifa", 32.7940, 34.9896, listOf("חיפה")),
        CityOption("ramat_gan", "Ramat Gan", 32.0684, 34.8248, listOf("רמת גן")),
        CityOption("beer_sheva", "Beer Sheva", 31.2529, 34.7915, listOf("Be'er Sheva", "Beersheba", "Beer-Sheva", "באר שבע")),
        CityOption("givatayim", "Givatayim", 32.0722, 34.8125, listOf("גבעתיים")),
        CityOption("holon", "Holon", 32.0158, 34.7874, listOf("חולון")),
        CityOption("bat_yam", "Bat Yam", 32.0132, 34.7480, listOf("בת ים")),
        CityOption("petah_tikva", "Petah Tikva", 32.0871, 34.8878, listOf("Petach Tikva", "פתח תקווה", "פתח תקוה")),
        CityOption("rishon_lezion", "Rishon LeZion", 31.9730, 34.7925, listOf("Rishon Le-Zion", "Rishon Lezion", "ראשון לציון")),
        CityOption("netanya", "Netanya", 32.3215, 34.8532, listOf("נתניה")),
        CityOption("ashdod", "Ashdod", 31.8044, 34.6553, listOf("אשדוד")),
        CityOption("ashkelon", "Ashkelon", 31.6688, 34.5743, listOf("אשקלון")),
        CityOption("herzliya", "Herzliya", 32.1663, 34.8433, listOf("Herzelia", "הרצליה")),
        CityOption("raanana", "Ra'anana", 32.1848, 34.8713, listOf("Raanana", "Ra'anana", "רעננה")),
        CityOption("kfar_saba", "Kfar Saba", 32.1750, 34.9069, listOf("Kfar-Saba", "כפר סבא")),
        CityOption("bnei_brak", "Bnei Brak", 32.0849, 34.8352, listOf("בני ברק")),
        CityOption("rehovot", "Rehovot", 31.8948, 34.8113, listOf("רחובות")),
        CityOption("modiin", "Modi'in", 31.8980, 35.0104, listOf("Modiin", "מודיעין")),
        CityOption("hadera", "Hadera", 32.4340, 34.9197, listOf("חדרה")),
        CityOption("nazareth", "Nazareth", 32.6996, 35.3035, listOf("נצרת")),
        CityOption("eilat", "Eilat", 29.5577, 34.9519, listOf("אילת"))
    )

    val israelCities = cityOptions.map { it.displayName }

    fun configureCitySelector(context: Context, view: AutoCompleteTextView) {
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, israelCities)
        view.setAdapter(adapter)
        view.threshold = 0
        view.setOnClickListener { view.showDropDown() }
        view.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) view.showDropDown()
        }
    }

    fun normalizeCity(value: String, defaultCity: String = "Tel Aviv"): String {
        return normalizeOptionalCity(value) ?: normalizeOptionalCity(defaultCity) ?: "Tel Aviv"
    }

    fun normalizeOptionalCity(value: String): String? {
        return cityFor(value)?.displayName
    }

    fun normalizeCityId(value: String): String? {
        return cityFor(value)?.id
    }

    // Best-effort: does a free-text address mention one of our known cities anywhere in it?
    // Used when approving a Google-sourced studio claim, where the only location text available
    // is a full formatted address (e.g. "12 Dizengoff St, Tel Aviv, Israel") rather than a clean
    // city field - cityFor() alone requires an exact match, which a full address never is.
    fun guessCityFromAddress(address: String): CityOption? {
        if (address.isBlank()) return null
        val normalized = normalizeKey(address)
        if (normalized.isBlank()) return null
        return cityOptions.firstOrNull { city ->
            val candidates = listOf(city.displayName) + city.aliases
            candidates.any { candidate ->
                val key = normalizeKey(candidate)
                key.isNotBlank() && normalized.contains(key)
            }
        }
    }

    fun cityFor(value: String): CityOption? {
        val normalized = normalizeKey(value)
        if (normalized.isBlank()) return null
        return cityOptions.firstOrNull { city ->
            city.id == normalized ||
                normalizeKey(city.displayName) == normalized ||
                city.aliases.any { normalizeKey(it) == normalized }
        }
    }

    fun displayNameFor(value: String): String {
        return cityFor(value)?.displayName ?: value.trim()
    }

    private fun normalizeKey(value: String): String {
        return value.trim()
            .lowercase()
            .replace(Regex("[׳’'`´]"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), "_")
            .trim('_')
    }
}
