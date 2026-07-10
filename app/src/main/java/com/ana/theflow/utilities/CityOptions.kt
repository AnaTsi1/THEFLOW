package com.ana.theflow.utilities

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView

object CityOptions {

    val israelCities = listOf(
        "Tel Aviv",
        "Jerusalem",
        "Haifa",
        "Rishon LeZion",
        "Petah Tikva",
        "Ashdod",
        "Netanya",
        "Beer Sheva",
        "Bnei Brak",
        "Holon",
        "Ramat Gan",
        "Ashkelon",
        "Rehovot",
        "Bat Yam",
        "Herzliya",
        "Kfar Saba",
        "Ra'anana",
        "Modi'in",
        "Hadera",
        "Nazareth",
        "Eilat"
    )

    // Connects a city dropdown to an AutoCompleteTextView.
    fun configureCitySelector(context: Context, view: AutoCompleteTextView) {
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, israelCities)
        view.setAdapter(adapter)
        view.threshold = 0
        view.setOnClickListener {
            view.showDropDown()
        }
        view.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) view.showDropDown()
        }
    }

    // Returns a normalized city name or a default city.
    fun normalizeCity(value: String, defaultCity: String = "Tel Aviv"): String {
        return normalizeOptionalCity(value) ?: defaultCity
    }

    // Returns a normalized city name or null when the input is blank.
    fun normalizeOptionalCity(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null

        return israelCities.firstOrNull { city ->
            city.equals(trimmed, ignoreCase = true)
        }
    }
}
