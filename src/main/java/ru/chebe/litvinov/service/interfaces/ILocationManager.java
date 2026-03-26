package ru.chebe.litvinov.service.interfaces;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import ru.chebe.litvinov.data.Location;
import ru.chebe.litvinov.data.Player;

import java.util.List;

/**
 * Интерфейс для сервиса управления локациями.
 */
public interface ILocationManager {

    /**
     * Отправляет игроку информацию о запрошенной или текущей локации.
     *
     * @param event           событие Discord-сообщения
     * @param currentLocation текущая локация игрока (используется при запросе «моя»)
     */
    void locationInfo(MessageReceivedEvent event, String currentLocation);

    /**
     * Отправляет изображение карты игрового мира в Discord-канал.
     *
     * @param event событие Discord-сообщения
     */
    void map(MessageReceivedEvent event);

    /**
     * Возвращает список всех названий локаций в игре.
     *
     * @return список названий локаций
     */
    List<String> getLocationList();

    /**
     * Возвращает локацию по её названию.
     *
     * @param location название локации
     * @return объект Location или null если локация не найдена
     */
    Location getLocation(String location);

    /**
     * Перемещает игрока между локациями, обновляя списки населения обеих локаций.
     *
     * @param player       игрок, который перемещается
     * @param nextLocation название целевой локации
     * @return обновлённый объект целевой локации
     */
    Location movePlayerInPopulation(Player player, String nextLocation);
}
