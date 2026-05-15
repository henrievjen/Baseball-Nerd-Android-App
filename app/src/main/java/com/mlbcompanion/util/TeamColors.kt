package com.baseballnerd.app.util

import android.graphics.Color

/**
 * MLB team primary colors and logo URLs.
 * Logo images served from MLB's official CDN — no API key needed.
 * Format: https://www.mlbstatic.com/team-logos/{teamId}.svg
 */
object TeamColors {

    /**
     * Returns the primary brand color hex for a given MLB team ID.
     */
    fun getPrimaryColor(teamId: Int): Int {
        return Color.parseColor(teamColorMap[teamId] ?: "#C8102E") // default: MLB red
    }

    fun getLogoUrl(teamId: Int): String {
        return "https://www.mlbstatic.com/team-logos/$teamId.svg"
    }

    private val teamColorMap = mapOf(
        108 to "#BA0021", // Angels
        109 to "#A71930", // D-backs
        110 to "#DF4601", // Orioles
        111 to "#BD3039", // Red Sox
        112 to "#0E3386", // Cubs
        113 to "#C6011F", // Reds
        114 to "#E31937", // Guardians
        115 to "#333366", // Rockies
        116 to "#0C2340", // Tigers
        117 to "#EB6E1F", // Astros
        118 to "#004687", // Royals
        119 to "#005A9C", // Dodgers
        120 to "#AB0003", // Nationals
        121 to "#002D72", // Mets
        133 to "#003831", // Athletics
        134 to "#FDB827", // Pirates
        135 to "#2F241D", // Padres
        136 to "#005C5C", // Mariners
        137 to "#FD5A1E", // Giants
        138 to "#C41E3A", // Cardinals
        139 to "#092C5C", // Rays
        140 to "#003278", // Rangers
        141 to "#134A8E", // Blue Jays
        142 to "#002B5C", // Twins
        143 to "#E81828", // Phillies
        144 to "#CE1141", // Braves
        145 to "#27251F", // White Sox
        146 to "#00A3E0", // Marlins
        147 to "#003087", // Yankees
        158 to "#12284B"  // Brewers
    )
}
