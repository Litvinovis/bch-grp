package ru.chebe.litvinov.service;

import org.junit.Test;
import ru.chebe.litvinov.service.interfaces.IPlayersManager;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Item 92: Smoke tests verifying that IPlayersManager declares all expected commands
 * and that PlayersManager implements them.
 */
public class PlayersManagerAllCommandsTest {

    private static final Set<String> EXPECTED_METHODS = Set.of(
        // Core player commands
        "createPlayer", "getPlayerInfo", "getInventoryInfo", "move",
        "bossFight", "playersFight", "assignEvent", "checkEvent", "changeEvent",
        "buyItem", "useItem", "sellItem", "dailyBonus",
        "dieCast", "playRoulette", "rockPaperScissors", "guessTheNumber",
        "fightNpc", "clanRegister", "clanLeave", "clanJoin",
        "acceptApply", "rejectApply", "clanInfo",
        "getPlayer", "deathOfPlayer", "changeMoney", "changeXp", "changeHp",
        "topLeaderboard", "chooseClass", "getAchievements", "tradeItem",
        "challengeDuel", "acceptDuel", "declineDuel",
        "showDailyQuests", "lastBattleLog", "clanNpcFight", "locationPath",
        "exploreLocation", "goHome", "bankCommand", "upgradeItem", "compareItems",
        "craftItem", "merchantShop", "questJournal", "takeCredit", "repayCredit",
        "playPoker", "horseRacingInfo", "betOnHorse", "exchangeInfo",
        "sellResource", "clanLeaderboard", "clanBankCommand", "clanUpgradesCommand",
        "setClanBase", "clanWar", "promoteClanMember", "kickClanMember",
        "seasonLeaderboard", "prestige", "playerProfile", "hallOfFame",
        // Items 85-150 additions
        "adminReload", "giveActivityReward", "onlineCommand",
        "petCommand", "feedPet", "mountRacingRun", "mountRacingInfo",
        "professionCommand", "gatherResource", "showProfessionRecipes", "resourceMarket",
        "captureTerritory", "siegeCommand", "fortressCommand", "territoryClanMap",
        "declareAlliance", "worldBossAttack", "invasionStatus", "crisisStatus",
        "showSeason", "serverTournament", "showSkills", "investSkill", "useAbility",
        "chooseSecondClass", "showFactions", "diaryCommand", "topActivity",
        "lorePage", "weeklyBoard", "placeBounty", "getBounties",
        "arenaChallenge", "arenaLeaderboard", "teamArena", "survivalChallenge",
        "showChampion", "challengeChampion", "showLeague",
        "registerTournament", "tournamentStatus", "checkHiddenQuest"
    );

    @Test
    public void iPlayersManager_declaresAllExpectedMethods() {
        Set<String> declared = Arrays.stream(IPlayersManager.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());

        for (String expected : EXPECTED_METHODS) {
            assertTrue("IPlayersManager is missing method: " + expected, declared.contains(expected));
        }
    }

    @Test
    public void playersManager_implementsIPlayersManager() {
        assertTrue("PlayersManager must implement IPlayersManager",
            IPlayersManager.class.isAssignableFrom(PlayersManager.class));
    }

    @Test
    public void playersManager_hasNoAbstractMethods_fromInterface() {
        // All interface methods should be implemented (no abstract methods in the concrete class)
        Method[] interfaceMethods = IPlayersManager.class.getDeclaredMethods();
        Set<String> concreteMethods = Arrays.stream(PlayersManager.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());

        for (Method m : interfaceMethods) {
            assertTrue("PlayersManager is missing implementation for: " + m.getName(),
                concreteMethods.contains(m.getName()));
        }
    }

    @Test
    public void newManagerClasses_areLoadable() {
        // Smoke: all new service classes exist and are loadable
        assertNotNull(PetManager.class);
        assertNotNull(ProfessionManager.class);
        assertNotNull(TerritoryManager.class);
        assertNotNull(WorldEventManager.class);
        assertNotNull(FactionManager.class);
        assertNotNull(BountyManager.class);
        assertNotNull(ArenaManager.class);
        assertNotNull(TournamentManager.class);
    }

    @Test
    public void newRepositoryClasses_areLoadable() {
        assertNotNull(ru.chebe.litvinov.repository.BountyRepository.class);
        assertNotNull(ru.chebe.litvinov.repository.TerritoryRepository.class);
        assertNotNull(ru.chebe.litvinov.repository.TournamentRepository.class);
        assertNotNull(ru.chebe.litvinov.repository.GameEventLogRepository.class);
    }

    @Test
    public void petDataClass_isLoadable() {
        assertNotNull(ru.chebe.litvinov.data.Pet.class);
    }
}
