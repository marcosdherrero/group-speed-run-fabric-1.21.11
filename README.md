# Group Speedrun (GSR) — Fabric 1.21.11

A high-performance, team-oriented management suite for Minecraft speedruns. **Group Speedrun (GSR)** transforms Minecraft into a competitive racing environment, synchronizing timers, health, and structure tracking across all players while providing deep post-run analytics.

---

## 🚀 Key Features

* **World-Aware Logic:** Embedded in the world save; detects "Fresh" vs "Resume" states to manage statistics automatically.
* **Integrated Auto-Splitter:** Automatically records times for the Nether, Bastion, Fortress, The End, and the Dragon kill.
* **Smart HUD Visibility:** Features a 10-second "Split Pop-up," a smooth Tab-key peek, and an end-of-run permanent display.
* **Dynamic Compass:** A horizontal tracking bar that scales icons based on proximity and maps them to your field of view.
* **Shared Survival:** Optional **Shared Health** (unified heart bar) and **Group Death** (one die, all die) mechanics.
* **Post-Run Analytics:** Detailed JSON reports and chat broadcasts that award titles to winners or call out the "Disgrace" in failures.

---

## ⌨️ Command & Configuration Reference
All management is handled via the `/gsr` base command.

| Command | Functionality | Default State | Permission |
| :--- | :--- | :--- | :--- |
| `status` | Displays active HUD modes, shared HP, and rule toggles. | N/A | Everyone |
| `stats` | Broadcasts a Live Data Frame (Tag, Player, Type, Value). | N/A | Everyone |
| `toggle_hud` | Cycles visibility: Always → Tab-Only → Hidden. | 1 (Tab-Only) | Everyone |
| `timer_hud_side` | Swaps the timer between the left and right screen edges. | Right | Everyone |
| `locate_hud_height` | Swaps the tracker bar between top and bottom positions. | Top | Everyone |
| `scale_timer_hud [x]` | Resizes the timer UI (0.3 to 3.5). | 1.0 | Admin |
| `scale_locate_hud [x]` | Resizes the locator compass (0.3 to 3.5). | 0.95 | Admin |
| `toggle_shared_hp` | Syncs all players to a single unified health pool. | OFF | Admin |
| `toggle_group_death` | If ON, any single player death ends the run. | ON | Admin |
| `set_max_hp [val]` | Sets the team's maximum heart capacity. | 10.0 | Admin |
| `easy_locate <type>` | Pins a structure's location to the HUD compass. | All OFF | Admin / Run Over |
| `easy_locate clear` | Removes all structure pins from the HUD. | N/A | Admin / Run Over |
| `reset` | Full world wipe: Clears gear, stats, and resets time to 0. | N/A | Admin / Run Over |
---

## ⏱ Milestone & Split Tracking
The `GSRSplitManager` monitors progress and triggers HUD animations upon completion.

| Milestone | Trigger Condition | UI Icon |
| :--- | :--- | :--- |
| **Nether** | Entering the Nether dimension. | §a✔ Nether |
| **Bastion** | Standing inside a Bastion Remnant structure. | §a✔ Bastion |
| **Fortress** | Standing inside a Nether Fortress structure. | §a✔ Fortress |
| **The End** | Entering the End dimension. | §a✔ The End |
| **Dragon** | Defeating the Ender Dragon (Freezes Timer). | §a✔ Dragon |

---

## 📊 Comprehensive Award & Stat Tracking (Alphabetical)
Every run generates a post-game report. The mod uses a "Unique Pass" system to ensure as many players as possible receive recognition.

| Stat Tag | Award Label | Detail of Tracking | Context |
| :--- | :--- | :--- | :--- |
| `adc` | **ADC** | Total damage dealt to all entities. | **Both** |
| `brew_master` | **Brew Master** | Number of potions consumed. | **Both** |
| `builder` | **Builder** | Sum of blocks broken and placed. | **Both** |
| `coward` | **Coward** | Player who took the **least** damage. | **Failure** |
| `defender` | **Defender** | Highest armor rating reached. | **Both** |
| `dragon_warrior` | **Dragon Warrior** | Damage dealt to the Ender Dragon. | **Victory** |
| `good_for_nothing` | **Good for Nothing** | Player with fewest advancements. | **Failure** |
| `healer` | **Healer** | Total health points regenerated. | **Both** |
| `killer` | **Serial Killer** | Total number of mobs killed. | **Both** |
| `pog_champ` | **Pog Champ** | Number of Blaze Rods collected. | **Both** |
| `shuffler` | **Shuffler** | Total inventory/container opens. | **Failure** |
| `sightseer` | **Sightseer** | Total distance traveled (Blocks). | **Both** |
| `tank` | **Tank** | Total damage taken from all sources. | **Both** |
| `weakling` | **Weakling** | Player who dealt the **least** damage. | **Failure** |

---

## 📂 File Structure & JSON Data

### 1. World Configuration (`./world/groupspeedrun.json`)
Tracks the current state of the world, including split times, structure coordinates, and gameplay toggles. Synchronized to all clients via **GSRNetworking**. Visual preferences (scaling, colors) are preserved during resets, while run-data is cleared.

### 2. Run History (`./config/groupspeedrun/history/`)
Saves a permanent record of every attempt.
* **Naming:** `{W/L}_{Date}_{Player}_{World}.json`
* **Contents:** Final time (ticks/formatted), Victory/Failure status, and all awarded stats.

---

## 🛠 Installation
1.  Install **Fabric Loader** for **1.21.11**.
2.  Place the `groupspeedrun.jar` and **Fabric API** into your `mods` folder.
3.  Launch the server. Folders generate in `config` and `world` on startup.