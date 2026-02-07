# 🏁 Group Speedrun (GSR) — Fabric 1.21.1

A high-performance management suite for Minecraft speedruns. **Group Speedrun (GSR)** transforms Minecraft into a competitive racing environment, synchronizing timers, health, and structure tracking while providing deep post-run analytics.

---

## 🚀 Key Features

* **World-Aware Logic:** Detects "Fresh" vs "Resume" states to manage statistics automatically.
* **Integrated Auto-Splitter:** Records times for the Nether, Bastion, Fortress, The End, and the Dragon kill.
* **Smart HUD Visibility:** Features a 10-second "Split Pop-up," a smooth Tab-key peek, and an end-of-run permanent display.
* **Shared Survival:** Optional **Shared Health** (unified heart bar) and **Group Death** (one die, all die) mechanics.
* **Post-Run Analytics:** Detailed JSON reports and chat broadcasts that award titles based on performance.
* **Atomic Sync:** Uses custom networking to sync server configs to all clients in real-time.

---

## ⌨️ Command Reference
All management is handled via the `/gsr` base command.

| Command | Functionality | Permission |
| :--- | :--- | :--- |
| `/gsr status` | Shows HUD mode, Shared HP, Group Death, and Max Hearts. | Everyone |
| `/gsr stats` | Broadcasts a Live Data Frame of player stats to the chat. | Everyone |
| `/gsr toggle_hud` | Cycles visibility: **Always** → **Tab-Only** → **Hidden**. | Everyone |
| `/gsr timer_hud_side` | Swaps the timer between the **Left** and **Right** screen edges. | Everyone |
| `/gsr locate_hud_height` | Swaps the tracker bar between **Top** and **Bottom**. | Everyone |
| `/gsr scale_timer_hud [x]` | Resizes the timer UI (Values: 0.3 to 3.5). | Everyone |
| `/gsr scale_locate_hud [x]` | Resizes the locator compass (Values: 0.3 to 3.5). | Everyone |
| `/gsr toggle_shared_hp` | Syncs all players to a single unified health pool. | Admin |
| `/gsr toggle_group_death` | If ON, the entire team fails if one player dies. | Admin |
| `/gsr set_max_hp [val]` | Sets max hearts (Range: 0.5 - 100). | Admin |
| `/gsr easy_locate <type>` | Pins **Fortress, Bastion, Stronghold,** or **Ship**. | Admin / Run Over |
| `/gsr easy_locate clear` | Removes all active structure pins from the HUD. | Admin / Run Over |
| `/gsr reset` | Wipes gear, stats, advancements, and resets world time. | Admin / Run Over |

---

## 📊 Award & Stat Tracking
GSR uses a **"Unique Pass"** system: it recognizes as many individual players as possible before assigning multiple awards to the same person.

| Stat Tag | Award Label | Detail / Math | Context |
| :--- | :--- | :--- | :--- |
| `dragon_warrior` | **Dragon Warrior** | Damage dealt to the Ender Dragon. | **Victory** |
| `adc` | **ADC** | Total damage dealt (Points / 10). | **Both** |
| `killer` | **Serial Killer** | Total number of mobs killed. | **Both** |
| `healer` | **Healer** | Total health points regenerated (Points / 2). | **Both** |
| `tank` | **Tank** | Total damage taken (Points / 10). | **Both** |
| `defender` | **Defender** | Highest armor rating reached. | **Both** |
| `sightseer` | **Sightseer** | Total distance traveled (CM / 100). | **Both** |
| `pog_champ` | **Pog Champ** | High-value actions (e.g., Blaze Rods collected). | **Both** |
| `builder` | **Builder** | Sum of blocks broken and placed. | **Both** |
| `brew_master` | **Brew Master** | Number of potions consumed. | **Both** |
| `coward` | **Coward** | Player who took the **least** damage. | **Failure** |
| `weakling` | **Weakling** | Player who dealt the **least** damage. | **Failure** |
| `shuffler` | **Professional Shuffler** | Number of inventories/containers opened. | **Failure** |
| `good_for_nothing` | **Good for Nothing** | Player with the fewest advancements. | **Failure** |

---

## 📂 File Structure & Data

### 1. Global Config (`./config/gsr-config.json`)
Stores persistent settings like HUD scales and gameplay toggles. This is synced to the `GSRClient` on join and whenever settings change via custom packets.

### 2. Live World Stats (`./world/gsr_stats.json`)
Saves real-time data using `ConcurrentHashMap` to ensure that damage, blocks, and kills are preserved if the server restarts mid-run.

### 3. Run History (`./config/groupspeedrun/history/`)
Saves a permanent record of every attempt.
* **Naming:** `[RESULT]_[Date]_[PrimaryPlayer]_[World].json`
* **Contents:** Stores `final_time_ticks`, formatted time, and a full `awards` object containing all winner names and their numeric values.

---

## 🛠 Installation

### Requirements
* **Minecraft Version:** 1.21.1
* **Fabric Loader:** [Latest Version](https://fabricmc.net/use/installer/)
* **Dependencies:** [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)

### Setup Instructions
1. **Client Side:**
    - Install the Fabric Loader for 1.21.1.
    - Place the `groupspeedrun.jar` and `fabric-api.jar` into your `.minecraft/mods` folder.
2. **Server Side:**
    - Place the `groupspeedrun.jar` and `fabric-api.jar` into the server's `mods` folder.
    - Restart the server to generate the necessary configuration files.
3. **Verify:**
    - Join the server and type `/gsr status` to ensure the mod is active.

---

## ⚙️ Technical Details
* **Auto-Start:** Timer triggers the moment the first player movement is detected after a `/gsr reset`.
* **Networking:** Synchronized via `GSRConfigPayload` and scheduled on the main client thread to prevent race conditions during HUD rendering.
* **Reset Logic:** Performs a total world cleanup—clearing inventories, resetting hunger/fire, revoking all advancements, and resetting player statistics.
* **Thread Safety:** Statistical maps are handled via `ConcurrentHashMap` to prevent `ConcurrentModificationExceptions` during automated file saves.