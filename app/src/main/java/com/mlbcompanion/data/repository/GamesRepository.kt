package com.baseballnerd.app.data.repository

import com.baseballnerd.app.data.api.MlbApiService
import com.baseballnerd.app.data.api.MlbApiClient
import com.baseballnerd.app.data.model.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class GamesRepository(private val api: MlbApiService = MlbApiClient.service) {

    companion object {
        // Static memory cache for awards to persist across repository instances (fragment navigations)
        private val awardsCache = mutableMapOf<String, List<AwardsUiGroup>>()
    }

    suspend fun getGamesForDate(date: String): List<GameCardModel> {
        val response = api.getSchedule(date = date)
        val games = response.dates?.firstOrNull()?.games ?: emptyList()
        return mapGameSummariesToModels(games)
    }

    suspend fun getTodaysGames(): List<GameCardModel> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = sdf.format(Date())
        return getGamesForDate(today)
    }

    suspend fun getTeamSchedule(teamId: Int, year: Int): List<GameCardModel> {
        val response = api.getSchedule(
            teamId = teamId,
            startDate = "$year-01-01",
            endDate = "$year-12-31"
        )
        val allGames = response.dates?.flatMap { it.games ?: emptyList() } ?: emptyList()
        return mapGameSummariesToModels(allGames)
    }

    private fun mapGameSummariesToModels(games: List<GameSummary>): List<GameCardModel> {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return games.map { summary ->
            var status = summary.status?.abstractGameState ?: "Unknown"
            val detailed = summary.status?.detailedState ?: ""
            val linescore = summary.linescore

            val gameDateStr = summary.gameDate ?: ""
            // Force status to Final for historical games
            if (status != "Live" && gameDateStr.length >= 4) {
                val gameYear = gameDateStr.take(4).toIntOrNull() ?: currentYear
                if (gameYear < currentYear) {
                    status = "Final"
                }
            }

            val inning = buildInningOrdinal(
                status, detailed, linescore,
                summary.gameDate ?: "", summary.resumeDate,
                summary.doubleHeader, summary.gameNumber,
                summary.status?.startTimeTBD == true
            )

            val awayTeam = summary.teams?.away
            val homeTeam = summary.teams?.home

            val currentInning = linescore?.currentInning ?: 0
            val inningHalf = linescore?.inningHalf ?: ""
            val awayHits = linescore?.teams?.away?.hits ?: 0
            val homeHits = linescore?.teams?.home?.hits ?: 0
            val awayErrors = linescore?.teams?.away?.errors ?: 0
            val homeErrors = linescore?.teams?.home?.errors ?: 0

            // Treat Delayed games as non-final even if abstract status says Final
            val isFinal = status == "Final" && !detailed.startsWith("Delayed", ignoreCase = true)

            GameCardModel(
                gamePk = summary.gamePk,
                awayTeamId = awayTeam?.team?.id ?: 0,
                homeTeamId = homeTeam?.team?.id ?: 0,
                awayTeamName = awayTeam?.team?.name ?: "Away",
                homeTeamName = homeTeam?.team?.name ?: "Home",
                awayScore = awayTeam?.score ?: 0,
                homeScore = homeTeam?.score ?: 0,
                awayHits = awayHits,
                homeHits = homeHits,
                awayErrors = awayErrors,
                homeErrors = homeErrors,
                gameState = status,
                detailedState = detailed,
                inningOrdinal = inning,
                currentInning = currentInning,
                inningHalf = inningHalf,
                balls = linescore?.balls ?: 0,
                strikes = linescore?.strikes ?: 0,
                outs = linescore?.outs ?: 0,
                runnerOnFirst = linescore?.offense?.first != null,
                runnerOnSecond = linescore?.offense?.second != null,
                runnerOnThird = linescore?.offense?.third != null,
                gameDate = summary.gameDate ?: "",
                resumeDate = summary.resumeDate,
                awayRecord = awayTeam?.leagueRecord?.let { "${it.wins}-${it.losses}" },
                homeRecord = homeTeam?.leagueRecord?.let { "${it.wins}-${it.losses}" },
                doubleHeader = summary.doubleHeader,
                gameNumber = summary.gameNumber,
                isStartTimeTBD = summary.status?.startTimeTBD == true,
                awayStarter = null,
                homeStarter = null,
                venueName = summary.venue?.name,
                venueTimeZone = summary.venue?.timeZone?.tz,
                // Away no-hitter: away pitchers held home batters hitless (homeHits == 0)
                // Home no-hitter: home pitchers held away batters hitless (awayHits == 0)
                awayNoHitter = checkNoHitter(homeHits, currentInning, inningHalf, true, isFinal),
                homeNoHitter = checkNoHitter(awayHits, currentInning, inningHalf, false, isFinal),
                // BB/HBP not available from schedule linescore — bbKnown=false, only confirms final
                awayPerfectGame = checkPerfectGame(homeHits, 0, 0, homeErrors, currentInning, inningHalf, true, isFinal, bbKnown = false),
                homePerfectGame = checkPerfectGame(awayHits, 0, 0, awayErrors, currentInning, inningHalf, false, isFinal, bbKnown = false),
                gameType = summary.gameType,
                seriesDescription = summary.seriesDescription,
                seriesGameNumber = summary.seriesGameNumber,
                gamesInSeries = summary.gamesInSeries,
                broadcasts = summary.broadcasts
            )
        }
    }

    // A no-hitter: the pitching team has allowed zero hits.
    // - Walks and HBPs are allowed (they don't break a no-hitter).
    // - For a completed game: requires zero hits through at least 9 innings.
    // - For a live game: flag after 6+ complete innings so it's meaningful but not constant noise.
    //   "Complete" means: if pitching team is Away (pitching top half), inning must be > threshold;
    //   if pitching team is Home (pitching bottom half), current inning >= threshold and half == "bottom"
    //   or inning > threshold.
    // - pitchingTeamHitsAllowed: hits recorded by the BATTING team (home hits = away pitchers' stat).
    private fun checkNoHitter(
        pitchingTeamHitsAllowed: Int,
        inning: Int,
        half: String,
        awayTeamIsPitching: Boolean,
        isFinal: Boolean
    ): Boolean {
        if (pitchingTeamHitsAllowed > 0) return false
        if (isFinal) return inning >= 9

        // Live game — require enough innings to be meaningful
        val threshold = 6
        return if (awayTeamIsPitching) {
            // Away pitching = top half innings; complete when we move past the top
            inning > threshold || (inning == threshold && half.equals("bottom", ignoreCase = true))
        } else {
            // Home pitching = bottom half innings; complete when inning advances
            inning > threshold
        }
    }

    // A perfect game: the pitching team has allowed NO baserunners of any kind —
    // no hits, no walks (BB), no hit-by-pitches (HBP), no reaching on errors.
    // bbKnown=false means BB/HBP data wasn't available (scores card path) —
    // in that case only confirm for final games where hits+errors=0.
    private fun checkPerfectGame(
        pitchingTeamHitsAllowed: Int,
        bb: Int,
        hbp: Int,
        errors: Int,
        inning: Int,
        half: String,
        awayTeamIsPitching: Boolean,
        isFinal: Boolean,
        bbKnown: Boolean = true
    ): Boolean {
        if (pitchingTeamHitsAllowed > 0 || errors > 0) return false
        if (bbKnown && (bb > 0 || hbp > 0)) return false
        // Without BB/HBP data, only confirm as final (can't safely flag live)
        if (!bbKnown) return isFinal && inning >= 9
        if (isFinal) return inning >= 9

        val threshold = 6
        return if (awayTeamIsPitching) {
            inning > threshold || (inning == threshold && half.equals("bottom", ignoreCase = true))
        } else {
            inning > threshold
        }
    }

    suspend fun getLiveAndUpcomingGameSummaries(): List<GameSummary> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = sdf.format(Date())
        val response = api.getSchedule(date = today)
        return response.dates?.firstOrNull()?.games ?: emptyList()
    }

    suspend fun getGameDetail(gamePk: Long, date: String? = null): GameCardModel? {
        val boxscore = api.getBoxscore(gamePk)
        val linescore = api.getLinescore(gamePk)

        val homeData = boxscore.teams?.home ?: return null
        val awayData = boxscore.teams?.away ?: return null

        // Fetch schedule to get status and fallback starter info
        val dateToUse = if (date?.contains("T") == true) date.substring(0, 10) else date
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        // Use gamePks and date filters to ensure we get the correct instance of the game.
        // Postponed games may appear on multiple dates (original and rescheduled).
        val scheduleResp = runCatching {
            api.getSchedule(
                date = dateToUse,
                gamePks = gamePk.toString(),
                hydrate = "broadcasts,venue(timeZone),probablePitcher(stats(group=[pitching],type=[season],gameType=R))"
            )
        }.getOrNull()

        // Find the summary that matches the gamePk AND is on the requested date.
        // This avoids picking up a "Postponed" entry from a previous date if multiple instances are returned.
        val summary = scheduleResp?.dates?.find { it.date == dateToUse }?.games?.find { it.gamePk == gamePk }
            ?: scheduleResp?.dates?.flatMap { it.games ?: emptyList() }?.find { it.gamePk == gamePk }

        var status = summary?.status?.abstractGameState ?: ""
        val detailed = summary?.status?.detailedState ?: ""

        // Fallback for historical games: if the game is in the past (year-wise) and not Live, mark as Final.
        // This fixes an issue where old games sometimes return "Preview" or "Scheduled" status.
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val gameYear = dateToUse.take(4).toIntOrNull() ?: currentYear
        if (gameYear < currentYear && status != "Live") {
            status = "Final"
        }

        val inningOrdinal = buildInningOrdinal(
            status, detailed, linescore,
            summary?.gameDate ?: "", summary?.resumeDate,
            summary?.doubleHeader, summary?.gameNumber,
            summary?.status?.startTimeTBD == true
        )

        // If delayed, we don't treat it as "Final" even if status says so
        val isFinal = status == "Final" && !detailed.startsWith("Delayed", ignoreCase = true)
        val isGameLiveOrFinal = status == "Live" || isFinal
        val isPreview = !isGameLiveOrFinal

        val awayLineup = buildLineup(awayData, awayData.team?.name ?: "Away", awayData.team?.id ?: 0, onlyPitched = isGameLiveOrFinal, onlyStarter = isPreview)
        val homeLineup = buildLineup(homeData, homeData.team?.name ?: "Home", homeData.team?.id ?: 0, onlyPitched = isGameLiveOrFinal, onlyStarter = isPreview)

        // Try boxscore first
        var awayStarter = awayData.probablePitcher?.let { buildStarterModel(it, boxscore, "away") }
        // Fallback to schedule summary if boxscore has no stats or generic name
        if (awayStarter == null || awayStarter.era == "-.--") {
            summary?.teams?.away?.probablePitcher?.let { p ->
                val container = p.stats?.find {
                    it.group?.displayName?.lowercase() == "pitching" &&
                            it.type?.displayName?.equals("season", ignoreCase = true) == true
                }
                val s = container?.stats?.pitching
                val split = container?.splits?.firstOrNull()?.stat

                awayStarter = StarterModel(
                    id = p.id ?: 0,
                    name = p.fullName ?: "TBD",
                    number = p.primaryNumber,
                    hand = p.pitchHand?.code,
                    era = s?.era ?: split?.era ?: "-.--",
                    record = if ((s?.wins ?: split?.wins) != null && (s?.losses ?: split?.losses) != null)
                        "${s?.wins ?: split?.wins}-${s?.losses ?: split?.losses}" else null,
                    strikeouts = s?.strikeOuts ?: split?.strikeOuts
                )
            }
        }

        var homeStarter = homeData.probablePitcher?.let { buildStarterModel(it, boxscore, "home") }
        if (homeStarter == null || homeStarter.era == "-.--") {
            summary?.teams?.home?.probablePitcher?.let { p ->
                val container = p.stats?.find {
                    it.group?.displayName?.lowercase() == "pitching" &&
                            it.type?.displayName?.equals("season", ignoreCase = true) == true
                }
                val s = container?.stats?.pitching
                val split = container?.splits?.firstOrNull()?.stat

                homeStarter = StarterModel(
                    id = p.id ?: 0,
                    name = p.fullName ?: "TBD",
                    number = p.primaryNumber,
                    hand = p.pitchHand?.code,
                    era = s?.era ?: split?.era ?: "-.--",
                    record = if ((s?.wins ?: split?.wins) != null && (s?.losses ?: split?.losses) != null)
                        "${s?.wins ?: split?.wins}-${s?.losses ?: split?.losses}" else null,
                    strikeouts = s?.strikeOuts ?: split?.strikeOuts
                )
            }
        }

        // If starters still missing stats, try fetching person directly
        suspend fun enhanceStarter(starter: StarterModel?): StarterModel? {
            if (starter == null || starter.id == 0) return starter
            if (starter.era != "-.--" && starter.record != null) return starter

            // Use the dedicated regular-season-only stats fetch for accurate ERA/W-L
            val person = getStarterSeasonStats(starter.id, dateToUse.take(4)) ?: return starter
            val container = person.stats?.find {
                it.group?.displayName?.lowercase() == "pitching" &&
                        it.type?.displayName?.equals("season", ignoreCase = true) == true
            }
            val s = container?.stats?.pitching
            val split = container?.splits?.firstOrNull()?.stat

            return starter.copy(
                number = person.primaryNumber ?: starter.number,
                hand = person.pitchHand?.code ?: starter.hand,
                era = s?.era ?: split?.era ?: starter.era,
                record = if ((s?.wins ?: split?.wins) != null && (s?.losses ?: split?.losses) != null)
                    "${s?.wins ?: split?.wins}-${s?.losses ?: split?.losses}" else starter.record,
                strikeouts = s?.strikeOuts ?: split?.strikeOuts ?: starter.strikeouts
            )
        }

        if (status == "Preview") {
            awayStarter = enhanceStarter(awayStarter)
            homeStarter = enhanceStarter(homeStarter)
        }

        // Fetch plays for the game
        val plays = getGamePlays(gamePk)

        // ── Bullpen Usage Fetching ───────────────────────────────────────────

        suspend fun getBullpenUsage(teamData: BoxscoreTeamData, teamId: Int): List<BullpenUsageModel> {
            // Get pitchers from boxscore (if already in game) or active roster (for upcoming)
            val boxscorePitchers = teamData.players?.values?.filter { it.position?.code == "1" }?.mapNotNull { it.person } ?: emptyList()
            val pitchers = if (boxscorePitchers.size <= 1) {
                runCatching { api.getTeamRoster(teamId) }.getOrNull()?.roster
                    ?.filter { it.position?.code == "1" }
                    ?.mapNotNull { it.person } ?: boxscorePitchers
            } else {
                boxscorePitchers
            }

            val starterId = teamData.probablePitcher?.id ?: summary?.teams?.away?.probablePitcher?.id ?: summary?.teams?.home?.probablePitcher?.id ?: 0
            val bullpenPitchers = pitchers.filter { it.id != starterId }

            if (bullpenPitchers.isEmpty()) return emptyList()

            // Calculate the 3 days relative to dateToUse: [D-3, D-2, D-1]
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance().apply {
                time = sdf.parse(dateToUse) ?: Date()
            }
            val targetDates = mutableListOf<String>()
            val displayDates = mutableListOf<String>()
            val sdfDisplay = SimpleDateFormat("MM/dd", Locale.US)

            cal.add(Calendar.DAY_OF_YEAR, -3)
            for (i in 0..2) {
                targetDates.add(sdf.format(cal.time))
                displayDates.add(sdfDisplay.format(cal.time))
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            val personIds = bullpenPitchers.mapNotNull { it.id }.distinct()
            val year = dateToUse.take(4)
            val hydrate = "stats(group=[pitching],type=[season,gameLog],season=$year)"

            val people = runCatching { api.getPeople(personIds.joinToString(","), hydrate) }.getOrNull()?.people ?: emptyList()

            return people.filter { person ->
                val seasonContainer = person.stats?.find { it.type?.displayName?.lowercase() == "season" }
                val totalGamesStarted = seasonContainer?.splits?.sumOf { it.stat?.gamesStarted ?: 0 } ?: 0
                totalGamesStarted <= 1
            }.map { person ->
                val logContainer = person.stats?.find { it.type?.displayName == "gameLog" }
                val splits = logContainer?.splits ?: emptyList()

                val pitchesByDate = splits.filter { it.gameDate != null || it.date != null }
                    .groupBy { it.gameDate?.take(10) ?: it.date ?: "" }
                    .mapValues { entry -> entry.value.sumOf { it.stat?.numberOfPitches ?: 0 } }

                val usage = targetDates.mapIndexed { index, dateStr ->
                    PitchCountDay(displayDates[index], pitchesByDate[dateStr] ?: 0)
                }

                BullpenUsageModel(
                    pitcherName = person.fullName ?: "Unknown",
                    pitcherId = person.id ?: 0,
                    usage = usage
                )
            }.sortedByDescending { it.usage.sumOf { day -> day.count } }
        }

        val awayBullpen = getBullpenUsage(awayData, awayData.team?.id ?: 0)
        val homeBullpen = getBullpenUsage(homeData, homeData.team?.id ?: 0)

        // Extract game info from boxscore/linescore
        val venue = summary?.venue?.name
        val venueTimeZone = summary?.venue?.timeZone?.tz

        val weather = boxscore.info?.find { it.label?.equals("Weather", true) == true }?.value
        val wind = boxscore.info?.find { it.label?.equals("Wind", true) == true }?.value
        val attendance = boxscore.info?.find { it.label?.equals("Att", true) == true }?.value
        val firstPitch = boxscore.info?.find { it.label?.equals("First pitch", true) == true }?.value
        val gameDuration = boxscore.info?.find { it.label?.equals("T", true) == true }?.value

        return GameCardModel(
            gamePk = gamePk,
            awayTeamId = awayData.team?.id ?: 0,
            homeTeamId = homeData.team?.id ?: 0,
            awayTeamName = awayData.team?.name ?: "Away",
            homeTeamName = summary?.teams?.home?.team?.name ?: homeData.team?.name ?: "Home",
            awayScore = linescore.teams?.away?.runs ?: 0,
            homeScore = linescore.teams?.home?.runs ?: 0,
            awayHits = linescore.teams?.away?.hits ?: 0,
            homeHits = linescore.teams?.home?.hits ?: 0,
            awayErrors = linescore.teams?.away?.errors ?: 0,
            homeErrors = linescore.teams?.home?.errors ?: 0,
            gameState = status,
            detailedState = detailed,
            inningOrdinal = inningOrdinal,
            currentInning = linescore.currentInning ?: 0,
            inningHalf = linescore.inningHalf ?: "",
            balls = linescore.balls ?: 0,
            strikes = linescore.strikes ?: 0,
            outs = linescore.outs ?: 0,
            runnerOnFirst = linescore.offense?.first != null,
            runnerOnSecond = linescore.offense?.second != null,
            runnerOnThird = linescore.offense?.third != null,
            currentPitcher = buildPitcherModel(linescore, boxscore),
            currentBatter = buildBatterModel(linescore, boxscore),
            currentAtBatPitches = plays.lastOrNull()?.pitches ?: emptyList(),
            gameDate = summary?.gameDate ?: "",
            resumeDate = summary?.resumeDate,
            linescore = buildLinescoreModel(linescore, isFinal),
            awayLineup = awayLineup,
            homeLineup = homeLineup,
            plays = plays,
            awayStarter = awayStarter,
            homeStarter = homeStarter,
            awayRecord = summary?.teams?.away?.leagueRecord?.let { "${it.wins}-${it.losses}" },
            homeRecord = summary?.teams?.home?.leagueRecord?.let { "${it.wins}-${it.losses}" },
            venueName = venue,
            venueTimeZone = venueTimeZone,
            weather = weather,
            wind = wind,
            attendance = attendance,
            firstPitch = firstPitch,
            gameDuration = gameDuration,
            doubleHeader = summary?.doubleHeader,
            gameNumber = summary?.gameNumber,
            isStartTimeTBD = summary?.status?.startTimeTBD == true,
            awayBullpenUsage = awayBullpen,
            homeBullpenUsage = homeBullpen,
            // Away no-hitter: away pitchers held home batters hitless
            // Home no-hitter: home pitchers held away batters hitless
            awayNoHitter = checkNoHitter(linescore.teams?.home?.hits ?: 0, linescore.currentInning ?: 0, linescore.inningHalf ?: "", true, isFinal),
            homeNoHitter = checkNoHitter(linescore.teams?.away?.hits ?: 0, linescore.currentInning ?: 0, linescore.inningHalf ?: "", false, isFinal),
            // Perfect game: use real BB from pitching team stats and HBP from batting team stats
            // Away perfect game = home pitchers' BB + away batters' HBP + home errors all zero
            awayPerfectGame = checkPerfectGame(
                pitchingTeamHitsAllowed = linescore.teams?.home?.hits ?: 0,
                bb = homeData.teamStats?.pitching?.baseOnBalls ?: 0,
                hbp = awayData.teamStats?.batting?.hitByPitch ?: 0,
                errors = linescore.teams?.home?.errors ?: 0,
                inning = linescore.currentInning ?: 0,
                half = linescore.inningHalf ?: "",
                awayTeamIsPitching = true,
                isFinal = isFinal,
                bbKnown = true
            ),
            homePerfectGame = checkPerfectGame(
                pitchingTeamHitsAllowed = linescore.teams?.away?.hits ?: 0,
                bb = awayData.teamStats?.pitching?.baseOnBalls ?: 0,
                hbp = homeData.teamStats?.batting?.hitByPitch ?: 0,
                errors = linescore.teams?.away?.errors ?: 0,
                inning = linescore.currentInning ?: 0,
                half = linescore.inningHalf ?: "",
                awayTeamIsPitching = false,
                isFinal = isFinal,
                bbKnown = true
            ),
            gameType = summary?.gameType,
            seriesDescription = summary?.seriesDescription,
            seriesGameNumber = summary?.seriesGameNumber,
            gamesInSeries = summary?.gamesInSeries,
            broadcasts = summary?.broadcasts
        )
    }

    private fun buildStarterModel(p: PlayerReference, box: BoxscoreResponse, side: String): StarterModel? {
        val pid = p.id ?: return null
        val teamData = if (side == "home") box.teams?.home else box.teams?.away
        val player = teamData?.players?.get("ID$pid") ?: return null

        // Look for season pitching stats in the person object if available
        val container = player.person?.stats?.find {
            it.group?.displayName?.lowercase() == "pitching" &&
                    it.type?.displayName?.equals("season", ignoreCase = true) == true
        }
        val s = container?.stats?.pitching
        val split = container?.splits?.firstOrNull()?.stat

        return StarterModel(
            id = pid,
            name = player.person?.fullName ?: "TBD",
            number = player.person?.primaryNumber,
            hand = player.person?.pitchHand?.code,
            era = s?.era ?: split?.era ?: "-.--",
            record = if ((s?.wins ?: split?.wins) != null && (s?.losses ?: split?.losses) != null)
                "${s?.wins ?: split?.wins}-${s?.losses ?: split?.losses}" else null,
            strikeouts = s?.strikeOuts ?: split?.strikeOuts
        )
    }

    private fun buildLineup(
        teamData: BoxscoreTeamData,
        teamName: String,
        teamId: Int,
        onlyPitched: Boolean = false,
        onlyStarter: Boolean = false
    ): TeamLineup {
        val playersMap = teamData.players ?: emptyMap()

        // Collect all players who have been in the game or are part of the initial batting order.
        val allPlayers = playersMap.values.filter { it.battingOrder != null }
            .sortedBy { it.battingOrder?.toIntOrNull() ?: 0 }

        val lineupBatters = allPlayers.mapNotNull { p ->
            val id = p.person?.id ?: return@mapNotNull null
            val s = p.stats?.batting
            val rawOrder = p.battingOrder?.toIntOrNull() ?: 0
            val isSubstitution = rawOrder % 100 != 0

            val order = rawOrder / 100

            // Look for season stats in person object
            val seasonContainer = p.person.stats?.find {
                it.group?.displayName?.lowercase() == "hitting" &&
                        it.type?.displayName?.lowercase() == "season"
            }
            val seasonStats = seasonContainer?.stats?.batting

            LineupBatter(
                id = id,
                battingOrder = order,
                name = p.person.fullName ?: "Unknown",
                position = p.position?.abbreviation ?: "" ,
                ab = s?.atBats ?: 0,
                r = s?.runs ?: 0,
                h = s?.hits ?: 0,
                rbi = s?.rbi ?: 0,
                bb = s?.baseOnBalls ?: 0,
                k = s?.strikeOuts ?: 0,
                hr = s?.homeRuns ?: 0,
                gameHAb = "${s?.hits ?: 0}-${s?.atBats ?: 0}",
                seasonAvg = seasonStats?.avg ?: p.person.stats?.firstOrNull()?.splits?.firstOrNull()?.stat?.avg ?: s?.avg,
                seasonObp = seasonStats?.obp ?: p.person.stats?.firstOrNull()?.splits?.firstOrNull()?.stat?.obp ?: s?.obp,
                seasonSlg = seasonStats?.slg ?: p.person.stats?.firstOrNull()?.splits?.firstOrNull()?.stat?.slg ?: s?.slg,
                seasonOps = seasonStats?.ops ?: p.person.stats?.firstOrNull()?.splits?.firstOrNull()?.stat?.ops ?: s?.ops,
                isSubstitution = isSubstitution
            )
        }

        // Pitchers
        var pitcherIds = teamData.pitchers ?: emptyList()
        if (onlyStarter) pitcherIds = pitcherIds.take(1)

        val lineupPitchers = pitcherIds.mapNotNull { id ->
            val p = playersMap["ID$id"] ?: return@mapNotNull null
            val s = p.stats?.pitching
            if (onlyPitched && (s?.inningsPitched == null || s.inningsPitched == "0.0")) return@mapNotNull null

            val seasonContainer = p.person?.stats?.find {
                it.group?.displayName?.lowercase() == "pitching" &&
                        it.type?.displayName?.lowercase() == "season"
            }
            val seasonStats = seasonContainer?.stats?.pitching

            LineupPitcher(
                id = id,
                name = p.person?.fullName ?: "Unknown",
                ip = s?.inningsPitched ?: "0.0",
                h = s?.hits ?: 0,
                r = s?.runs ?: 0,
                er = s?.earnedRuns ?: 0,
                bb = s?.baseOnBalls ?: 0,
                k = s?.strikeOuts ?: 0,
                hr = s?.homeRuns ?: 0,
                pitches = s?.numberOfPitches ?: 0,
                strikes = s?.strikes ?: 0,
                balls = (s?.numberOfPitches ?: 0) - (s?.strikes ?: 0),
                era = s?.era ?: "-.--",
                seasonEra = seasonStats?.era ?: p.person?.stats?.firstOrNull()?.splits?.firstOrNull()?.stat?.era ?: s?.era ?: "-.--",
                seasonWhip = seasonStats?.whip ?: p.person?.stats?.firstOrNull()?.splits?.firstOrNull()?.stat?.whip ?: s?.whip
            )
        }

        return TeamLineup(
            teamName = teamName,
            teamId = teamId,
            batters = lineupBatters,
            pitchers = lineupPitchers
        )
    }

    private suspend fun getStarterSeasonStats(personId: Int, year: String): BoxscorePerson? {
        val hydrate = "stats(group=[pitching],type=[season],season=$year)"
        return runCatching {
            api.getPerson(personId, hydrate)
        }.getOrNull()?.people?.firstOrNull()
    }

    private fun buildInningOrdinal(
        status: String,
        detailed: String,
        line: LinescoreResponse?,
        date: String,
        resumeDate: String?,
        doubleHeader: String?,
        gameNumber: Int?,
        isTBD: Boolean
    ): String {
        // Priority check for Delay and other states over abstract status
        if (detailed.startsWith("Delayed", ignoreCase = true)) return "Delayed"
        if (detailed.equals("Postponed", ignoreCase = true)) return "Postponed"
        if (detailed.equals("Suspended", ignoreCase = true)) {
            val base = "Suspended"
            return if (resumeDate != null) "$base (Resumes $resumeDate)" else base
        }
        if (detailed.equals("Cancelled", ignoreCase = true)) return "Cancelled"

        if (status == "Final") {
            val innings = line?.currentInning ?: 9
            return if (innings > 9) "Final/$innings" else "Final"
        }
        if (status == "Live") {
            val inn = line?.currentInningOrdinal ?: ""
            val half = line?.inningHalf ?: ""
            return "$half $inn"
        }

        // Handle doubleheaders
        val dhSuffix = if (doubleHeader == "Y" || doubleHeader == "S") " (Game $gameNumber)" else ""

        // Preview / Scheduled: format the time
        if (isTBD) return "TBD$dhSuffix"
        return try {
            val inputSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            inputSdf.timeZone = TimeZone.getTimeZone("UTC")
            val dateObj = inputSdf.parse(date)
            val outputSdf = SimpleDateFormat("h:mm a", Locale.US)
            outputSdf.timeZone = TimeZone.getDefault()
            outputSdf.format(dateObj!!) + dhSuffix
        } catch (e: Exception) {
            "Scheduled$dhSuffix"
        }
    }

    private fun buildPitcherModel(line: LinescoreResponse, box: BoxscoreResponse): PitcherModel? {
        val pid = line.defense?.pitcher?.id ?: return null
        // Find player in boxscore to get game stats
        val player = box.teams?.home?.players?.get("ID$pid")
            ?: box.teams?.away?.players?.get("ID$pid")
            ?: return null

        val s = player.stats?.pitching ?: return null

        // Get season stats from person hydrate if available
        val seasonContainer = player.person?.stats?.find {
            it.group?.displayName?.lowercase() == "pitching" &&
                    it.type?.displayName?.equals("season", ignoreCase = true) == true
        }
        val s2 = seasonContainer?.stats?.pitching
        val split = seasonContainer?.splits?.firstOrNull()?.stat

        return PitcherModel(
            id = pid,
            name = player.person?.fullName ?: "Unknown",
            ip = s.inningsPitched ?: "0.0",
            er = s.earnedRuns ?: 0,
            k = s.strikeOuts ?: 0,
            bb = s.baseOnBalls ?: 0,
            seasonEra = s2?.era ?: split?.era ?: player.person?.stats?.firstOrNull()?.splits?.firstOrNull()?.stat?.era ?: s.era ?: "-.--"
        )
    }

    private fun buildBatterModel(line: LinescoreResponse, box: BoxscoreResponse): BatterModel? {
        val pid = line.offense?.batter?.id ?: return null
        val player = box.teams?.home?.players?.get("ID$pid")
            ?: box.teams?.away?.players?.get("ID$pid")
            ?: return null

        val s = player.stats?.batting ?: return null

        return BatterModel(
            id = pid,
            name = player.person?.fullName ?: "Unknown",
            hits = s.hits ?: 0,
            atBats = s.atBats ?: 0
        )
    }

    suspend fun getGamePlays(gamePk: Long): List<PlaySummary> {
        val pbp = runCatching { api.getPlayByPlay(gamePk) }.getOrNull() ?: return emptyList()
        val scoringPlayIndices = pbp.scoringPlays?.toSet() ?: emptySet()
        return pbp.allPlays?.map { play ->
            val result = play.result
            val about = play.about
            val matchup = play.matchup
            val atBatIdx = about?.atBatIndex ?: 0

            PlaySummary(
                atBatIndex = atBatIdx,
                batterName = matchup?.batter?.fullName ?: "Unknown",
                batterId = matchup?.batter?.id,
                pitcherName = matchup?.pitcher?.fullName,
                pitcherId = matchup?.pitcher?.id,
                description = result?.description ?: "",
                inning = about?.inning ?: 0,
                halfInning = about?.halfInning ?: "",
                isScoringPlay = result?.isScoringPlay ?: scoringPlayIndices.contains(atBatIdx),
                awayScore = result?.awayScore ?: 0,
                homeScore = result?.homeScore ?: 0,
                pitches = play.playEvents?.filter { it.isPitch == true }?.map { event ->
                    val d = event.details
                    val p = event.pitchData
                    PitchDetail(
                        pitchNumber = event.pitchNumber ?: 0,
                        pitchType = d?.pitchType?.description ?: "Unknown",
                        pitchTypeCode = d?.pitchType?.code ?: "",
                        speed = if (p?.startSpeed != null) "${p.startSpeed}" else "--",
                        callDescription = d?.call?.description ?: "",
                        callCode = d?.call?.code ?: "",
                        px = p?.coordinates?.px,
                        pz = p?.coordinates?.pz,
                        szTop = p?.strikeZoneTop,
                        szBottom = p?.strikeZoneBottom
                    )
                } ?: emptyList()
            )
        } ?: emptyList()
    }

    suspend fun getStandings(season: String): Pair<List<StandingsDivision>, List<WildCardEntry>> {
        val response = api.getStandings(leagueId = "103,104", season = season)
        val records = response.records ?: return Pair(emptyList(), emptyList())

        val divisions = records.map { rec ->
            StandingsDivision(
                divisionName = rec.division?.name ?: "Unknown",
                league = rec.league?.name ?: "",
                teams = rec.teamRecords?.map { tr ->
                    StandingsRecord(
                        teamId = tr.team?.id ?: 0,
                        teamName = tr.team?.name ?: "Unknown",
                        wins = tr.wins ?: 0,
                        losses = tr.losses ?: 0,
                        pct = tr.winningPercentage ?: ".000",
                        gb = tr.gamesBack ?: "-",
                        divisionRank = tr.divisionRank ?: 0
                    )
                } ?: emptyList()
            )
        }

        val responseWc = api.getStandings(leagueId = "103,104", season = season, standingsTypes = "wildCard")
        val recordsWc = responseWc.records ?: emptyList()
        val wildCards = recordsWc.flatMap { rec ->
            val leagueFullName = rec.league?.name ?: ""
            val leagueAbbr = when {
                leagueFullName.contains("American", ignoreCase = true) -> "AL"
                leagueFullName.contains("National", ignoreCase = true) -> "NL"
                else -> leagueFullName
            }
            rec.teamRecords?.map { tr ->
                WildCardEntry(
                    rank = tr.wildCardRank ?: 0,
                    teamId = tr.team?.id ?: 0,
                    teamName = tr.team?.name ?: "Unknown",
                    wins = tr.wins ?: 0,
                    losses = tr.losses ?: 0,
                    pct = tr.winningPercentage ?: ".000",
                    wcGb = tr.wildCardGamesBack ?: "-",
                    league = leagueAbbr
                )
            } ?: emptyList()
        }

        return Pair(divisions, wildCards)
    }

    suspend fun getStatsLeaders(category: String, season: String, statGroup: String): List<StatLeader> {
        val response = api.getStatsLeaders(
            leaderCategories = category,
            season = season,
            statGroup = statGroup
        )
        val alLeaders = response.leagueLeaders?.firstOrNull()?.leaders ?: emptyList()
        return alLeaders.sortedBy { it.rank }
    }

    suspend fun getBoxscore(gamePk: Long): BoxscoreResponse {
        return api.getBoxscore(gamePk)
    }

    suspend fun getTeamRoster(teamId: Int): List<RosterEntry> {
        val response = api.getTeamRoster(teamId)
        return response.roster ?: emptyList()
    }

    suspend fun getPerson(personId: Int): BoxscorePerson? {
        // Hydrate with season stats and current team
        val hydrate = "stats(group=[hitting,pitching],type=[season])"
        val response = api.getPerson(personId, hydrate)
        return response.people?.firstOrNull()
    }

    suspend fun getPeopleLast3(ids: List<Int>, season: String): List<BoxscorePerson> {
        if (ids.isEmpty()) return emptyList()
        val hydrate = "stats(group=[hitting,pitching],type=[gameLog],season=$season,gameType=[R]),currentTeam"
        val response = api.getPeople(ids.joinToString(","), hydrate)
        return response.people ?: emptyList()
    }

    suspend fun getPlayerSeasons(personId: Int): List<String> {
        val response = api.getPersonCareer(personId)
        val person = response.people?.firstOrNull() ?: return emptyList()
        return person.stats?.flatMap { it.splits ?: emptyList() }
            ?.mapNotNull { it.season }
            ?.distinct()
            ?.sortedDescending()
            ?: emptyList()
    }

    suspend fun getPlayerStats(personId: Int, year: String): BoxscorePerson? {
        // The MLB Stats API does NOT reliably include bio fields (birthDate, height, weight, etc.)
        // in the person object when a complex stats hydrate is used. This affects certain players
        // (e.g. pitchers like Cristopher Sánchez) whose bio data is silently omitted in the
        // hydrated response even though it exists in the plain /people/{id} endpoint.
        //
        // Strategy: always make THREE parallel calls so each concern is isolated:
        //   1. Bio call  — no stats hydrate → API always returns full bio fields for all players
        //   2. Stats call — season + gameLog only (no "career" in the same request)
        //   3. Career call — career type with no season filter (mixing season= with career type
        //      is invalid and can cause the API to return incomplete data)
        // Then merge everything into one BoxscorePerson.

        // 1. Bio — plain call, guaranteed to return birthDate / height / weight / etc.
        val bioPerson = runCatching {
            api.getPerson(personId, "currentTeam,primaryPosition")
        }.getOrNull()?.people?.firstOrNull()

        // 2. Season + game-log stats for the requested year
        val statsHydrate =
            "stats(group=[hitting,pitching,fielding]," +
                    "type=[season,gameLog]," +
                    "season=$year," +
                    "gameType=[R,S,E,P,W,A])"
        val statsPerson = runCatching {
            api.getPerson(personId, statsHydrate)
        }.getOrNull()?.people?.firstOrNull()

        // 3. Career stats — no season filter
        val careerHydrate =
            "stats(group=[hitting,pitching,fielding],type=[career],gameType=[R])"
        val careerPerson = runCatching {
            api.getPerson(personId, careerHydrate)
        }.getOrNull()?.people?.firstOrNull()

        // Need at least bio or stats to build a meaningful response
        val base = bioPerson ?: statsPerson ?: return null

        // Merge all stat containers from both season and career calls
        val mergedStats = buildList {
            statsPerson?.stats?.let { addAll(it) }
            careerPerson?.stats?.let { addAll(it) }
        }

        // Build the final person: bio fields always come from bioPerson (most reliable),
        // stats come from the merged season + career data.
        return base.copy(
            stats              = mergedStats,
            // Prefer bioPerson for all bio fields; fall back to statsPerson if bioPerson
            // somehow didn't return them (defensive).
            birthDate          = bioPerson?.birthDate          ?: statsPerson?.birthDate,
            currentAge         = bioPerson?.currentAge         ?: statsPerson?.currentAge,
            birthCity          = bioPerson?.birthCity          ?: statsPerson?.birthCity,
            birthStateProvince = bioPerson?.birthStateProvince ?: statsPerson?.birthStateProvince,
            birthCountry       = bioPerson?.birthCountry       ?: statsPerson?.birthCountry,
            mlbDebutDate       = bioPerson?.mlbDebutDate       ?: statsPerson?.mlbDebutDate,
            height             = bioPerson?.height             ?: statsPerson?.height,
            weight             = bioPerson?.weight             ?: statsPerson?.weight,
            primaryNumber      = bioPerson?.primaryNumber      ?: statsPerson?.primaryNumber,
            primaryPosition    = bioPerson?.primaryPosition    ?: statsPerson?.primaryPosition,
            pitchHand          = bioPerson?.pitchHand          ?: statsPerson?.pitchHand,
            batSide            = bioPerson?.batSide            ?: statsPerson?.batSide,
            currentTeam        = bioPerson?.currentTeam        ?: statsPerson?.currentTeam
        )
    }

    suspend fun getPlayerGameLog(personId: Int, season: Int, group: String): List<StatSplit> {
        val hydrate = "stats(group=[$group],type=[gameLog],season=$season)"
        val response = api.getPerson(personId, hydrate)
        return response.people?.firstOrNull()?.stats?.find { it.type?.displayName == "gameLog" }?.splits ?: emptyList()
    }

    suspend fun getHallOfFame(year: String? = null): List<HallOfFameRow> {
        val response = api.getHallOfFame(year = year)
        return response.hallOfFame ?: emptyList()
    }

    private fun buildLinescoreModel(line: LinescoreResponse, isFinal: Boolean): LinescoreModel {
        val inningList = line.innings ?: emptyList()
        val maxInning = maxOf(9, inningList.size)

        val innings = (1..maxInning).map { i ->
            val inn = inningList.find { it.num == i }

            val awayRuns = inn?.away?.runs?.toString() ?: ""
            var homeRuns = inn?.home?.runs?.toString() ?: ""

            // If the game is final and it's the last inning, check if the home team didn't need to bat
            if (isFinal && i == maxInning && homeRuns == "") {
                // Confirm away team DID bat (which is typical if we have an inning entry at all)
                if (awayRuns != "") {
                    homeRuns = "X"
                }
            }

            InningScoreModel(
                num = i,
                awayRuns = awayRuns,
                homeRuns = homeRuns
            )
        }

        return LinescoreModel(
            innings = innings,
            awayTotal = TeamTotalsModel(line.teams?.away?.runs ?: 0, line.teams?.away?.hits ?: 0, line.teams?.away?.errors ?: 0),
            homeTotal = TeamTotalsModel(line.teams?.home?.runs ?: 0, line.teams?.home?.hits ?: 0, line.teams?.home?.errors ?: 0),
            awayHits = line.teams?.away?.hits ?: 0,
            homeHits = line.teams?.home?.hits ?: 0
        )
    }
    // ─── Awards ───────────────────────────────────────────────────────────────

    /**
     * Discovers every MLB award via v1/awards, then concurrently fetches
     * recipients for each award for [season]. Groups with no recipients are
     * dropped, preserving the API's own ordering.
     */
    suspend fun getAwardsForSeason(season: String): List<AwardsUiGroup> {
        // Check cache first
        awardsCache[season]?.let { return it }

        // Step 1 — discover all award definitions from the API.
        val definitions = runCatching {
            api.getAwards(sportId = 1).awards ?: emptyList()
        }.getOrElse { emptyList() }
            .filter { it.id != null && it.name != null }

        if (definitions.isEmpty()) return emptyList()

        // Step 2 — fetch recipients for every award concurrently.
        // Hydrate recipients with player person details to get their full name.
        val groups = coroutineScope {
            definitions.map { def ->
                async {
                    val recipients = runCatching {
                        api.getAwardRecipients(
                            awardId = def.id!!,
                            season = season,
                            sportId = 1,
                            hydrate = "player"
                        ).awards ?: emptyList()
                    }.getOrElse { emptyList() }

                    if (recipients.isNotEmpty()) {
                        AwardsUiGroup(
                            label = def.name!!,
                            awardId = def.id!!,
                            recipients = recipients
                        )
                    } else null
                }
            }.awaitAll()
        }

        // Step 3 — strip nulls, preserve the original definition order.
        val idOrder = definitions.mapIndexed { i, d -> d.id to i }.toMap()
        val result = groups
            .filterNotNull()
            .sortedBy { idOrder[it.awardId] ?: Int.MAX_VALUE }

        // Save to cache before returning
        if (result.isNotEmpty()) {
            awardsCache[season] = result
        }
        return result
    }
}