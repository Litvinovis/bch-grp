package ru.chebe.litvinov.raid;

import ru.chebe.litvinov.data.Person;

/**
 * Рейдовый босс — Person с увеличенными характеристиками (5-10x от обычного).
 */
public class RaidBoss extends Person {

    private final int maxHp;

    /**
     * Создаёт рейдового босса с указанными характеристиками.
     * Броня устанавливается в 5 автоматически.
     *
     * @param name     имя босса
     * @param hp       начальное (и максимальное) количество HP
     * @param strength сила атаки
     */
    public RaidBoss(String name, int hp, int strength) {
        this.nickName = name;
        this.hp = hp;
        this.maxHp = hp;
        this.strength = strength;
        this.armor = 5;
    }

    /**
     * Возвращает максимальное HP рейдового босса.
     *
     * @return начальное значение HP
     */
    public int getMaxHp() {
        return maxHp;
    }

    /**
     * Создаёт стандартного рейдового босса «Рейд-Чебеш» с характеристиками HP: 5000, Сила: 25.
     *
     * @return экземпляр стандартного рейдового босса
     */
    public static RaidBoss createDefault() {
        return new RaidBoss("Рейд-Чебеш", 5000, 25);
    }
}
