# HereLoggy

A premium [Paper](https://papermc.io) Minecraft plugin for **fully automated lumberjacking and tree felling** — systematically discover trees, execute recursive whole-tree clearing, dynamically manage inventory storage, swap and restock axes, auto-feed, defend against hostiles, replant saplings, and boost leveling with **AuraSkills** integration!

---

## Features

- **Double-Axe Selection & Path Mapping**:
  - Selection is quick and simple: hold any Axe, **Sneak + Right-Click** to set **Point A** and **Point B** bounding coordinates.
  - The plugin automatically triggers an **asynchronous scan** of the area to map walkable blocks and discover tree locations, avoiding server lag.

- **Dynamic Auto-Chopping Methods**:
  - **Whole Tree Mode (BFS)**: Uses a smart recursive Breadth-First Search (BFS) with a radial adjacency of 2 to traverse and break the entire tree structure (up to a 512-block safety limit) from a single base chop.
  - **Reachable Mode**: Only chops wood blocks within standard player reach height (up to eye-height + 3 blocks), respecting traditional survival mechanics.

- **Automatic Foliage & Obstacle Clearing**:
  - Periodically scans a local radius around the player to **break leaves, vines, and nether foliage** automatically.
  - Keeps walking paths fully clear to prevent the player from getting stuck or trapped.

- **Smart Tool Preservation & Restocking**:
  - Monitors axe durability in real-time. Automatically swaps active quickslots to another axe in the inventory to prevent tool breakage.
  - **Tool Supply Restocking**: Link a **Tool Supply Chest** with spare axes. If durability drops below a user-defined threshold and no spares exist on hand, the player teleports to the chest, deposits the worn axe, withdraws a fresh one, and teleports back to resume lumberjacking instantly!

- **Advanced Storage Routing & Keep/Trash Chests**:
  - Link a **Keep Chest** (for valuable logs) and a **Trash Chest** (for saplings, leaves, extra drops) using the setup wizard.
  - When the player's inventory becomes full, the plugin automatically routes items to deposit chests. If chests are full or unconfigured, the session pauses safely rather than dropping items.
  - Allows configuring routing destinations (Keep, Trash, or Keep on Person) per wood type.

- **Integrated Hostile Mob Defense & Auto-Feeding**:
  - Monitors surrounding entities and **actively defends the player** from hostile mobs, pausing wood-cutting for weapon cooldown ticks.
  - Automatically consumes food items from the inventory/offhand when hunger drops below saturation limits.

- **Vacuum Drop Attraction & Shared Mending Repair**:
  - Automatically vacuum-attracts all dropped items and experience orbs within a **6-block radius** around broken blocks.
  - Directs collected block-break experience straight to repairing hotbar tools enchanted with **Mending** (prioritizing the most damaged axe), allocating leftover XP to the player.

- **Automatic Sapling Replanting**:
  - If enabled, automatically places matching saplings on soil, dirt, nylium, or soul blocks immediately after a tree is felled, as long as saplings are available in the player's inventory or off-hand.

- **AuraSkills Integration**:
  - Fully supports AuraSkills (formerly Aurelium Skills).
  - Award-winning **Foraging XP** is granted for broken log blocks and attracted experience orbs.
  - Player movement velocity multiplier scales dynamically based on their current Foraging Level (`1.0 + Foraging Level * 0.01`).

- **Interactive Configuration GUI**:
  - A beautiful, full-featured GUI to toggle active states, replanting preferences, and inventory routing destinations for 10 tree types: **Oak, Spruce, Birch, Jungle, Acacia, Dark Oak, Mangrove, Cherry, Crimson, and Warped**.

---

## Commands

All commands can be run with `/hl` instead of `/hereloggy`.

| Command | Description | Permission |
|---------|-------------|------------|
| `/hereloggy start` (or `/hl start`) | Start auto-chopping (triggers area scan first if selection exists but has not been scanned) | `hereloggy.use` |
| `/hereloggy stop` (or `/hl stop`) | Stop active chopping session and display statistics | `hereloggy.use` |
| `/hereloggy restart` (or `/hl restart`) | Resume auto-chopping from the last paused block index | `hereloggy.use` |
| `/hereloggy config` (or `/hl config`) | Open interactive GUI to configure individual tree settings | `hereloggy.config` |
| `/hereloggy setup` (or `/hl setup`) | Start interactive chat setup wizard for chests and thresholds | `hereloggy.setup` |
| `/hereloggy clear` (or `/hl clear`) | Clear point selections, scans, and player setups | `hereloggy.use` |

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `hereloggy.use` | Allows use of `/hereloggy` command | `true` |
| `hereloggy.setup` | Allows use of the setup wizard | `true` |
| `hereloggy.config` | Allows use of the tree configuration GUI | `true` |

---

## How to Use

### 1. Bounding Area Selection
1. Hold any Axe in your main hand.
2. **Sneak + Right-Click** a block to mark **Point A**.
3. **Sneak + Right-Click** another block to mark **Point B** (defining the logging boundaries).
4. The plugin will map out walkable paths and announce that it is ready.

### 2. Interactive Setup Wizard
1. Run `/hl setup`.
2. **Step 1**: Hold an Axe and **Sneak + Right-Click** a chest where you want to deposit **Trash** (saplings, leaves, etc.).
3. **Step 2**: **Sneak + Right-Click** a chest where you want to deposit **Valuable Logs** (Keep Chest).
4. **Step 3**: **Sneak + Right-Click** a chest where you store **Spare Axes** (Tool Supply), or type `skip` in chat.
5. **Step 4**: Enter an axe durability threshold (e.g. `12`) or type `skip` (defaults to `10`).
6. Setup is complete! Your configuration will be saved in `plugins/HereLoggy/setup-configs/{playerId}.yml`.

### 3. Tree Configuration GUI
1. Run `/hl config` to open the main menu.
2. Under the main menu:
   - Click the **Chopping Method** icon at the bottom center to toggle between **Whole Tree** (BFS recursive felling) and **Reachable** (reach-height felling).
   - Click on any of the 10 supported **Logs** (Oak, Spruce, Birch, Jungle, Acacia, Dark Oak, Mangrove, Cherry, Crimson, Warped) to access their options.
3. Under the tree options menu:
   - **Chopping Status**: Enable or disable felling for this tree type.
   - **Replant Saplings**: Toggle automatic sapling replanting.
   - **Route Destination**: Toggle between routing logs to the **Trash Chest**, **Keep Chest**, or keeping them in your **Inventory**.
4. Settings are immediately saved to `plugins/HereLoggy/player-configs/{playerId}.yml`.

### 4. Running the Bot
1. Stand near the selected bounding area.
2. Run `/hl start` to begin auto-chopping!
3. The plugin will walk the safe generated path layer-by-layer, clear foliage, fell discovered trees, repair mending tools, replant saplings, feed, defend against hostiles, and dump full inventories into chests automatically.
4. If you need to stop, run `/hl stop`. A detailed activity summary will show logs collected, foliage cleared, saplings replanted, and axes replaced.
5. Use `/hl restart` to resume from where you left off.

---

## Supported Tree Types

HereLoggy natively recognizes and supports:
* **Overworld**: Oak, Spruce, Birch, Jungle, Acacia, Dark Oak, Mangrove, Cherry
* **Nether**: Crimson Stem, Warped Stem

---

## Building from Source

Build the plugin jar using Gradle:
```bash
./gradlew build
```
The compiled JAR will be saved in the `build/libs/` directory.

---

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
