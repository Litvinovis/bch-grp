package ru.chebe.litvinov.data;

import java.time.LocalDate;

/**
 * POJO, представляющий набор ежедневных квестов игрока.
 * Одна строка соответствует одному игроку за одну дату.
 */
public class DailyQuest {

    private String userId;
    private LocalDate questDate;

    private String quest1Type;
    private int quest1Progress;
    private int quest1Required;
    private boolean quest1Done;

    private String quest2Type;
    private int quest2Progress;
    private int quest2Required;
    private boolean quest2Done;

    private String quest3Type;
    private int quest3Progress;
    private int quest3Required;
    private boolean quest3Done;

    private boolean bonusClaimed;

    public DailyQuest() {}

    // --- userId ---
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    // --- questDate ---
    public LocalDate getQuestDate() { return questDate; }
    public void setQuestDate(LocalDate questDate) { this.questDate = questDate; }

    // --- quest1 ---
    public String getQuest1Type() { return quest1Type; }
    public void setQuest1Type(String quest1Type) { this.quest1Type = quest1Type; }

    public int getQuest1Progress() { return quest1Progress; }
    public void setQuest1Progress(int quest1Progress) { this.quest1Progress = quest1Progress; }

    public int getQuest1Required() { return quest1Required; }
    public void setQuest1Required(int quest1Required) { this.quest1Required = quest1Required; }

    public boolean isQuest1Done() { return quest1Done; }
    public void setQuest1Done(boolean quest1Done) { this.quest1Done = quest1Done; }

    // --- quest2 ---
    public String getQuest2Type() { return quest2Type; }
    public void setQuest2Type(String quest2Type) { this.quest2Type = quest2Type; }

    public int getQuest2Progress() { return quest2Progress; }
    public void setQuest2Progress(int quest2Progress) { this.quest2Progress = quest2Progress; }

    public int getQuest2Required() { return quest2Required; }
    public void setQuest2Required(int quest2Required) { this.quest2Required = quest2Required; }

    public boolean isQuest2Done() { return quest2Done; }
    public void setQuest2Done(boolean quest2Done) { this.quest2Done = quest2Done; }

    // --- quest3 ---
    public String getQuest3Type() { return quest3Type; }
    public void setQuest3Type(String quest3Type) { this.quest3Type = quest3Type; }

    public int getQuest3Progress() { return quest3Progress; }
    public void setQuest3Progress(int quest3Progress) { this.quest3Progress = quest3Progress; }

    public int getQuest3Required() { return quest3Required; }
    public void setQuest3Required(int quest3Required) { this.quest3Required = quest3Required; }

    public boolean isQuest3Done() { return quest3Done; }
    public void setQuest3Done(boolean quest3Done) { this.quest3Done = quest3Done; }

    // --- bonusClaimed ---
    public boolean isBonusClaimed() { return bonusClaimed; }
    public void setBonusClaimed(boolean bonusClaimed) { this.bonusClaimed = bonusClaimed; }
}
