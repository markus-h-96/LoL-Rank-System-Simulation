// written by Huz for fun, as I was interested in how much luck affected the climbing process in League of Legends, ~ 24.10.21
// TODO refactoring, probably add a Player class

import java.util.Locale;
import java.util.Random;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.io.Writer;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;

public class RankSystemSimulation {

	public static void main(String args[]) {

		String errorText = "";
		
		int totalPlayers = 10000;
		int gamesPlayed = 100;
		/*
		 * how much better the players are than their starting mmr; positive = players'
		 * skill is above their starting mmr, negative = players' skill is below their
		 * starting mmr
		 */
		int mmrDifferenceFromCorrectRank = 200;
		/*
		 * obviously have to assume that LP for win and loss is the same, since that's
		 * // basically how it works in the long run
		 */
		int lpPerGame = 16;
		// if set to true, print some info about current values to help debug
		boolean debug = false;
		// explanation below
		int mmrDifferenceForTenfoldWinrate = 650;
		/*
		 * LP / MMR difference where chance of winning is 10 times the chance of losing.
		 * In chess, 400 ELO difference means player A (with x ELO) has 10x the chance
		 * to win vs player B (with x-400 ELO) -> 10/11 vs 1/11 chance of winning. While
		 * a 400 LP / MMR difference in League of Legends might mean player A
		 * "outperforms" player B 10/11 of the time, that clearly does not result in a
		 * 91% win rate. Otherwise, a diamond 4 player would win 64% of their games in
		 * platinum 1, 76% in platinum 2, 91% in platinum 4, 99% in gold 4 etc., which
		 * seems unrealistic.
		 * 
		 * As League of Legends is a 5v5 game rather than a 1v1 game, the above may be
		 * true for an ELO / MMR difference of 2000, accounting for average MMR
		 * difference of 400 *per player*? Assuming all other 9 players in the game play
		 * at the same skill level and oneself plays at a skill level of X LP / MMR /
		 * Elo above that, one could then expect the following win rates:
		 * 
		 * 100 LP difference, e.g., D4 in P1 game - 53% chance of winning 200 LP
		 * difference, e.g., D4 in P2 game - 56% chance of winning 300 LP difference,
		 * e.g., D4 in P3 game - 59% chance of winning 400 LP difference, e.g., D4 in P4
		 * game - 61% chance of winning 800 LP difference, e.g., D4 in G4 game - 71%
		 * chance of winning 1200 LP difference, e.g., D4 in S4 game - 80% chance of
		 * winning 1600 LP difference, e.g., D4 in B4 game - 86% chance of winning 2000
		 * LP difference, e.g., D4 in I4 game - 91% chance of winning
		 * 
		 * To see all values, google plot 10^(x/2000) / (1 + 10^(x/2000))
		 * 
		 * 
		 * Update: Based on Spy's scraping of actual games
		 * https://cdn.discordapp.com/attachments/716044932141547540/912401067214524446/
		 * unknown.png (note that blue side wins more often overall), it seems like 650
		 * MMR difference = 10x chance of winning is a good estimate, as it leads to
		 * almost identical win rates. -> Use 650 for calculations, until Riot releases
		 * more information on this.
		 * 
		 */

		// if config.ini exists, try to parse
		String jarPath;
		
		try {
			jarPath = new File(RankSystemSimulation.class.getProtectionDomain().getCodeSource().getLocation().toURI())
					.getParentFile().getPath();

			if (new File(jarPath + "\\LoL Rank System Simulation\\config.ini")
					.isFile()) {
				try {
					BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(
							jarPath +"\\LoL Rank System Simulation\\config.ini")));
					String line;

					String[] keys = new String[] { "simulated players = ", "games played = ",
							"lp difference from skill level = ", "lp per game (identical gain/loss) = ",
							"lp difference for tenfold winrate = " };

					int[] values = new int[] { totalPlayers, gamesPlayed, mmrDifferenceFromCorrectRank, lpPerGame,
							mmrDifferenceForTenfoldWinrate };

					while ((line = br.readLine()) != null) {
						// try setting key to value
						line.trim();
						for (int i = 0; i < keys.length; i++) {
							if (line.startsWith(keys[i])) {
//							System.out.println(line.substring(keys[i].length()).strip());
								values[i] = Integer.parseInt(line.substring(keys[i].length()).trim());
								break;
							}
						}
					}
					br.close();
					simulateGames(values[0], values[1], values[2], values[3], values[4], debug, errorText);

				} catch (IOException | NumberFormatException e) {
					// TODO Auto-generated catch block
					errorText = "Something went wrong, please check your config.ini settings. Using default input for the following simulation.\n\n";
//					e.printStackTrace();
					simulateGames(totalPlayers, gamesPlayed, mmrDifferenceFromCorrectRank, lpPerGame,
							mmrDifferenceForTenfoldWinrate, debug, errorText);
				}
			} else {
				simulateGames(totalPlayers, gamesPlayed, mmrDifferenceFromCorrectRank, lpPerGame,
						mmrDifferenceForTenfoldWinrate, debug, errorText);
			}
		} catch (URISyntaxException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	static void simulateGames(int totalPlayers, int gamesPlayed, int mmrDifferenceFromCorrectRank, int lpPerGame,
			int mmrDifferenceForTenfoldWinrate, boolean debug, String errorText) {

		DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
		DecimalFormat df = new DecimalFormat("#.#####", symbols);
		df.setRoundingMode(RoundingMode.HALF_UP);

		double baseChanceToWin = 1
				- (1 / (1 + Math.pow(10, mmrDifferenceFromCorrectRank / (double) mmrDifferenceForTenfoldWinrate)));

		// most extreme cases out of all simulated players
		int overallLongestLosingStreak = 0;
		int overallLongestWinStreak = 0;
		double highestWinrate = 0;
		double lowestWinrate = 1;

		// final LP - how many players are within X LP of their skill level?
		int within50LPCounter = 0;
		int within100LPCounter = 0;
		int within150LPCounter = 0;
		int within200LPCounter = 0;
		int within250LPCounter = 0;
		int within300LPCounter = 0;

		// how many players were always at least 50 LP below their skill level?
		int always50LPTooLowCounter = 0;
		int always100LPTooLowCounter = 0;
		int always150LPTooLowCounter = 0;
		int always200LPTooLowCounter = 0;
		int always250LPTooLowCounter = 0;
		int always300LPTooLowCounter = 0;

		// how many players were always at least 50 lP above their skill level?
		int always50LPTooHighCounter = 0;
		int always100LPTooHighCounter = 0;
		int always150LPTooHighCounter = 0;
		int always200LPTooHighCounter = 0;
		int always250LPTooHighCounter = 0;
		int always300LPTooHighCounter = 0;

		int totalLpCounter = 0;
		int[] playerLpGains = new int[totalPlayers];

		// per player stats
		int longestWinStreak;
		int longestLosingStreak;
		int currentWinStreak;
		int currentLosingStreak;
		int totalWins;
		int totalLosses;

		int lpGained;

		double winrate;

		boolean always50LPTooLow;
		boolean always100LPTooLow;
		boolean always150LPTooLow;
		boolean always200LPTooLow;
		boolean always250LPTooLow;
		boolean always300LPTooLow;

		boolean always50LPTooHigh;
		boolean always100LPTooHigh;
		boolean always150LPTooHigh;
		boolean always200LPTooHigh;
		boolean always250LPTooHigh;
		boolean always300LPTooHigh;

		Random rnd = new Random();

		for (int j = 0; j < totalPlayers; j++) {
			double chanceToWin = baseChanceToWin;

			totalWins = 0;
			totalLosses = 0;
			longestWinStreak = 0;
			longestLosingStreak = 0;
			currentWinStreak = 0;
			currentLosingStreak = 0;
			lpGained = 0;

			always50LPTooLow = (mmrDifferenceFromCorrectRank - lpGained) >= 50;
			always100LPTooLow = (mmrDifferenceFromCorrectRank - lpGained) >= 100;
			always150LPTooLow = (mmrDifferenceFromCorrectRank - lpGained) >= 150;
			always200LPTooLow = (mmrDifferenceFromCorrectRank - lpGained) >= 200;
			always250LPTooLow = (mmrDifferenceFromCorrectRank - lpGained) >= 250;
			always300LPTooLow = (mmrDifferenceFromCorrectRank - lpGained) >= 300;

			always50LPTooHigh = (-mmrDifferenceFromCorrectRank + lpGained) >= 50;
			always100LPTooHigh = (-mmrDifferenceFromCorrectRank + lpGained) >= 100;
			always150LPTooHigh = (-mmrDifferenceFromCorrectRank + lpGained) >= 150;
			always200LPTooHigh = (-mmrDifferenceFromCorrectRank + lpGained) >= 200;
			always250LPTooHigh = (-mmrDifferenceFromCorrectRank + lpGained) >= 250;
			always300LPTooHigh = (-mmrDifferenceFromCorrectRank + lpGained) >= 300;

			for (int i = 0; i < gamesPlayed; i++) {
				if (rnd.nextDouble() < chanceToWin) {
					// win
					totalWins++;
					currentLosingStreak = 0;
					currentWinStreak++;
					if (debug)
						System.out.print("Player # " + j + ", Game # " + i + ": win");
					lpGained += lpPerGame;
				} else {
					// lose
					totalLosses++;
					currentWinStreak = 0;
					currentLosingStreak++;
					if (debug)
						System.out.print("Player # " + j + ", Game # " + i + ": loss");
					lpGained -= lpPerGame;
				}
				longestLosingStreak = Math.max(longestLosingStreak, currentLosingStreak);
				longestWinStreak = Math.max(longestWinStreak, currentWinStreak);

				always50LPTooLow &= (mmrDifferenceFromCorrectRank - lpGained) >= 50;
				always100LPTooLow &= (mmrDifferenceFromCorrectRank - lpGained) >= 100;
				always150LPTooLow &= (mmrDifferenceFromCorrectRank - lpGained) >= 150;
				always200LPTooLow &= (mmrDifferenceFromCorrectRank - lpGained) >= 200;
				always250LPTooLow &= (mmrDifferenceFromCorrectRank - lpGained) >= 250;
				always300LPTooLow &= (mmrDifferenceFromCorrectRank - lpGained) >= 300;

				always50LPTooHigh &= (-mmrDifferenceFromCorrectRank + lpGained) >= 50;
				always100LPTooHigh &= (-mmrDifferenceFromCorrectRank + lpGained) >= 100;
				always150LPTooHigh &= (-mmrDifferenceFromCorrectRank + lpGained) >= 150;
				always200LPTooHigh &= (-mmrDifferenceFromCorrectRank + lpGained) >= 200;
				always250LPTooHigh &= (-mmrDifferenceFromCorrectRank + lpGained) >= 250;
				always300LPTooHigh &= (-mmrDifferenceFromCorrectRank + lpGained) >= 300;

				// update chance of winning after each game
				chanceToWin = 1
						- 1 / (1 + Math.pow(10, (mmrDifferenceFromCorrectRank - (totalWins - totalLosses) * lpPerGame)
								/ (double) mmrDifferenceForTenfoldWinrate));

				if (debug) {
					System.out.println(", wins-losses: " + (totalWins - totalLosses) + ", chance to win next game is "
							+ df.format(100 * chanceToWin) + "%");
				}
			}
			// done simulating games for player j

			winrate = (double) totalWins / (double) gamesPlayed;

			totalLpCounter += lpGained;

			// final LP within X lP of skill level
			if (Math.abs(mmrDifferenceFromCorrectRank - lpGained) <= 300) {
				within300LPCounter++;
			}
			if (Math.abs(mmrDifferenceFromCorrectRank - lpGained) <= 250) {
				within250LPCounter++;
			}
			if (Math.abs(mmrDifferenceFromCorrectRank - lpGained) <= 200) {
				within200LPCounter++;
			}
			if (Math.abs(mmrDifferenceFromCorrectRank - lpGained) <= 150) {
				within150LPCounter++;
			}
			if (Math.abs(mmrDifferenceFromCorrectRank - lpGained) <= 100) {
				within100LPCounter++;
			}
			if (Math.abs(mmrDifferenceFromCorrectRank - lpGained) <= 50) {
				within50LPCounter++;
			}

			// always X LP below skill level
			if (always50LPTooLow) {
				always50LPTooLowCounter++;
			}
			if (always100LPTooLow) {
				always100LPTooLowCounter++;
			}
			if (always150LPTooLow) {
				always150LPTooLowCounter++;
			}
			if (always200LPTooLow) {
				always200LPTooLowCounter++;
			}
			if (always250LPTooLow) {
				always250LPTooLowCounter++;
			}
			if (always300LPTooLow) {
				always300LPTooLowCounter++;
			}

			// always X LP above skill level
			if (always50LPTooHigh) {
				always50LPTooHighCounter++;
			}
			if (always100LPTooHigh) {
				always100LPTooHighCounter++;
			}
			if (always150LPTooHigh) {
				always150LPTooHighCounter++;
			}
			if (always200LPTooHigh) {
				always200LPTooHighCounter++;
			}
			if (always250LPTooHigh) {
				always250LPTooHighCounter++;
			}
			if (always300LPTooHigh) {
				always300LPTooHighCounter++;
			}

			playerLpGains[j] = lpGained;

			// update most extreme cases overall
			overallLongestWinStreak = Math.max(overallLongestWinStreak, longestWinStreak);
			overallLongestLosingStreak = Math.max(overallLongestLosingStreak, longestLosingStreak);
			highestWinrate = Math.max(highestWinrate, winrate);
			lowestWinrate = Math.min(lowestWinrate, winrate);
		}

		int bestLpGain = (int) Math.round((2 * highestWinrate - 1) * lpPerGame * gamesPlayed);
		int worstLpGain = (int) Math.round((2 * lowestWinrate - 1) * lpPerGame * gamesPlayed);

		int lpDistributionValues = (bestLpGain - worstLpGain) / (2 * lpPerGame) + 1;
		int[] lpDistribution = new int[lpDistributionValues];

		for (int i = 0; i < lpDistributionValues; i++) {
			lpDistribution[i] = 0;
		}
		for (int i = 0; i < totalPlayers; i++) {
			lpDistribution[(playerLpGains[i] - worstLpGain) / (2 * lpPerGame)]++;
		}

		int[] lpDistributionCumulated = new int[lpDistributionValues];
		lpDistributionCumulated[0] = lpDistribution[0];
		for (int i = 1; i < lpDistributionValues; i++) {
			lpDistributionCumulated[i] = lpDistributionCumulated[i - 1] + lpDistribution[i];
		}

		// first index / counter that is > 0, as in "there were actually players who
		// were this lucky / unlucky and never came close to their skill level"
		int indexTooLow = -1;
		int indexTooHigh = -1;

		int[] tooLow = new int[] { always50LPTooLowCounter, always100LPTooLowCounter, always150LPTooLowCounter,
				always200LPTooLowCounter, always250LPTooLowCounter, always300LPTooLowCounter };
		for (int i = 5; i >= 0; i--) {
			if (tooLow[i] > 0) {
				indexTooLow = i;
				break;
			}
		}

		int[] tooHigh = new int[] { always50LPTooHighCounter, always100LPTooHighCounter, always150LPTooHighCounter,
				always200LPTooHighCounter, always250LPTooHighCounter, always300LPTooHighCounter };
		for (int i = 5; i >= 0; i--) {
			if (tooHigh[i] > 0) {
				indexTooHigh = i;
				break;
			}
		}

		String summary = "Simulated players: " + totalPlayers + "\nGames played per player: " + gamesPlayed
				+ "\nPlayers' expected skill level: " + mmrDifferenceFromCorrectRank + " LP from starting point"
				+ "\nLP per game: " + "+-" + lpPerGame
				+ "\nAdjusting the players' chance to win after each game - Playing at a skill level of "
				+ mmrDifferenceForTenfoldWinrate
				+ " LP above current rank results in a 91% chance to win (winning is 10 times as likely as losing)"
				+ "\n-----------------------------------------------------------------------------------------"
				+ "-------------------------------------------------------------------------------------------"
				+ "\nChance of winning first match: " + df.format(100 * baseChanceToWin) + "%" + "\nAverage LP gained: "
				+ (totalLpCounter / totalPlayers)
				+ "\n\nPlayers who ended up within 50 LP of their expected skill level: "
				+ df.format(100 * (double) within50LPCounter / totalPlayers) + "%"
				+ "\nPlayers who ended up within 100 LP of their expected skill level: "
				+ df.format(100 * (double) within100LPCounter / totalPlayers) + "%"
				+ "\nPlayers who ended up within 150 LP of their expected skill level: "
				+ df.format(100 * (double) within150LPCounter / totalPlayers) + "%"
				+ "\nPlayers who ended up within 200 LP of their expected skill level: "
				+ df.format(100 * (double) within200LPCounter / totalPlayers) + "%"
				+ "\nPlayers who ended up within 250 LP of their expected skill level: "
				+ df.format(100 * (double) within250LPCounter / totalPlayers) + "%"
				+ "\nPlayers who ended up within 300 LP of their expected skill level: "
				+ df.format(100 * (double) within300LPCounter / totalPlayers) + "%" + "\n\nLuckiest player: "
				+ df.format(100 * highestWinrate) + "% win rate (" + bestLpGain + " LP)" + "\nUnluckiest player: "
				+ df.format(100 * lowestWinrate) + "% win rate (" + worstLpGain + " LP)" + "\nLongest win streak: "
				+ overallLongestWinStreak + "\nLongest losing streak: " + overallLongestLosingStreak + "\n\n";

		// keep in mind that mmrDiff is positive if players' mmr is BELOW skill level

		if (mmrDifferenceFromCorrectRank > 0) {
			if (indexTooLow == -1) {
				summary += "All players came within 50 LP of their expected skill level at some point.\n";
			} else {
				for (int i = 0; i <= indexTooLow; i++) {
					summary += "Players who were always " + ((i + 1) * 50) + " LP below their expected skill level: "
							+ df.format(100.0 * tooLow[i] / totalPlayers) + "%\n";
				}
			}
		} else if (mmrDifferenceFromCorrectRank < 0) {
			if (indexTooHigh == -1) {
				summary += "All players came within 50 LP of their expected skill level at some point.\n";
			} else {
				for (int i = 0; i <= indexTooHigh; i++) {
					summary += "Players who were always " + ((i + 1) * 50) + " LP above their expected skill level: "
							+ df.format(100.0 * tooHigh[i] / totalPlayers) + "%\n";
				}
			}
		} else {
			summary += "All players came within 50 LP of their expected skill level at some point.\n";
		}

		summary += "\nFinal LP distribution:\n";
		for (int i = 0; i < lpDistributionValues; i++) {
			summary += worstLpGain + (2 * lpPerGame * i) + " LP - "
					+ df.format(100.0 * lpDistribution[i] / totalPlayers) + "% (" + lpDistribution[i] + " player";
			if (lpDistribution[i] != 1) {
				summary += "s)";
			} else {
				summary += ")";
			}
			summary += " | " + df.format(100.0 * lpDistributionCumulated[i] / totalPlayers) + "% cumulated ("
					+ lpDistributionCumulated[i] + " player";
			if (lpDistributionCumulated[i] != 1) {
				summary += "s)";
			} else {
				summary += ")";
			}
			summary += "\n";

		}

		summary = errorText + summary;
		
		System.out.print(summary);

		// path
		String jarPath;
		try {
			jarPath = new File(RankSystemSimulation.class.getProtectionDomain().getCodeSource().getLocation().toURI())
					.getParentFile().getPath();

			// creating files
			// folder to put files inside
			new File(jarPath + "\\LoL Rank System Simulation").mkdir();

			// summary (stats)
			try (Writer writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(jarPath + "\\LoL Rank System Simulation\\simulation.txt"), "utf-8"))) {
				writer.write(summary);
				writer.close();
			} catch (IOException ioe) {
				System.out.println("Something went wrong1");
			}

			// config
			if (!new File(jarPath + "\\LoL Rank System Simulation\\config.ini").isFile()) {
				try (Writer writer = new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream(jarPath + "\\LoL Rank System Simulation\\config.ini"), "utf-8"))) {
					writer.write("simulated players = 10000" + "\ngames played = 100"
							+ "\nlp difference from skill level = 200" + "\nlp per game (identical gain/loss) = 16"
							+ "\nlp difference for tenfold winrate = 650");
					writer.close();
				} catch (IOException ioe) {
					System.out.println("Something went wrong2");
				}
			}

			// readme
			if (!new File(jarPath + "\\LoL Rank System Simulation\\readme.txt").isFile()) {
				try (Writer writer = new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream(jarPath + "\\LoL Rank System Simulation\\readme.txt"), "utf-8"))) {
					writer.write(
							"This program simulates the outcome of League of Legends games and tracks the LP gained, win rates, win / loss streaks etc."
									+ "\nYou can configure the settings in the config.ini, which you will find in the same folder as this file. "
									+ "\nAn example for the config.ini is shown in the following (between the dotted lines):"
									+ "\n\n-----------------------" + "\nsimulated players = 10000"
									+ "\ngames played = 100" + "\nlp difference from skill level = 200"
									+ "\nlp per game (identical gain/loss) = 16"
									+ "\nlp difference for tenfold winrate = 650" + "\n-----------------------"
									+ "\n\nExplanation of the above configuration:" + "\n1. We simulate 10000 players."
									+ "\n2. Each player plays 100 games. "
									+ "\n3. Every player starts 200 LP BELOW their skill level, i.e. they are 200 LP better than their starting MMR. "
									+ "\n   For example, this would simulate a player with a skill level of gold 4 (35 LP) starting at silver 2 (35 LP)."
									+ "\n4. Each win results in +16 LP, each loss results in -16 LP. "
									+ "\n   This does not account for mismatch between LP and MMR, but over time this becomes irrelevant, and LP gains & losses should be roughly equal. "
									+ "\n5. The chance of winning a game depends on the difference between current LP and skill level, just like in chess. "
									+ "An LP difference of 650 LP will result in a 91% chance of winning. "
									+ "\n   This value of 650 is based on someone scraping ranked games from Riot's API and figuring out the win rate based on LP difference. "
									+ "\n   Thus, it is likely a very good estimate and probably shouldn't be changed (much).");

					writer.close();
					new File(jarPath + "\\LoL Rank System Simulation\\readme.txt").setReadOnly();
				} catch (IOException ioe) {
					System.out.println("Something went wrong3");
				}
			}
		} catch (URISyntaxException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
}