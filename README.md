# 🏁 Group Speedrun (GSR) — Fabric 1.21.11

**The ultimate fun-tracker for your next co-op adventure!**

**Group Speedrun (GSR)** is a lightweight utility designed to make playing Minecraft with friends more engaging. Instead of just playing side-by-side, GSR ties your group together with a shared timer, progress notifications, and a "Who's Who" of fun stats at the end of your session. Whether you're trying to beat the game quickly or just want to see who spent the most time looking through chests, GSR tracks it all for you.

---

## 🚀 Key Features

* **Shared Journey Timer:** A single, synchronized clock for the whole group. It starts automatically when the first person moves.
* **Team Milestones:** Get notified the moment anyone in the group reaches the Nether, finds a Bastion, or enters the End.
* **Shared Survival (Optional):** Enable **Shared Health** so the whole team shares a single heart bar, or **Group Death** to make every player's life count for the whole team.
* **The Post-Game Wrap-up:** Once the Dragon falls (or the team does!), GSR broadcasts a fun summary of everyone's contributions.
* **Smart Peeking HUD:** The tracker doesn't clutter your screen. It can be set to "Tab-Only," so it only pops up when you're checking the player list or when a split occurs.
* **World-Bound Stats:** All progress is saved directly to your world folder. If you need to take a break, your timer and stats will be right where you left them.

---

## 🛠 Installation & Compatibility

### Requirements
* **Minecraft Version:** 1.21.11
* **Fabric Loader:** [Latest Version](https://fabricmc.net/use/installer/)
* **Dependencies:** [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)

### Setup Instructions
1. **Client & Server:** Place `groupspeedrun.jar` and `fabric-api.jar` into your `mods` folder.
2. **Multiplayer (Essential / LAN / Servers):** Fully compatible with **Essential**. For the best experience, **everyone in the group** should have the mod installed locally.
3. **Verify:** Join the world and type `/gsr status`.

---

## ⌨️ Command Reference
Use `/gsr` to customize your group's experience.

| Command | What it does | Permission |
| :--- | :--- | :--- |
| `/gsr status` | Check your current HUD and game settings. | Everyone |
| `/gsr stats` | See a live preview of the fun stats collected so far. | Everyone |
| `/gsr toggle_hud` | Change when you see the timer (Always, Tab-Only, or Hidden). | Everyone |
| `/gsr scale_timer_hud` | Resize the timer UI (Values: 0.3 to 3.5). | Everyone |
| `/gsr scale_locate_hud`| Resize the locator bar (Values: 0.3 to 3.5). | Everyone |
| `/gsr toggle_shared_hp` | Turn the shared team heart bar ON or OFF. | Admin |
| `/gsr toggle_group_death`| Decide if one player's death resets the whole team. | Admin |
| `/gsr set_max_hp` | Change the total hearts for the group. | Admin |
| `/gsr easy_locate <type>` | Pins **Fortress, Bastion, Stronghold,** or **Ship**. | Admin / Post-Game |
| `/gsr reset` | Clear everything and start a brand new run together. | Admin / Post-Game |

---

## 📊 Fun Awards & Stat Tracking
GSR looks at how everyone played and hands out "Awards" at the end. Our **Unique Pass** system tries to make sure as many friends as possible get a shout-out!

| Stat Tag | Award Label | Description | Context |
| :--- | :--- | :--- | :--- |
| `dragon_warrior` | **Dragon Warrior** | Most damage dealt to the Ender Dragon. | Victory |
| `adc` | **Mob Masher** | Highest total damage dealt to mobs. | Both |
| `brew_master` | **Alchemist** | Most potions consumed. | Both |
| `builder` | **The Architect** | Most blocks placed and broken combined. | Both |
| `coward` | **The Pacifist** | Took the least damage (Failure Roasting). | Failure |
| `defender` | **Ironclad** | Highest armor rating reached. | Both |
| `good_for_nothing` | **The Tourist** | Player with the fewest advancements. | Failure |
| `healer` | **Medic** | Most health points regenerated. | Both |
| `killer` | **Serial Killer** | Total number of mobs killed. | Both |
| `pog_champ` | **Blaze Hunter** | Most high-value actions (Blaze Rods). | Both |
| `shuffler` | **Professional Shuffler** | Most time spent in inventories/chests. | Failure |
| `sightseer` | **Sightseer** | Longest distance traveled (Blocks). | Both |
| `tank` | **The Sponge** | Most damage taken. | Both |
| `weakling` | **Gentle Soul** | Dealt the least damage (Failure Roasting). | Failure |

---

## 📂 Data & Configuration

### File Locations
* **Global Visuals:** `.minecraft/config/gsr-config.json` (Scale, positions, and colors).
* **World Run State:** `[YourWorld]/groupspeedrun.json` (Current timer, splits, and pins).
* **Run History:** `.minecraft/config/groupspeedrun/history/` (JSON "scrapbook" of every finished run).

### Configuration Options (`GSRConfig.java`)

| Setting | Type | Default | Options / Range | Description |
| :--- | :--- | :--- | :--- | :--- |
| `hudMode` | int | `1` | `0` (Always), `1` (Tab-Only), `2` (Hidden) | Sets when the timer is visible. |
| `timerHudScale` | float | `1.0f` | `0.3` to `3.5` | Size of the timer on the screen. |
| `locateHudScale`| float | `0.95f`| `0.3` to `3.5` | Size of the structure tracker bar. |
| `timerHudOnRight`| bool | `true` | `true` / `false` | If false, timer moves to the left side. |
| `locateHudOnTop` | bool | `true` | `true` / `false` | If false, tracker moves to the bottom. |
| `groupDeathEnabled`| bool | `true` | `true` / `false` | One player's death fails the team run. |
| `sharedHealthEnabled`| bool | `false`| `true` / `false` | Syncs everyone to one health pool. |
| `maxHearts` | float | `10.0f` | `0.5` to `100.0` | Sets the team's total heart count. |
| `fortressColor` | hex | `#511515`| Any Hex Code | Bar color for Nether Fortresses. |
| `bastionColor` | hex | `#3C3947`| Any Hex Code | Bar color for Bastions. |
| `strongholdColor`| hex | `#97d16b`| Any Hex Code | Bar color for Strongholds. |
| `shipColor` | hex | `#A6638C`| Any Hex Code | Bar color for End Ships. |

---

## ⚙️ Technical Details
* **Auto-Start:** Timer triggers the moment the first player movement is detected after a `/gsr reset`.
* **Networking:** Uses `GSRConfigPayload` records and `CustomPayload` IDs to sync settings from server to client with a buffer limit of 32,767 to handle large data sets.

* **Sync Logic:** Uses a **"Host-First"** model. The server/host executes `GSREvents.onTick` every 50ms (20 TPS) and pushes updates to clients to ensure perfect synchronization.
* **Persistence:** Periodic autosave every 5 seconds (100 ticks) ensures that splits and stats are preserved even if the server stops unexpectedly.