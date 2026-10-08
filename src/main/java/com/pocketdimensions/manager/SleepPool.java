package com.pocketdimensions.manager;

import java.util.List;

/**
 * The overworld and every pocket room sleep as one: a head count of everyone in them, counted exactly as vanilla's
 * SleepStatus counts one dimension (spectators left out, at least one sleeper needed, the playersSleepingPercentage
 * gamerule rounded up). Pure logic, tested; PocketSleepHandler feeds it the players and acts on the answer.
 */
public record SleepPool(int active, int sleeping, int deepSleeping) {

    /** One player: spectating, in bed, and in bed long enough (vanilla's 100 ticks) to count towards skipping. */
    public record Sleeper(boolean spectator, boolean inBed, boolean longEnough) {}

    public static SleepPool of(List<Sleeper> players) {
        int active = 0, sleeping = 0, deep = 0;
        for (Sleeper s : players) {
            if (s.spectator()) continue;
            active++;
            if (s.inBed()) sleeping++;
            if (s.inBed() && s.longEnough()) deep++;
        }
        return new SleepPool(active, sleeping, deep);
    }

    /** How many must sleep for the night to pass (the "5" in "1/5 players sleeping"). */
    public int needed(int percent) {
        return Math.max(1, (int) Math.ceil(active * percent / 100.0f));
    }

    /** Enough are in bed: the message turns to "Sleeping through this night". */
    public boolean enoughInBed(int percent) {
        return sleeping >= needed(percent);
    }

    /** Enough have been in bed long enough: morning comes. */
    public boolean skipNight(int percent) {
        return enoughInBed(percent) && deepSleeping >= needed(percent);
    }
}
