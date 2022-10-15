This program simulates the outcome of League of Legends games and tracks the LP gained, win rates, win / loss streaks etc.
You can configure the settings in the config.ini, which you will find in the same folder as this file. 
An example for the config.ini is shown in the following (between the dotted lines):

-----------------------
simulated players = 10000
games played = 100
lp difference from skill level = 200
lp per game (identical gain/loss) = 16
lp difference for tenfold winrate = 650
-----------------------

Explanation of the above configuration:
1. We simulate 10000 players.
2. Each player plays 100 games. 
3. Every player starts 200 LP BELOW their skill level, i.e. they are 200 LP better than their starting MMR. 
   For example, this would simulate a player with a skill level of gold 4 (35 LP) starting at silver 2 (35 LP).
4. Each win results in +16 LP, each loss results in -16 LP. 
   This does not account for mismatch between LP and MMR, but over time this becomes irrelevant, and LP gains & losses should be roughly equal. 
5. The chance of winning a game depends on the difference between current LP and skill level, just like in chess. An LP difference of 650 LP will result in a 91% chance of winning. 
   This value of 650 is based on someone scraping ranked games from Riot's API and figuring out the win rate based on LP difference. 
   Thus, it is likely a very good estimate and probably shouldn't be changed (much).