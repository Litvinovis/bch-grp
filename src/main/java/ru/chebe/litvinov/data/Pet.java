package ru.chebe.litvinov.data;

import lombok.Getter;
import lombok.Setter;

/**
 * Питомец игрока. Может быть одного из пяти типов, иметь уровень и характеристики.
 */
@Getter
@Setter
public class Pet {
    private String type;       // WOLF, FOX, CAT, RAVEN, LEGENDARY
    private int level;         // 1 = обычный, 2 = эволюционировавший
    private int hunger;        // 0-100, уменьшается со временем
    private int battleCount;   // для порога эволюции
    private long lastFedTime;

    public Pet() {
        this.level = 1;
        this.hunger = 100;
        this.battleCount = 0;
        this.lastFedTime = System.currentTimeMillis();
    }

    public Pet(String type) {
        this();
        this.type = type;
    }
}
