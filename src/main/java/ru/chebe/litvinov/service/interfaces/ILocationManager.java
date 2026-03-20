package ru.chebe.litvinov.service.interfaces;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;

import java.util.List;

/**
 * Интерфейс для сервиса управления локациями.
 */
public interface ILocationManager {
    void locationInfo(MessageReceivedEvent event, String currentLocation);
    void map(MessageReceivedEvent event);
    List<String> getLocationList();
    Location getLocation(String location);
    Location movePlayerInPopulation(Player player, String nextLocation);
}
