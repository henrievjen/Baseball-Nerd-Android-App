package com.baseballnerd.app.data.model

import com.google.gson.annotations.SerializedName

// ─── Schedule Response ───────────────────────────────────────────────────────

data class ScheduleResponse(val dates: List<ScheduleDate>?)
data class ScheduleDate(
    val date: String?,
    val games: List<GameSummary>?
)

data class GameSummary(
    val gamePk: Long,
    val gameDate: String?,
    val status: GameStatus?,
    val teams: GameTeams?,
    val linescore: LinescoreResponse?,
    val resumeDate: String?,
    val venue: VenueInfo?,
    val doubleHeader: String? = null,
    val gameNumber: Int? = null,
    val gamedayType: String? = null,
    val gameType: String? = null,
    val seriesDescription: String? = null,
    val gamesInSeries: Int? = null,
    val seriesGameNumber: Int? = null,
    val boxscore: BoxscoreResponse? = null,
    val broadcasts: List<Broadcast>? = null
)

data class Broadcast(
    val id: Int?,
    val type: String?,
    val name: String?,
    val source: String?,
    val homeAway: String?
)

data class VenueInfo(
    val id: Int?,
    val name: String?,
    val timeZone: TimeZoneInfo? = null
)

data class TimeZoneInfo(
    val id: String?,
    val offset: Int?,
    val tz: String?
)

data class GameStatus(
    val abstractGameState: String?,
    val detailedState: String?,
    val statusCode: String?,
    val startTimeTBD: Boolean? = null
)

data class GameTeams(val away: GameTeamEntry?, val home: GameTeamEntry?)
data class GameTeamEntry(
    val team: TeamInfo?,
    val score: Int?,
    val probablePitcher: ProbablePitcher?,
    val leagueRecord: LeagueRecord?
)
data class TeamInfo(val id: Int, val name: String?)

data class LeagueRecord(val wins: Int?, val losses: Int?, val pct: String?)

data class ProbablePitcher(
    val id: Int?,
    val fullName: String?,
    val primaryNumber: String? = null,
    val pitchHand: PitchHand? = null,
    val stats: List<BoxscoreStatContainer>? = null
)

data class PitchHand(val code: String?, val description: String?)

// ─── Linescore Response ──────────────────────────────────────────────────────

data class LinescoreResponse(
    val currentInning: Int?,
    val currentInningOrdinal: String?,
    val inningHalf: String?,
    val innings: List<InningLine>?,
    val teams: LinescoreTeams?,
    val offense: Offense?,
    val defense: Defense?,
    val balls: Int?,
    val strikes: Int?,
    val outs: Int?
)

data class InningLine(val num: Int?, val home: InningScore?, val away: InningScore?)
data class InningScore(val runs: Int?, val hits: Int?, val errors: Int?)
data class LinescoreTeams(val home: TeamScore?, val away: TeamScore?)
data class TeamScore(val runs: Int?, val hits: Int?, val errors: Int?)

data class Offense(
    @SerializedName("batter") val batter: PlayerReference?,
    @SerializedName("first")  val first: PlayerReference?,
    @SerializedName("second") val second: PlayerReference?,
    @SerializedName("third")  val third: PlayerReference?
)

data class Defense(val pitcher: PlayerReference?)
data class PlayerReference(val id: Int?, val fullName: String?)

// ─── Game UI Models ──────────────────────────────────────────────────────────

data class PitcherModel(
    val id: Int? = null,
    val name: String,
    val ip: String,
    val er: Int,
    val k: Int,
    val bb: Int,
    val seasonWins: Int = 0,
    val seasonLosses: Int = 0,
    val seasonEra: String = "-.--"
)

data class BatterModel(val id: Int? = null, val name: String, val hits: Int, val atBats: Int)
data class InningScoreModel(val num: Int, val awayRuns: String, val homeRuns: String)
data class TeamTotalsModel(val runs: Int, val hits: Int, val errors: Int)
data class LinescoreModel(
    val innings: List<InningScoreModel>,
    val awayTotal: TeamTotalsModel,
    val homeTotal: TeamTotalsModel,
    val awayHits: Int = 0,
    val homeHits: Int = 0
)

data class StarterModel(
    val id: Int,
    val name: String,
    val number: String?,
    val hand: String?,
    val era: String?,
    val record: String?,
    val strikeouts: Int?
)

// ─── Lineup Models ────────────────────────────────────────────────────────────

data class LineupBatter(
    val id: Int,
    val battingOrder: Int,   // 1–9
    val name: String,
    val position: String,    // "CF", "1B", etc.
    val ab: Int,
    val r: Int,
    val h: Int,
    val rbi: Int,
    val bb: Int,
    val k: Int,
    val hr: Int,
    val gameHAb: String,     // "2-4" format for the game
    val seasonAvg: String? = null,
    val seasonObp: String? = null,
    val seasonSlg: String? = null,
    val seasonOps: String? = null,
    val isSubstitution: Boolean = false
)

data class LineupPitcher(
    val id: Int,
    val name: String,
    val ip: String,
    val h: Int,
    val r: Int,
    val er: Int,
    val bb: Int,
    val k: Int,
    val hr: Int,
    val pitches: Int = 0,
    val strikes: Int = 0,
    val balls: Int = 0,
    val era: String,
    val seasonEra: String = "-.--",
    val seasonWhip: String? = null
)

data class TeamLineup(
    val teamName: String,
    val teamId: Int,
    val batters: List<LineupBatter>,
    val pitchers: List<LineupPitcher> = emptyList()
)

// ─── Bullpen Usage Models ───────────────────────────────────────────────────

data class BullpenUsageModel(
    val pitcherName: String,
    val pitcherId: Int,
    val usage: List<PitchCountDay>
)

data class PitchCountDay(
    val date: String, // "MM/dd" or similar
    val count: Int
)

// ─── Unified Game Card Model ─────────────────────────────────────────────────

data class GameCardModel(
    val gamePk: Long,
    val awayTeamId: Int,
    val homeTeamId: Int,
    val awayTeamName: String,
    val homeTeamName: String,
    val awayScore: Int,
    val homeScore: Int,
    val awayHits: Int = 0,
    val homeHits: Int = 0,
    val awayErrors: Int = 0,
    val homeErrors: Int = 0,
    val gameState: String,
    val detailedState: String,
    val inningOrdinal: String,
    val currentInning: Int = 0,
    val inningHalf: String = "",
    val balls: Int,
    val strikes: Int,
    val outs: Int,
    val runnerOnFirst: Boolean,
    val runnerOnSecond: Boolean,
    val runnerOnThird: Boolean,
    val currentPitcher: PitcherModel? = null,
    val currentBatter: BatterModel? = null,
    val currentAtBatPitches: List<PitchDetail> = emptyList(),
    val gameDate: String,
    val resumeDate: String? = null,
    val linescore: LinescoreModel? = null,
    val awayLineup: TeamLineup? = null,
    val homeLineup: TeamLineup? = null,
    val plays: List<PlaySummary> = emptyList(),
    val awayStarter: StarterModel? = null,
    val homeStarter: StarterModel? = null,
    val awayRecord: String? = null,
    val homeRecord: String? = null,
    val venueName: String? = null,
    val venueTimeZone: String? = null,
    val venueTimeZoneAbbrev: String? = null,
    val weather: String? = null,
    val wind: String? = null,
    val attendance: String? = null,
    val firstPitch: String? = null,
    val gameDuration: String? = null,
    val doubleHeader: String? = null,
    val gameNumber: Int? = null,
    val isStartTimeTBD: Boolean = false,
    val awayBullpenUsage: List<BullpenUsageModel> = emptyList(),
    val homeBullpenUsage: List<BullpenUsageModel> = emptyList(),
    // No Hitter / Perfect Game Flags
    val awayNoHitter: Boolean = false,
    val homeNoHitter: Boolean = false,
    val awayPerfectGame: Boolean = false,
    val homePerfectGame: Boolean = false,
    val gameType: String? = null,
    val seriesDescription: String? = null,
    val seriesGameNumber: Int? = null,
    val gamesInSeries: Int? = null,
    val broadcasts: List<Broadcast>? = null
)

data class PitchDetail(
    val pitchNumber: Int,
    val pitchType: String,        // e.g. "Four-Seam Fastball", "Slider"
    val pitchTypeCode: String,    // e.g. "FF", "SL"
    val speed: String,            // e.g. "95.2 mph" or "--"
    val callDescription: String,  // e.g. "Ball", "Called Strike", "Foul Ball"
    val callCode: String,         // e.g. "B", "C", "S", "F", "X"
    val px: Double? = null,       // horizontal location (plate_x)
    val pz: Double? = null,       // vertical location (plate_z)
    val szTop: Double? = null,
    val szBottom: Double? = null
)

data class PlaySummary(
    val atBatIndex: Int,
    val batterName: String,
    val batterId: Int? = null,
    val pitcherName: String? = null,
    val pitcherId: Int? = null,
    val description: String,
    val inning: Int,
    val halfInning: String,       // "top" or "bottom"
    val pitches: List<PitchDetail> = emptyList(),
    val isScoringPlay: Boolean = false,
    val awayScore: Int = 0,
    val homeScore: Int = 0
)

// ─── Standings Response ──────────────────────────────────────────────────────

data class StandingsResponse(val records: List<StandingsRecord2>?)

data class StandingsRecord2(
    val division: DivisionInfo?,
    val league: LeagueInfo?,
    val teamRecords: List<TeamRecord>?
)

data class DivisionInfo(val id: Int?, val name: String?, val nameShort: String?)
data class LeagueInfo(val id: Int?, val name: String?, val abbreviation: String?)

data class TeamRecord(
    val team: TeamInfo2?,
    val wins: Int?,
    val losses: Int?,
    val winningPercentage: String?,
    val gamesBack: String?,
    val wildCardGamesBack: String?,
    val divisionRank: Int?,
    val wildCardRank: Int?
)

data class TeamInfo2(val id: Int?, val name: String?)

data class StandingsDivision(val divisionName: String, val league: String, val teams: List<StandingsRecord>)

data class StandingsRecord(
    val teamId: Int,
    val teamName: String,
    val wins: Int,
    val losses: Int,
    val pct: String,
    val gb: String,
    val divisionRank: Int
)

data class WildCardEntry(
    val rank: Int,
    val teamId: Int,
    val teamName: String,
    val wins: Int,
    val losses: Int,
    val pct: String,
    val wcGb: String,
    val league: String
)

// ─── Boxscore Response ────────────────────────────────────────────────────────

data class BoxscoreResponse(
    val teams: BoxscoreTeams?,
    val info: List<BoxscoreInfoContainer>? = null
)

data class BoxscoreInfoContainer(
    val label: String?,
    val value: String?
)

data class BoxscoreTeams(val home: BoxscoreTeamData?, val away: BoxscoreTeamData?)

data class BoxscoreTeamData(
    val team: BoxscoreTeamInfo?,
    val battingOrder: List<Int>?,          // ordered list of playerIds
    val pitchers: List<Int>?,              // ordered list of pitcherIds
    val players: Map<String, BoxscorePlayer>?,
    val probablePitcher: PlayerReference? = null,
    val teamStats: BoxscoreTeamStats? = null
)

data class BoxscoreTeamStats(
    val batting: BoxscoreBattingStats?,
    val pitching: BoxscorePitchingStats?
)

data class BoxscoreTeamInfo(val id: Int?, val name: String?)

data class BoxscorePlayer(
    val person: BoxscorePerson?,
    val position: BoxscorePosition?,
    val battingOrder: String?,             // "100" = 1st, "200" = 2nd, etc.
    val stats: BoxscorePlayerStats?
)

data class BoxscorePerson(
    val id: Int?,
    val fullName: String?,
    val firstName: String? = null,
    val lastName: String? = null,
    val nameFirstLast: String? = null,
    val name: String? = null,
    val nickName: String? = null,
    val birthDate: String? = null,
    val deathDate: String? = null,
    val currentAge: Int? = null,
    val birthCity: String? = null,
    val birthStateProvince: String? = null,
    val birthCountry: String? = null,
    val mlbDebutDate: String? = null,
    val height: String? = null,
    val weight: Int? = null,
    val active: Boolean? = null,
    val stats: List<BoxscoreStatContainer>?,
    val primaryNumber: String? = null,
    val primaryPosition: BoxscorePosition? = null,
    val pitchHand: PitchHand? = null,
    val batSide: PitchHand? = null,
    val currentTeam: TeamInfo? = null
)

data class BoxscorePosition(val abbreviation: String?, val name: String? = null, val code: String? = null)

data class BoxscoreStatContainer(
    val group: BoxscoreStatGroup?,
    val stats: BoxscorePlayerStats?,       // used in boxscore endpoint (flat game stats)
    val type: StatType? = null,
    val splits: List<StatSplit>? = null    // used in people endpoint (season/yearByYear/gameLog)
)

// ─── People / Stats endpoint split models ────────────────────────────────────

/** One row inside a stats/splits array from the people endpoint. */
data class StatSplit(
    val season: String? = null,
    val date: String? = null,          // "04/01" formatted date for game log entries
    val gameDate: String? = null,      // ISO date, e.g. "2024-04-01T18:10:00Z"
    val game: GameReference? = null,
    val isHome: Boolean? = null,
    val isWin: Boolean? = null,
    @SerializedName("stat") val stat: PlayerStatLine? = null,
    val opponent: GameOpponent? = null,
    val team: GameOpponent? = null,
    val gameType: String? = null,
    val position: BoxscorePosition? = null
)

data class GameReference(
    val id: Int? = null,
    val gamePk: Long? = null,
    val link: String? = null
)

data class GameOpponent(val id: Int?, val name: String?)

data class StatType(val displayName: String?)

data class BoxscoreStatGroup(val displayName: String?)

data class BoxscorePlayerStats(
    val pitching: BoxscorePitchingStats?,
    val batting: BoxscoreBattingStats?
)

data class BoxscorePitchingStats(
    val inningsPitched: String?,
    val hits: Int?,
    val runs: Int?,
    val earnedRuns: Int?,
    val strikeOuts: Int?,
    val baseOnBalls: Int?,
    val homeRuns: Int?,
    val era: String?,
    val wins: Int?,
    val losses: Int?,
    val gamesPlayed: Int? = null,
    val gamesStarted: Int? = null,
    val saves: Int? = null,
    val holds: Int? = null,
    val whip: String? = null,
    val avg: String? = null,
    val numberOfPitches: Int? = null,
    val strikes: Int? = null,
    val battersFaced: Int? = null,
    val outs: Int? = null
)

data class BoxscoreBattingStats(
    val atBats: Int?,
    val runs: Int?,
    val hits: Int?,
    @SerializedName("rbi") val rbi: Int?,
    val baseOnBalls: Int?,
    val strikeOuts: Int?,
    val avg: String? = null,
    val obp: String? = null,
    val slg: String? = null,
    val ops: String? = null,
    val homeRuns: Int? = null,
    val doubles: Int? = null,
    val triples: Int? = null,
    val stolenBases: Int? = null,
    val hitByPitch: Int? = null
)

// ─── Play-by-Play Response ────────────────────────────────────────────────────

data class PlayByPlayResponse(
    val allPlays: List<Play>?,
    val scoringPlays: List<Int>?
)

data class Play(
    val result: PlayResult?,
    val about: PlayAbout?,
    val matchup: PlayMatchup?,
    val playEvents: List<PlayEvent>?
)

data class PlayResult(
    val type: String?,
    val event: String?,
    val description: String?,
    val isScoringPlay: Boolean?,
    val awayScore: Int?,
    val homeScore: Int?
)

data class PlayAbout(
    val atBatIndex: Int?,
    val halfInning: String?,
    val inning: Int?,
    val isComplete: Boolean?
)

data class PlayMatchup(
    val batter: PlayPerson?,
    val pitcher: PlayPerson?
)

data class PlayPerson(
    val id: Int?,
    val fullName: String?,
    val name: String? = null,
    val nameFirstLast: String? = null
)

data class PlayEvent(
    val details: PlayEventDetails?,
    val pitchData: PitchEventData?,
    val pitchNumber: Int?,
    @SerializedName("isPitch") val isPitch: Boolean?,
    val type: String?
)

data class PlayEventDetails(
    val description: String?,
    val code: String?,
    @SerializedName("type") val pitchType: PlayEventType?,  // Pitch type: FF, SL, CU, etc.
    val call: PlayEventCall?,                                // Pitch call: Ball, Strike, etc.
    val isBall: Boolean?,
    val isStrike: Boolean?,
    val isOut: Boolean?
)

data class PlayEventType(
    val code: String?,
    val description: String?
)

data class PlayEventCall(
    val code: String?,
    val description: String?
)

data class PitchEventData(
    val startSpeed: Double?,
    val endSpeed: Double?,
    val strikeZoneTop: Double?,
    val strikeZoneBottom: Double?,
    val coordinates: PitchCoordinates?
)

data class PitchCoordinates(
    @SerializedName("pX") val px: Double?,
    @SerializedName("pZ") val pz: Double?
)

// ─── People Response ─────────────────────────────────────────────────────────

data class PeopleResponse(val people: List<BoxscorePerson>?)

// ─── Stats Leaders ──────────────────────────────────────────────────────────

data class LeagueLeadersResponse(
    val leagueLeaders: List<StatLeaderCategory>?
)

data class StatLeaderCategory(
    val leaderCategory: String?,
    val statGroup: String?,
    val leaders: List<StatLeader>?
)

data class StatLeader(
    val rank: Int,
    val value: String,
    val person: PlayPerson?,
    val team: TeamInfo?
)

// ─── Roster Response ─────────────────────────────────────────────────────────

data class RosterResponse(val roster: List<RosterEntry>?)
data class RosterEntry(
    val person: BoxscorePerson?,
    val position: BoxscorePosition?,
    val status: RosterStatus?
)
data class RosterStatus(val code: String?, val description: String?)

// ─── Award Responses ─────────────────────────────────────────────────────────

data class AwardDefinitionsResponse(val awards: List<AwardDefinition>?)
data class AwardDefinition(val id: String?, val name: String?, val description: String?)

data class AwardsResponse(val awards: List<AwardRecipient>?)
data class AwardDefinition2(val id: String?, val name: String?)

data class AwardRecipient(
    val id: String?,
    val name: String?,
    val recipientName: String?,
    val recipient: String? = null,
    val date: String?,
    val season: String?,
    val team: TeamInfo?,
    @SerializedName("player") val person: BoxscorePerson?,
    val results: List<AwardVoteResult>?
)

data class AwardVoteResult(
    val rank: Int?,
    val votes: Int?,
    val points: Int?,
    @SerializedName("player") val person: BoxscorePerson?
)

/** Grouping awards by their UI display label. */
data class AwardsUiGroup(
    val label: String,
    val awardId: String,
    val recipients: List<AwardRecipient>
)

// ─── Hall of Fame Response ───────────────────────────────────────────────────

data class HallOfFameResponse(val hallOfFame: List<HallOfFameRow>?)
data class HallOfFameRow(
    val person: PlayPerson?,
    val yearId: Int?,
    val votedBy: String?,
    val ballots: Int?,
    val votes: Int?,
    val percentage: Double?,
    val inducted: String?,
    val category: String?
)

// ─── Team Response ───────────────────────────────────────────────────────────

data class TeamResponse(val teams: List<TeamInfo3>?)
data class TeamInfo3(
    val id: Int,
    val name: String,
    val abbreviation: String?,
    val league: LeagueInfo?,
    val division: DivisionInfo?,
    val venue: VenueInfo?
)

data class PlayerStatLine(
    // Common
    val gamesPlayed: Int? = null,
    val position: BoxscorePosition? = null,
    // Batting
    val atBats: Int? = null,
    val runs: Int? = null,
    val hits: Int? = null,
    val homeRuns: Int? = null,
    @SerializedName("rbi") val rbi: Int? = null,
    val stolenBases: Int? = null,
    val avg: String? = null,
    val obp: String? = null,
    val slg: String? = null,
    val ops: String? = null,
    val strikeOuts: Int? = null,
    val baseOnBalls: Int? = null,
    val doubles: Int? = null,
    val triples: Int? = null,
    val plateAppearances: Int? = null,
    val totalBases: Int? = null,
    val sacFlies: Int? = null,
    val hitByPitch: Int? = null,
    // Pitching
    val era: String? = null,
    val wins: Int? = null,
    val losses: Int? = null,
    val inningsPitched: String? = null,
    val gamesStarted: Int? = null,
    val saves: Int? = null,
    val holds: Int? = null,
    val blownSaves: Int? = null,
    val whip: String? = null,
    val earnedRuns: Int? = null,
    @SerializedName("pitchingHits") val pitchingHits: Int? = null,
    @SerializedName("pitchingRuns") val pitchingRuns: Int? = null,
    @SerializedName("pitchingHomeRuns") val pitchingHomeRuns: Int? = null,
    val battersFaced: Int? = null,
    val strikeOutsPer9Inn: String? = null,
    val walksPer9Inn: String? = null,
    val hitsPer9Inn: String? = null,
    val strikeoutWalkRatio: String? = null,
    val numberOfPitches: Int? = null,
    val strikes: Int? = null,
    val hitBatters: Int? = null,
    val intentionalWalks: Int? = null,
    val completeGames: Int? = null,
    val shutouts: Int? = null,
    // Fielding
    val errors: Int? = null,
    val fielding: String? = null,
    val assists: Int? = null,
    val putOuts: Int? = null,
    val chances: Int? = null,
    val doublePlays: Int? = null,
    val caughtStealing: Int? = null
)
