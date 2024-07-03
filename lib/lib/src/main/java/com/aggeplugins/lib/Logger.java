package com.aggeplugins.lib;

import com.example.Packets.MousePackets;

import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.api.*;
import net.runelite.api.Client;

import com.google.inject.Inject;

import java.util.*;
import java.util.stream.Collectors;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class Logger {
    @Inject
    private ItemManager itemManager;

    public Logger(Client client)
    {
        this.init();
    }

    private void init()
    {
        // Init the starting exp map with current skill exp.
        startingExp = new HashMap<>();
        for (Skill skill : Skill.values()) {
            startingExp.put(skill, client.getSkillExperience(skill));
        }
        // Init the gained exp map with all 0s.
        gainedExp = new HashMap<>();
        for (Skill skill : Skill.values()) {
            gainedExp.put(skill, 0);
        }

        // Construct a new loot price cache.
        lootCache = new HashMap<>();
        Loot loot = new Loot();
    
        time = Instant.now();

        count = 0;
    }

    public void resetExp()
    {
        // Reset the exp maps.
        startingExp.replaceAll((k, v) -> client.getSkillExperience(k));
        gainedExp.replaceAll((k, v) -> 0);
    }

    public void reset()
    {
        resetExp();

        // Zero out loot.
        loot = null;
        loot = new Loot();

        time = Instant.now();

        count = 0;
    }

    public void addExp(Skill skill, int exp)
    {
        gainedExp.replace(skill, exp - startingExp.get(skill));
    }

    public int getExp(Skill skill)
    {
        return gainedExp.get(skill);
    }

    public int getTotalExp()
    {
        AtomicInteger total = new AtomicInteger(0);
        gainedExp.forEach((k, v) -> total.addAndGet(v));
        return total.get();
    }

    public void addLoot(TileItem ti)
    {
        int id = ti.getId();
        String name = LibUtil.itemIdToString(id);
        int price = -1;
        if (name != null) {
            price = getLootPrice(name);
        }
        if (price != -1 && name != null) {
            loot.add(ti.getQuantity(), price, name);
        }
    }

    public Duration getTime()
    {
        Instant now = Instant.now();
        return Duration.between(time, now);
    }

    public String getFormattedTime() {
        Duration duration = this.getTime();
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public void addCount()
    {
        this.count++;
    }

    public int getCount()
    {
        return this.count;
    }

    /**
     * Gets the cached price or wiki price if not yet cached
     *
     * @param name Exact name of item
     * @return
     */
    private int getLootPrice(String name) 
    {
        if (lootCache.containsKey(name)) {
            return lootCache.get(name);
        }
        int price = itemManager.search(name).get(0).getWikiPrice();
        lootCache.put(name, price);
        return price;
    }

    public class Loot {
        private int amount;
        private int value;
        private String recent;
        public Loot()
        {
            this.amount = 0;
            this.value = 0;
            this.recent = "";
        }
        public int getAmount()                  { return this.amount; }
        public int getValue()                   { return this.value; }
        public String getRecent()               { return this.recent; }
        public void setAmount(int amt)          { this.amount = amt; }
        public void setValue(int v)             { this.value = v; }
        public void setRecent(String recent)    { this.recent = recent; }
        public void add(int amount, int value, String recent)
        {
            this.amount += amount;
            this.value += value;
            this.recent = recent;
        }
    }

    private Map<Skill, Integer> startingExp;
    private Map<Skill, Integer> gainedExp;
    private Loot loot;
    private Instant time;
    private Map<String, Integer> lootCache;
    private int count;
}
