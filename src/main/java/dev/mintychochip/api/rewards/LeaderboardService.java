package dev.mintychochip.api.rewards;

/** Public read API for daily and weekly player rankings. */
public interface LeaderboardService {

  LeaderboardView leaderboard(LeaderboardPeriod period, int limit);
}
