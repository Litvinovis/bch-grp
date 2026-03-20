package ru.chebe.litvinov.raid;

import ru.chebe.litvinov.data.Person;

/**
 * Рейдовый босс — Person с увеличенными характеристиками (5-10x от обычного).
 */
public class RaidBoss extends Person {

    private final int maxHp;

    public RaidBoss(String name, int hp, int strength) {
        this.nickName = name;
        this.hp = hp;
        this.maxHp = hp;
        this.strength = strength;
        this.armor = 5;
    }

    public int getMaxHp() {
        return maxHp;
    }

    /** Стандартный рейдовый босс — Рейд-Чебеш */
    public static RaidBoss createDefault() {
        return new RaidBoss("Рейд-Чебеш", 5000, 25);
    }
}
