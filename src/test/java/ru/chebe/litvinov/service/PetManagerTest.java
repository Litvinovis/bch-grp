package ru.chebe.litvinov.service;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.chebe.litvinov.data.Pet;
import ru.chebe.litvinov.data.Player;
import ru.chebe.litvinov.repository.PlayerRepository;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class PetManagerTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private MessageReceivedEvent event;
    @Mock private MessageChannelUnion channel;
    @Mock private MessageCreateAction messageAction;
    @Mock private net.dv8tion.jda.api.entities.User user;
    @Mock private net.dv8tion.jda.api.entities.Message message;

    private PetManager petManager;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        petManager = new PetManager(playerRepository);
        when(event.getChannel()).thenReturn(channel);
        when(event.getAuthor()).thenReturn(user);
        when(user.getId()).thenReturn("player1");
        when(channel.sendMessage(anyString())).thenReturn(messageAction);
        when(messageAction.submit()).thenReturn(CompletableFuture.completedFuture(null));
        when(event.getMessage()).thenReturn(message);
    }

    @Test
    public void testGetPetInfo_noPet_assignsCat() {
        Player player = new Player("Test", "player1");
        assertNull(player.getPet());
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+питомец");

        petManager.getPetInfo(event);

        verify(playerRepository).put(eq("player1"), argThat(p -> p.getPet() != null && "CAT".equals(p.getPet().getType())));
    }

    @Test
    public void testGetPetInfo_withPet_showsInfo() {
        Player player = new Player("Test", "player1");
        Pet pet = new Pet("WOLF");
        player.setPet(pet);
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+питомец");

        petManager.getPetInfo(event);

        verify(channel, atLeastOnce()).sendMessage(anyString());
    }

    @Test
    public void testFeedPet_noPet_sendsError() {
        Player player = new Player("Test", "player1");
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+кормить кружка цикория");

        petManager.feedPet(event);

        verify(channel).sendMessage(contains("нет питомца"));
    }

    @Test
    public void testFeedPet_noItem_sendsError() {
        Player player = new Player("Test", "player1");
        Pet pet = new Pet("CAT");
        player.setPet(pet);
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+кормить несуществующий предмет");

        petManager.feedPet(event);

        verify(channel).sendMessage(contains("нет в инвентаре"));
    }

    @Test
    public void testFeedPet_withItem_restoresHunger() {
        Player player = new Player("Test", "player1");
        Pet pet = new Pet("CAT");
        pet.setHunger(50);
        player.setPet(pet);
        player.getInventory().put("кружка цикория", 1);
        when(playerRepository.get("player1")).thenReturn(player);
        when(message.getContentDisplay()).thenReturn("+кормить кружка цикория");

        petManager.feedPet(event);

        verify(playerRepository).put(eq("player1"), argThat(p -> p.getPet().getHunger() == 80));
    }

    @Test
    public void testEvolvePet_notEnoughBattles_noEvolution() {
        Player player = new Player("Test", "player1");
        Pet pet = new Pet("WOLF");
        pet.setBattleCount(5);
        pet.setLevel(1);
        player.setPet(pet);

        petManager.evolvePet(player);

        assertEquals(1, player.getPet().getLevel());
    }

    @Test
    public void testEvolvePet_enough_evolves() {
        Player player = new Player("Test", "player1");
        Pet pet = new Pet("WOLF");
        pet.setBattleCount(10);
        pet.setLevel(1);
        player.setPet(pet);

        petManager.evolvePet(player);

        assertEquals(2, player.getPet().getLevel());
    }

    @Test
    public void testGetPetBattleBonus_wolf_str() {
        Player player = new Player("Test", "player1");
        Pet pet = new Pet("WOLF");
        player.setPet(pet);

        int bonus = petManager.getPetBattleBonus(player, "str");

        assertEquals(3, bonus);
    }

    @Test
    public void testGetPetBattleBonus_wolf_evolved() {
        Player player = new Player("Test", "player1");
        Pet pet = new Pet("WOLF");
        pet.setLevel(2);
        player.setPet(pet);

        int bonus = petManager.getPetBattleBonus(player, "str");

        assertEquals(6, bonus);
    }

    @Test
    public void testGetPetBattleBonus_noPet_zero() {
        Player player = new Player("Test", "player1");

        int bonus = petManager.getPetBattleBonus(player, "str");

        assertEquals(0, bonus);
    }

    @Test
    public void testRegisterBattle_decreasesHunger() {
        Player player = new Player("Test", "player1");
        Pet pet = new Pet("FOX");
        pet.setHunger(100);
        player.setPet(pet);

        petManager.registerBattle(player);

        assertEquals(90, player.getPet().getHunger());
        assertEquals(1, player.getPet().getBattleCount());
    }
}
