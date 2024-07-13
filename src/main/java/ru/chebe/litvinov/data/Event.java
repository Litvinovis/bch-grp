package ru.chebe.litvinov.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Predicate;

@Getter
@Setter
@Builder
public class Event {
    String name;
    String description;
    String locationStart;
    String locationEnd;
    String type;
    String timeEnd;
    Predicate<Player> condition;
    boolean isAnswer;
    String correctAnswer;
    String wrongAnswer;
    int moneyReward;
    int xpReward;
    String itemReward;
}
