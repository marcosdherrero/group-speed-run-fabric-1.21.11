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

### 🛠 Command Reference

| Command | Sub-Command | Description | Access |
| :--- | :--- | :--- | :--- |
| **`/gsr status`** | — | Displays current HUD, Shared HP, and Group Death settings. | Everyone |
| **`/gsr stats`** | — | Broadcasts current live stats (Damage, Distance, etc.) to chat. | Everyone |
| **`/gsr hud`** | `visibility_toggle` | Cycles visibility: **ALWAYS**, **TAB-ONLY**, or **HIDDEN**. | Everyone |
| | `side_toggle` | Swaps the Timer HUD between the **LEFT** and **RIGHT** side. | Everyone |
| | `height_toggle` | Swaps the Locator Bar between the **TOP** and **BOTTOM**. | Everyone |
| | `scale <value>` | Resizes all GSR HUD elements (Range: 0.3 - 3.5). | Everyone |
| **`/gsr settings`**| `shared_hp_toggle` | Toggle if the team shares a single health pool. | Admin |
| | `group_death_toggle`| Toggle if one player's death fails the run for all. | Admin |
| | `max_hp <amount>` | Sets the global heart limit for all players. | Admin |
| **`/gsr locate`** | `<type>_toggle` | Pins **Fortress, Bastion, Stronghold,** or **Ship** to HUD. | Admin/Post-Game |
| | `clear` | Removes all active structure pins from the HUD. | Admin/Post-Game |
| **`/gsr pause`** | — | Freezes the run timer for all players. | Admin |
| **`/gsr resume`** | — | Resumes the run timer. | Admin |
| **`/gsr reset`** | — | Wipes stats, inventories, advancements, and restarts world. | Admin/Post-Game |

---

## 📊 Fun Awards & Stat Tracking
GSR looks at how everyone played and hands out "Awards" at the end. Our **Unique Pass** system tries to make sure as many friends as possible get a shout-out!

| Stat Tag | Award Label | Description | Context |
| :--- | :--- | :--- | :--- |
| `dragon_warrior` | **§5🐉 Dragon Warrior** | Most damage dealt to the Ender Dragon. | Victory |
| `adc` | **§6🏹 ADC** | Highest total damage dealt to mobs. | Both |
| `killer` | **§4💀 Serial Killer** | Total number of mobs killed. | Both |
| `tank` | **§4❈ Tank** | Most damage taken. | Both |
| `defender` | **§b🛡 Defender** | Highest armor rating reached. | Both |
| `healer` | **§d❤ Healer** | Most health points regenerated. | Both |
| `brew_master` | **§b🧪 Brew Master** | Most potions consumed. | Both |
| `pog_champ` | **§e🔥 Pog Champ** | Most high-value actions (Blaze Rods). | Both |
| `builder` | **§2🔨 Builder** | Most blocks placed and broken combined. | Both |
| `sightseer` | **§f👣 Sightseer** | Longest distance traveled (Blocks). | Both |
| `shuffler` | **§3🗃 Shuffler** | Most chests opened. | Failure |
| `coward` | **§e🏃 Coward** | Took the least damage (Failure Roasting). | Failure |
| `weakling` | **§f🍼 Weakling** | Dealt the least damage (Failure Roasting). | Failure |
| `good_for_nothing` | **§8⚖ Carried** | Player with the fewest advancements. | Failure |


---

## 📂 Data & Configuration

### 📂 File Locations

| Category | File Path | Description |
| :--- | :--- | :--- |
| **Player Preferences** | `config/groupspeedrun_player.txt` | Global visual settings (HUD scale, positions, and modes). |
| **World Run State** | `[YourWorld]/data/groupspeedrun.txt` | World-specific data (Timer, Shared HP, and active pins). |
| **Run History** | `GSR_History/` | Root directory folder containing JSON results for every finished run. |

---

### 📝 Storage Details

* **Global Visuals (`groupspeedrun_player.txt`)**: Managed by `GSRConfigPlayer`. This file persists across different worlds and servers, storing your individual HUD scaling and alignment preferences.
* **World Data (`groupspeedrun.txt`)**: Managed by `GSRConfigWorld`. This is stored inside the specific world save folder (`/data/`), allowing the run state and structure coordinates to be tied to the map itself.
* **History Scrapbook (`GSR_History/`)**: Managed by `GSRRunHistoryManager`. Upon a Victory or Failure, a JSON file is generated in this root-level folder. Filenames follow the schema: `WorldName_Result_Date_PlayerName.json`.

### Configuration Options (`GSRConfig.java`)

| Setting | Type | Default | Options / Range | Location | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `hudMode` | int | `1` | `0` (Always), `1` (Tab), `2` (Hidden) | Player | Sets when the timer is visible on screen. |
| `timerHudScale` | float | `1.0f` | `0.3` to `3.5` | Player | Size of the timer text. |
| `locateHudScale` | float | `1.0f` | `0.3` to `3.5` | Player | Size of the structure tracker bar. |
| `timerHudOnRight` | bool | `true` | `true` / `false` | Player | If false, timer moves to the left side. |
| `locateHudOnTop` | bool | `true` | `true` / `false` | Player | If false, tracker moves to the bottom. |
| `barWidth` | int | `100` | — | Player | Width of the structure locator bar. |
| `barHeight` | int | `4` | — | Player | Height of the structure locator bar. |
| `maxDist` | int | `1000` | — | Player | Distance at which structure scaling begins. |
| `groupDeathEnabled` | bool | `true` | `true` / `false` | World | One player's death fails the team run. |
| `sharedHealthEnabled` | bool | `false` | `true` / `false` | World | Syncs everyone to one health pool. |
| `maxHearts` | float | `10.0f` | `0.5` to `100.0` | World | Sets the team's total heart count. |
| `fortressColor` | hex | `#511515` | Any Hex Code | World | Bar color for Nether Fortresses. |
| `bastionColor` | hex | `#3C3947` | Any Hex Code | World | Bar color for Bastions. |
| `strongholdColor` | hex | `#97d16b` | Any Hex Code | World | Bar color for Strongholds. |
| `shipColor` | hex | `#A6638C` | Any Hex Code | World | Bar color for End Ships. |

---

## ⚙️ Technical Details
* **Auto-Start:** Timer triggers the moment the first player movement is detected after a `/gsr reset`.
* **Networking:** Uses `GSRConfigPayload` records and `CustomPayload` IDs to sync settings from server to client with a buffer limit of 32,767 to handle large data sets.

* **Sync Logic:** Uses a **"Host-First"** model. The server/host executes `GSREvents.onTick` every 50ms (20 TPS) and pushes updates to clients to ensure perfect synchronization.
* **Persistence:** Periodic autosave every 5 seconds (100 ticks) ensures that splits and stats are preserved even if the server stops unexpectedly.
