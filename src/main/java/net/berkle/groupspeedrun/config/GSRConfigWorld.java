package net.berkle.groupspeedrun.config;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * World-Specific Configuration for GroupSpeedrun.
 * Handles persistence via both .txt (Properties) for long-term storage
 * and NBT for network syncing.
 */
public class GSRConfigWorld {
    private static final Logger LOGGER = LoggerFactory.getLogger("GSR-Config");

    // --- [ SHARED GAMEPLAY SETTINGS ] ---
    public boolean groupDeathEnabled = true;
    public boolean sharedHealthEnabled = false;
    public float maxHearts = 10.0f;
    public List<UUID> excludedPlayers = new ArrayList<>();

    // --- [ SHARED RUN STATE ] ---
    public long startTime = -1;
    public boolean isFailed = false;
    public boolean isVictorious = false;
    public boolean isTimerFrozen = false;
    public long frozenTime = 0;
    public long lastSplitTime = -1;
    public long victoryTimer = 0;

    // --- [ SPLIT MILESTONES ] ---
    public long timeNether = 0, timeBastion = 0, timeFortress = 0, timeEnd = 0, timeDragon = 0;

    // --- [ SHARED STRUCTURES ] ---
    public int fortressX = 0, fortressZ = 0;
    public boolean fortressActive = false;
    public int bastionX = 0, bastionZ = 0;
    public boolean bastionActive = false;
    public int strongholdX = 0, strongholdZ = 0;
    public boolean strongholdActive = false;
    public int shipX = 0, shipZ = 0;
    public boolean shipActive = false;

    // --- [ VISUAL CONSTANTS ] ---
    public String fortressColor = "#511515";
    public String bastionColor = "#3C3947";
    public String strongholdColor = "#97d16b";
    public String shipColor = "#A6638C";

    public void resetRunData() {
        this.startTime = -1;
        this.isFailed = false;
        this.isVictorious = false;
        this.isTimerFrozen = false;
        this.frozenTime = 0;
        this.lastSplitTime = -1;
        this.fortressActive = false;
        this.bastionActive = false;
        this.strongholdActive = false;
        this.shipActive = false;
        this.victoryTimer = 0;
        this.timeNether = 0; this.timeBastion = 0; this.timeFortress = 0; this.timeEnd = 0; this.timeDragon = 0;
    }

    public long getElapsedTime() {
        if (startTime <= 0) return 0;
        if (isTimerFrozen || isFailed || isVictorious) return frozenTime;
        return System.currentTimeMillis() - startTime;
    }

    // --- [ NBT SYNCING ] ---

    public void writeNbt(NbtCompound nbt) {
        nbt.putLong("startTime", startTime);
        nbt.putBoolean("isFailed", isFailed);
        nbt.putBoolean("wasVictorious", isVictorious);
        nbt.putBoolean("isTimerFrozen", isTimerFrozen);
        nbt.putLong("frozenTime", frozenTime);
        nbt.putFloat("maxHearts", maxHearts);

        // Splits
        nbt.putLong("timeNether", timeNether);
        nbt.putLong("timeBastion", timeBastion);
        nbt.putLong("timeFortress", timeFortress);
        nbt.putLong("timeEnd", timeEnd);
        nbt.putLong("timeDragon", timeDragon);

        // Excluded Players List
        NbtList excludedList = new NbtList();
        for (UUID uuid : excludedPlayers) {
            excludedList.add(NbtString.of(uuid.toString()));
        }
        nbt.put("excludedPlayers", excludedList);

        // Structures
        nbt.putInt("fortX", fortressX); nbt.putInt("fortZ", fortressZ); nbt.putBoolean("fortActive", fortressActive);
        nbt.putInt("bastX", bastionX); nbt.putInt("bastZ", bastionZ); nbt.putBoolean("bastActive", bastionActive);
        nbt.putInt("strongX", strongholdX); nbt.putInt("strongZ", strongholdZ); nbt.putBoolean("strongActive", strongholdActive);
        nbt.putInt("shipX", shipX); nbt.putInt("shipZ", shipZ); nbt.putBoolean("shipActive", shipActive);
    }

    public void readNbt(NbtCompound nbt) {
        if (nbt == null) return;

        // Use .orElse() because your NbtCompound returns Optional<Long>, Optional<Boolean>, etc.
        this.startTime = nbt.getLong("startTime").orElse(-1L);
        this.isFailed = nbt.getBoolean("isFailed").orElse(false);
        this.isVictorious = nbt.getBoolean("wasVictorious").orElse(false);
        this.isTimerFrozen = nbt.getBoolean("isTimerFrozen").orElse(false);
        this.frozenTime = nbt.getLong("frozenTime").orElse(0L);
        this.maxHearts = nbt.getFloat("maxHearts").orElse(10.0f);

        // Splits
        this.timeNether = nbt.getLong("timeNether").orElse(0L);
        this.timeBastion = nbt.getLong("timeBastion").orElse(0L);
        this.timeFortress = nbt.getLong("timeFortress").orElse(0L);
        this.timeEnd = nbt.getLong("timeEnd").orElse(0L);
        this.timeDragon = nbt.getLong("timeDragon").orElse(0L);

        // List Handling (Based on your NbtCompound.java line 400)
        nbt.getList("excludedPlayers").ifPresent(list -> {
            this.excludedPlayers.clear();
            for (int i = 0; i < list.size(); i++) {
                // NbtList in 1.21 also returns Optional<String> for getString(i)
                this.excludedPlayers.add(UUID.fromString(list.getString(i).orElse("")));
            }
        });

        // Structures
        this.fortressX = nbt.getInt("fortX").orElse(0);
        this.fortressZ = nbt.getInt("fortZ").orElse(0);
        this.fortressActive = nbt.getBoolean("fortActive").orElse(false);
        this.bastionX = nbt.getInt("bastX").orElse(0);
        this.bastionZ = nbt.getInt("bastZ").orElse(0);
        this.bastionActive = nbt.getBoolean("bastActive").orElse(false);
        this.strongholdX = nbt.getInt("strongX").orElse(0);
        this.strongholdZ = nbt.getInt("strongZ").orElse(0);
        this.strongholdActive = nbt.getBoolean("strongActive").orElse(false);
        this.shipX = nbt.getInt("shipX").orElse(0);
        this.shipZ = nbt.getInt("shipZ").orElse(0);
        this.shipActive = nbt.getBoolean("shipActive").orElse(false);
    }
    // --- [ FILE I/O ] ---

    public static File getWorldConfigFile(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("groupspeedrun.txt").toFile();
    }

    public static GSRConfigWorld load(MinecraftServer server) {
        GSRConfigWorld config = new GSRConfigWorld();
        File worldFile = getWorldConfigFile(server);

        if (!worldFile.exists()) {
            LOGGER.info("No world GSR config found. Creating defaults.");
            config.save(server);
            return config;
        }

        Properties p = new Properties();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(worldFile), StandardCharsets.UTF_8))) {
            p.load(r);

            config.startTime = Long.parseLong(p.getProperty("startTime", "-1"));
            config.isFailed = Boolean.parseBoolean(p.getProperty("isFailed", "false"));
            config.isVictorious = Boolean.parseBoolean(p.getProperty("wasVictorious", "false"));
            config.isTimerFrozen = Boolean.parseBoolean(p.getProperty("isTimerFrozen", "false"));
            config.frozenTime = Long.parseLong(p.getProperty("frozenTime", "0"));

            config.groupDeathEnabled = Boolean.parseBoolean(p.getProperty("groupDeathEnabled", "true"));
            config.sharedHealthEnabled = Boolean.parseBoolean(p.getProperty("sharedHealthEnabled", "false"));
            config.maxHearts = Float.parseFloat(p.getProperty("maxHearts", "10.0"));

            String excludedStr = p.getProperty("excludedPlayers", "");
            if (!excludedStr.isEmpty()) {
                for (String s : excludedStr.split(",")) {
                    try { config.excludedPlayers.add(UUID.fromString(s.trim())); } catch (Exception ignored) {}
                }
            }

            config.timeNether = Long.parseLong(p.getProperty("timeNether", "0"));
            config.timeBastion = Long.parseLong(p.getProperty("timeBastion", "0"));
            config.timeFortress = Long.parseLong(p.getProperty("timeFortress", "0"));
            config.timeEnd = Long.parseLong(p.getProperty("timeEnd", "0"));
            config.timeDragon = Long.parseLong(p.getProperty("timeDragon", "0"));

            config.fortressActive = Boolean.parseBoolean(p.getProperty("fortActive", "false"));
            config.fortressX = Integer.parseInt(p.getProperty("fortX", "0"));
            config.fortressZ = Integer.parseInt(p.getProperty("fortZ", "0"));
            config.bastionActive = Boolean.parseBoolean(p.getProperty("bastActive", "false"));
            config.bastionX = Integer.parseInt(p.getProperty("bastX", "0"));
            config.bastionZ = Integer.parseInt(p.getProperty("bastZ", "0"));
            config.strongholdActive = Boolean.parseBoolean(p.getProperty("strongActive", "false"));
            config.strongholdX = Integer.parseInt(p.getProperty("strongX", "0"));
            config.strongholdZ = Integer.parseInt(p.getProperty("strongZ", "0"));
            config.shipActive = Boolean.parseBoolean(p.getProperty("shipActive", "false"));
            config.shipX = Integer.parseInt(p.getProperty("shipX", "0"));
            config.shipZ = Integer.parseInt(p.getProperty("shipZ", "0"));

        } catch (Exception e) {
            LOGGER.error("Failed to load world-specific GSR config!", e);
        }
        return config;
    }

    public void save(MinecraftServer server) {
        File worldFile = getWorldConfigFile(server);
        if (worldFile.getParentFile() != null) worldFile.getParentFile().mkdirs();

        Properties p = new Properties();
        p.setProperty("startTime", String.valueOf(startTime));
        p.setProperty("isFailed", String.valueOf(isFailed));
        p.setProperty("wasVictorious", String.valueOf(isVictorious));
        p.setProperty("isTimerFrozen", String.valueOf(isTimerFrozen));
        p.setProperty("frozenTime", String.valueOf(frozenTime));

        p.setProperty("groupDeathEnabled", String.valueOf(groupDeathEnabled));
        p.setProperty("sharedHealthEnabled", String.valueOf(sharedHealthEnabled));
        p.setProperty("maxHearts", String.valueOf(maxHearts));
        p.setProperty("excludedPlayers", excludedPlayers.stream().map(UUID::toString).collect(Collectors.joining(",")));

        p.setProperty("timeNether", String.valueOf(timeNether));
        p.setProperty("timeBastion", String.valueOf(timeBastion));
        p.setProperty("timeFortress", String.valueOf(timeFortress));
        p.setProperty("timeEnd", String.valueOf(timeEnd));
        p.setProperty("timeDragon", String.valueOf(timeDragon));

        p.setProperty("fortActive", String.valueOf(fortressActive));
        p.setProperty("fortX", String.valueOf(fortressX));
        p.setProperty("fortZ", String.valueOf(fortressZ));
        p.setProperty("bastActive", String.valueOf(bastionActive));
        p.setProperty("bastX", String.valueOf(bastionX));
        p.setProperty("bastZ", String.valueOf(bastionZ));
        p.setProperty("strongActive", String.valueOf(strongholdActive));
        p.setProperty("strongX", String.valueOf(strongholdX));
        p.setProperty("strongZ", String.valueOf(strongholdZ));
        p.setProperty("shipActive", String.valueOf(shipActive));
        p.setProperty("shipX", String.valueOf(shipX));
        p.setProperty("shipZ", String.valueOf(shipZ));

        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(worldFile), StandardCharsets.UTF_8))) {
            p.store(w, "GSR World Data");
        } catch (IOException e) {
            LOGGER.error("Failed to save world-specific GSR config!", e);
        }
    }

    private int hexToInt(String h) { try { return Color.decode(h).getRGB(); } catch (Exception e) { return -1; } }
    public int getFortressColorInt() { return hexToInt(fortressColor); }
    public int getBastionColorInt() { return hexToInt(bastionColor); }
    public int getStrongholdColorInt() { return hexToInt(strongholdColor); }
    public int getShipColorInt() { return hexToInt(shipColor); }
}