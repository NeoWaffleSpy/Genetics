# Genetics

A genetics mod for Hytale. Early development.

Built from the [Hytale plugin template](https://github.com/Kaupenjoe/Hytale-Example-Plugin) using the
[Hytale Gradle Plugin](https://github.com/AzureDoom/Hytale-Gradle-Plugin).

## Current content

- **Syringe** (`Server/Item/Items/Tools/Genetics_Syringe.json`):
  - **Left-click a mob** with an empty syringe to draw a raw sample. Genes are rolled from the mob's gene
    profile, and the syringe becomes "<Mob>'s Genes".
  - **Right-click with a filled syringe** to inject it into yourself. Each gene of a raw sample may come out
    as-is, weakened, dropped or turned into a drawback; the higher the Complexity, the worse the odds.
    Temporary effects start right away; stat genes and permanent effects become your **active genome**,
    replacing the previous one. The syringe is emptied.
  - **Right-click with an empty syringe** to copy your active genome into it, as a raw sample.
  - Model and icon are temporary placeholders.
- **Active genome**: saved on the player, kept through death and re-applied after respawn.
- **Genetic Sickness**: every injection adds sickness (Complexity × 4, less for processed samples). It decays over
  about 3 minutes from the top, is kept through death, and its band (Mild, Moderate, Severe) shows as an effect icon
  with the time left. While sick, random events fire: hiccups, the source mob's call, sneezes, nausea.
- **Genome Capacity**: the most Complexity your body can carry (20 at the start). The Lab Station shows it next to a
  sample's Complexity. Injecting a genome over capacity asks for confirmation, then degrades it until it fits
  (mutations, then permanent effects, then stats) and pushes you into severe sickness. A **Genome Expansion** raises
  it for good (the first one, Moderate, to 45).
- **Syringe tiers**: Crude, Copper, Thorium and Adamantite. Stronger mobs need better syringes.
- **Identification**: every sample is "Unidentified" until processed at the Lab Station (see below). Unidentified samples can still be injected blind.
- **Stronger mobs, stronger genes**: predators and mythic creatures (bears, sabertooth, raptor, yeti, rex) roll bigger stats and higher-tier buffs (Meat Buff T2/T3, Regeneration T2, Stamina T2).
- **Catalogue and Science**: every creature, gene, buff and mutation you find. Discoveries pay **Science**
  (placeholder name): first sample of a species +3, each new gene +1, a completed species +5, a new buff +2, a new
  mutation +10 (`Balance/Default.json`). `/genetics journal` sums it up.
- **Research**: a tech tree paid with Science (`Server/Genetics/Research/`, one file per node) in four branches: Lab
  (Splicing, Make Permanent, Genetic Library, which also teaches the Gene Enhancer), Reagents (Gene Stabilizer), Syringes (Copper,
  Thorium, Adamantite) and Genome (Moderate, Complex, Advanced and Exotic expansions: capacity 45, 80, 130, 200; then the Supreme expansion,
  +50 each use up to a hard cap of 10 000, the first one lifting you to at least 250). Nodes teach recipes and turn on Lab features; the items themselves still cost materials.
  It replaces the old Journal milestones (players who had them keep the matching nodes).
- **Lab Station** (`Server/Item/Items/Bench/Genetics_Lab_Station.json`): a one-slot station that only accepts
  syringes. Its custom screen (`Common/UI/Custom/Genetics/LabStation.ui`) is one panel with four tabs, **Process,
  Splice, Catalogue and Research**, in the mod's look (vanilla panels with a lab-teal accent and helix ornament).
  Process and Splice keep the sample slot and the inventory on screen, with drag & drop between them. Uses the
  Alchemy bench's model as a placeholder. Its tier is saved on the block.
  - **Processing** (T1 for Simple Complexity, T2 for Moderate): reveals a raw sample's genes, which is how samples are
    identified, and lets you tick the ones to keep. Complexity is recalculated, and the cost is one Identification
    Reagent plus the materials of its **Complexity tier** (Simple, Moderate, Complex, Advanced, Exotic). The result
    is **genetic material**: injected exactly as it is, with less sickness, and never processed again.
    A capacity gauge shows the result against your Genome Capacity, and genes you have never found are tagged NEW.
  - **Make permanent** (researched): a temporary effect spliced up to 30:00 or more can be made permanent while
    processing, for its permanent Complexity and a **Gene Stabilizer** (recipe researched, crafted at the Workbench).
  - **Upgrading to T2** (copper, iron, Life Essence, once Splicing is researched) unlocks Moderate processing and
    splicing. The Splice tab offers it.
  - **Splicing** (researched, T2): genetic material A in the station plus B dragged from your inventory into the
    second slot (B stays in your inventory until the splice uses it). The two are added
    together: shared stats sum their percentages, shared temporary effects sum their timers (and cost Complexity in
    proportion), and genes only one side has carry over. The screen shows A, B and the result, with a tickbox per
    gene; the same buff at two tiers (Meat Buff T1 and T2) can't both be kept. The cost uses the tier of the average
    Complexity, and the result is an unidentified sample: processing reveals it, named after its tier ("Moderate
    Spliced Gene").
  - **Genetic Library** (researched, T3): extract one gene from genetic material into your library (the material
    is used up; stats and effects also cost **Gene Enhancers**, one per 10 Complexity). The library keeps one entry
    per gene at its best value. In the **Library** tab, tick genes and choose how much of each to use (0 to its
    best value, with a slider or by typing it) to combine them into new genetic material in an empty syringe; drawbacks lower Complexity, and
    several mutations multiply the cost (x3 each). `/genetics library` lists it.
  - **Mutations**: a splice may roll a new mutation (about 2–6%), drawn by weight among those its tier allows.
    Mutations carry stats, effects and **behaviors** (triggers such as low health, being hurt or nightfall that run
    the shared actions). `/genetics mutation <id>` gives you one for testing.
  Creative mode pays no material costs, like vanilla bench upgrades.
- **Effects**: `Genetics_Speed` (+20% movement speed), `Genetics_NightVision` (a soft light around the player),
  `Genetics_Nausea`, and one `Genetics_Sickness_*` effect per sickness band.
- **Data** (`Server/Genetics/`): the Effect Codex (stat weights, effect Complexity), the Mutation Codex, the mob gene
  profiles, the Sickness Event Codex, the Complexity tiers (`ComplexityTiers/`), and `Balance/Default.json` (raw
  injection odds, sickness rates and bands, splicing, station upgrades). All are asset types, so other packs can add or override entries; edits hot-reload.

## Layout

| Path | Purpose |
| --- | --- |
| `src/main/java/com/genetics/GeneticsPlugin.java` | Plugin entry point, registers custom interactions |
| `src/main/java/com/genetics/interaction/` | Custom interaction types |
| `src/main/java/com/genetics/genome/` | Genomes: format, sampling, Complexity, injection, active genome |
| `src/main/java/com/genetics/codex/` | Loaders for the Codices and mob gene profiles |
| `src/main/java/com/genetics/effect/` | Behavior of custom effects |
| `src/main/java/com/genetics/action/` | Shared actions (ApplyEffect, PlaySound, Impulse, …) used by data files |
| `src/main/java/com/genetics/sickness/` | Genetic Sickness: stat helpers, state, bands, decay and events |
| `src/main/java/com/genetics/labstation/` | Lab Station interaction, tier, and its tabbed screen (one class per tab) |
| `src/main/java/com/genetics/journal/` | The Catalogue (saved as the Gene Journal) and Science rewards |
| `src/main/java/com/genetics/research/` | Research nodes, buying them and the features they unlock |
| `src/main/java/com/genetics/library/` | The Genetic Library: entries, extracting, combining |
| `src/main/java/com/genetics/tier/` | Complexity tiers, cost Scaling rules, paying material costs |
| `src/main/java/com/genetics/process/` | Processing and splicing rules |
| `src/main/java/com/genetics/mutation/` | Mutation behaviors and the system that runs them |
| `src/main/resources/Server/` | Server assets (items, effects, translations, Genetics data) |
| `src/main/resources/Common/UI/Custom/Genetics/` | Custom UI layouts |
| `gradle.properties` | Mod metadata; `manifest.json` is generated from it on every build |

## Development

```bash
# Generate decompiled sources for your IDE and download the game assets
./gradlew setupHytaleDev

# Launch a local server with this plugin loaded
./gradlew runServer

# Launch with a debugger attached and hot swap enabled
./gradlew runServer -Ddebug=true -Dhotswap=true
```

The first run asks you to sign in with your Hytale account so the assets can be downloaded.
Run `./gradlew hytaleDoctor` if something isn't resolving correctly.

Don't edit `src/main/resources/manifest.json` by hand. Change `gradle.properties` instead.

To use your installed copy of the game instead of downloading assets, set this in `%USERPROFILE%/.gradle/gradle.properties`:

```properties
hytale_home=path/to/Hytale
```

See the [plugin wiki](https://github.com/AzureDoom/Hytale-Gradle-Plugin/wiki) for all options.
