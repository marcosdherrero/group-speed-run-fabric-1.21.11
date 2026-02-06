# Group Speedrun (GSR) — Fabric 1.21.11

A high-performance, team-oriented management suite for Minecraft speedruns. **Group Speedrun (GSR)** transforms Minecraft into a competitive racing environment, synchronizing timers, health, and structure tracking across all players while providing deep post-run analytics.

---

## 🚀 Key Features

* **World-Aware Logic:** Unlike external timers, GSR is embedded in the world save. It detects "Fresh" vs "Resume" states to manage statistics automatically.
* **Integrated Auto-Splitter:** Automatically records times for the Nether, Bastion, Fortress, The End, and the Dragon kill.
* **Smart HUD Visibility:** The HUD features a 10-second "Split Pop-up," a smooth Tab-key peek, and an end-of-run permanent display.
* **Dynamic Compass:** A horizontal tracking bar that scales icons based on proximity and maps them to your field of view.
* **Shared Survival:** Optional **Shared Health** (unified heart bar) and **Group Death** (one die, all die) mechanics.
* **Post-Run "Roast" System:** Detailed JSON reports and chat broadcasts that award "Dragon Warrior" to winners or "The Disgrace" to losers.

---

## ⌨️ Command Reference
All management is handled via the `/gsr` base command.

| Command | Functionality | Permission |
| :--- | :--- | :--- |
| `status` | Shows HUD mode, Group Death/Shared HP status, and Max Hearts. | Everyone |
| `toggle_hud` | Cycles HUD: `Always Visible` → `Tab Only` → `Hidden`. | Everyone |
| `toggle_shared_hp` | Enables/Disables unified health pool for the team. | Admin |
| `toggle_group_death` | If ON, one player dying fails the entire run. | Admin |
| `set_max_hp [val]` | Sets group max hearts (0.5 - 100). Default is 10. | Admin |
| `toggle_hud timer_hud_side` | Swaps timer position between **LEFT** and **RIGHT**. | Everyone |
| `scale_timer_hud [x]` | Resizes timer (0.3 - 3.5). No value resets to 1.0. | Admin |
| `easy_locate <type>` | Toggles tracker for `fortress`, `bastion`, `stronghold`, or `ship`. | Everyone |
| `easy_locate clear` | Clears all active structure trackers. | Everyone |
| `reset` | **Master Reset:** Clears inv/advancements/stats and teleports to spawn. | Admin |

---

## ⏱ Milestone & Split Tracking
The `GSRSplitManager` monitors your progress in real-time.

| Milestone | Trigger Condition | UI Icon |
| :--- | :--- | :--- |
| **Nether** | Entering the Nether dimension. | §a✔ Nether |
| **Bastion** | Standing inside a Bastion Remnant structure. | §a✔ Bastion |
| **Fortress** | Standing inside a Nether Fortress structure. | §a✔ Fortress |
| **The End** | Entering the End dimension. | §a✔ The End |
| **Dragon** | Defeating the Ender Dragon (Freezes Timer). | §a✔ Dragon |

---

## 📊 Performance Statistics (Awards)
At the end of every run, players are evaluated based on their contribution.

| Stat Tag | Award Label | Data Tracked |
| :--- | :--- | :--- |
| `dragon_warrior` | **Dragon Warrior** | Most damage dealt to the Ender Dragon. |
| `pog_champ` | **Pog Champ** | Most Blaze Rods collected. |
| `adc` | **ADC** | Highest overall mob damage dealt. |
| `tank` | **Tank** | Highest total damage taken. |
| `shuffler` | **Professional Shuffler** | Most inventory/container opens (Roast). |
| `coward` | **Coward** | Least damage taken in a failed run (Roast). |

---

## 📂 File Structure & JSON Data
The mod ensures data integrity through persistent JSON files.

### 1. World Configuration (`./world/groupspeedrun.json`)
This file tracks the current state of the world, including all split times (in ticks), active structure coordinates, and gameplay toggles. It is synchronized to all clients via the **GSRNetworking** system.

### 2. Run History (`./config/groupspeedrun/history/`)
Saves a permanent record of every attempt.
* **Naming:** `{W/L}_{Date}_{Player}_{World}.json`
* **Contents:** Final time (ticks/formatted), Victory/Failure status, and the raw numerical values for every award.

---

## 🛠 Installation
1. Install **Fabric Loader** for **1.21.11**.
2. Place the `groupspeedrun.jar` and **Fabric API** into your `mods` folder.
3. Launch the server. The mod will generate necessary folders in the `config` and `world` directories.