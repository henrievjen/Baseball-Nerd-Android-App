package com.baseballnerd.app.data.api

import com.baseballnerd.app.data.model.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MlbApiService {

    @GET("v1/schedule")
    suspend fun getSchedule(
        @Query("sportId") sportId: Int = 1,
        @Query("date") date: String? = null,
        @Query("teamId") teamId: Int? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null,
        @Query("gameType") gameType: String? = null,
        @Query("gamePks") gamePks: String? = null,
        @Query("hydrate") hydrate: String = "linescore,team,probablePitcher(stats(group=[pitching],type=[season],gameType=R))"
    ): ScheduleResponse

    @GET("v1/game/{gamePk}/linescore")
    suspend fun getLinescore(
        @Path("gamePk") gamePk: Long
    ): LinescoreResponse

    @GET("v1/game/{gamePk}/boxscore")
    suspend fun getBoxscore(
        @Path("gamePk") gamePk: Long,
        @Query("hydrate") hydrate: String = "person(stats(group=[pitching,hitting],type=[season],gameType=R))"
    ): BoxscoreResponse

    @GET("v1/standings")
    suspend fun getStandings(
        @Query("leagueId") leagueId: String,
        @Query("season") season: String,
        @Query("standingsTypes") standingsTypes: String = "regularSeason",
        @Query("hydrate") hydrate: String = "division,league,team"
    ): StandingsResponse

    @GET("v1/game/{gamePk}/playByPlay")
    suspend fun getPlayByPlay(
        @Path("gamePk") gamePk: Long
    ): PlayByPlayResponse

    @GET("v1/people/{personId}")
    suspend fun getPerson(
        @Path("personId") personId: Int,
        @Query("hydrate") hydrate: String = "stats(group=[hitting,pitching],type=[season,gameLog,last3Games],gameType=[R,S,E,P,W,A])",
        @Query("season") season: String? = null
    ): PeopleResponse

    @GET("v1/people")
    suspend fun getPeople(
        @Query("personIds") personIds: String,
        @Query("hydrate") hydrate: String = "stats(group=[hitting,pitching],type=[season,last3Games],gameType=[R,S,E,P,W,A])",
        @Query("season") season: String? = null
    ): PeopleResponse

    /** Career year-by-year stats — no season filter, returns splits for every active year. */
    @GET("v1/people/{personId}")
    suspend fun getPersonCareer(
        @Path("personId") personId: Int,
        @Query("hydrate") hydrate: String = "stats(group=[hitting,pitching],type=yearByYear),currentTeam"
    ): PeopleResponse

    @GET("v1/stats/leaders")
    suspend fun getStatsLeaders(
        @Query("leaderCategories") leaderCategories: String,
        @Query("season") season: String,
        @Query("statGroup") statGroup: String,
        @Query("limit") limit: Int = 100,
        @Query("statType") statType: String = "season",
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): LeagueLeadersResponse

    @GET("v1/teams/{teamId}/roster")
    suspend fun getTeamRoster(
        @Path("teamId") teamId: Int,
        @Query("rosterType") rosterType: String = "active",
        @Query("season") season: String? = null,
        @Query("hydrate") hydrate: String = "person(stats(type=season,group=fielding))"
    ): RosterResponse

    @GET("v1/awards")
    suspend fun getAwards(
        @Query("sportId") sportId: Int = 1,
        @Query("season") season: String? = null
    ): AwardDefinitionsResponse

    @GET("v1/awards/{awardId}/recipients")
    suspend fun getAwardRecipients(
        @Path("awardId") awardId: String,
        @Query("season") season: String,
        @Query("sportId") sportId: Int = 1,
        @Query("hydrate") hydrate: String? = null
    ): AwardsResponse

    @GET("v1/hallOfFame")
    suspend fun getHallOfFame(
        @Query("year") year: String? = null,
        @Query("sportId") sportId: Int = 1
    ): HallOfFameResponse

    @GET("v1/teams")
    suspend fun getTeams(
        @Query("sportId") sportId: Int = 1,
        @Query("season") season: String? = null
    ): TeamResponse
}
