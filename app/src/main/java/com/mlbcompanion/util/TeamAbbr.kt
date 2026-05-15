package com.baseballnerd.app.util

/**
 * MLB team abbreviations keyed by team ID, matching the IDs in TeamColors.
 * Fallback: last word of team name, truncated to 3 chars, uppercased.
 */
object TeamAbbr {

    fun fromId(teamId: Int): String = byId[teamId] ?: "MLB"

    fun fromName(fullName: String): String {
        byName[fullName]?.let { return it }
        val parts = fullName.trim().split(" ")
        val last = parts.last()
        return when {
            last.equals("Sox", ignoreCase = true) -> "${parts[parts.size - 2].take(2)}${last.take(1)}".uppercase()
            last.equals("Jays", ignoreCase = true) -> "TOR"
            else -> last.take(3).uppercase()
        }
    }

    private val byId = mapOf(
        108 to "LAA",
        109 to "ARI",
        110 to "BAL",
        111 to "BOS",
        112 to "CHC",
        113 to "CIN",
        114 to "CLE",
        115 to "COL",
        116 to "DET",
        117 to "HOU",
        118 to "KC",
        119 to "LAD",
        120 to "WSH",
        121 to "NYM",
        133 to "ATH",
        134 to "PIT",
        135 to "SD",
        136 to "SEA",
        137 to "SF",
        138 to "STL",
        139 to "TB",
        140 to "TEX",
        141 to "TOR",
        142 to "MIN",
        143 to "PHI",
        144 to "ATL",
        145 to "CHW",
        146 to "MIA",
        147 to "NYY",
        158 to "MIL"
    )

    private val byName = mapOf(
        "Los Angeles Angels"       to "LAA",
        "Arizona Diamondbacks"     to "ARI",
        "Baltimore Orioles"        to "BAL",
        "Boston Red Sox"           to "BOS",
        "Chicago Cubs"             to "CHC",
        "Cincinnati Reds"          to "CIN",
        "Cleveland Guardians"      to "CLE",
        "Colorado Rockies"         to "COL",
        "Detroit Tigers"           to "DET",
        "Houston Astros"           to "HOU",
        "Kansas City Royals"       to "KC",
        "Los Angeles Dodgers"      to "LAD",
        "Washington Nationals"     to "WSH",
        "New York Mets"            to "NYM",
        "Oakland Athletics"        to "OAK",
        "Athletics"                to "ATH",
        "Pittsburgh Pirates"       to "PIT",
        "San Diego Padres"         to "SD",
        "Seattle Mariners"         to "SEA",
        "San Francisco Giants"     to "SF",
        "St. Louis Cardinals"      to "STL",
        "Tampa Bay Rays"           to "TB",
        "Texas Rangers"            to "TEX",
        "Toronto Blue Jays"        to "TOR",
        "Minnesota Twins"          to "MIN",
        "Philadelphia Phillies"    to "PHI",
        "Atlanta Braves"           to "ATL",
        "Chicago White Sox"        to "CHW",
        "Miami Marlins"            to "MIA",
        "New York Yankees"         to "NYY",
        "Milwaukee Brewers"        to "MIL"
    )
}
