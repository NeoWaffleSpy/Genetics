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
- **Identification**: every sample is "Unidentified" until identified at the Lab Station with an **Identification Reagent**. Unidentified samples can still be injected blind.
- **Gene Journal**: every species sampled and gene identified. New species show a notification, and milestones
  (4 and 8 species) teach the Copper Syringe and Genome Expansion recipes. `/genetics journal` shows it.
- **Lab Station** (`Server/Item/Items/Bench/Genetics_Lab_Station.json`): a one-slot station that only accepts
  syringes. Its custom screen (`Common/UI/Custom/Genetics/LabStation.ui`) shows the sample's source, Complexity,
  stat genes and effect genes, with drag & drop between the station and the inventory. Uses the Alchemy bench's
  model as a placeholder. Its tier is saved on the block; **upgrading to T2** (copper, iron, Life Essence) unlocks:
  - **Processing**: pick which genes of an identified raw sample to keep. Complexity is recalculated, and the cost
    comes from its **Complexity tier** (Simple, Moderate, Complex, Advanced, Exotic). The result is **genetic
    material**: injected exactly as it is, with less sickness, and never processed again.
  - **Splicing**: combine the genetic material in the station with one from your inventory. Each gene comes from one
    side at random, biased toward the higher Complexity; the screen shows every gene's odds. The cost uses the tier
    of the average Complexity, and the result is a raw sample to process again.
  - **Mutations**: a splice may roll a new mutation (about 2–6%), drawn by weight among those its tier allows.
    Mutations carry stats, effects and **behaviors** (triggers such as low health, being hurt or nightfall that run
    the shared actions). `/genetics mutation <id>` gives you one for testing.
  Creative mode pays no material costs, like vanilla bench upgrades.
- **Effects**: `Genetics_Speed` (+20% movement speed), `Genetics_NightVision` (a soft light around the player),
  `Genetics_Nausea`, and one `Genetics_Sickness_*` effect per sickness band.
- **Data** (`Server/Genetics/`): the Effect Codex (stat weights, effect Complexity), the Mutation Codex, the mob gene
  profiles, the Sickness Event Codex, the Complexity tiers (`ComplexityTiers/`), and `Balance/Default.json` (raw
  injection odds, sickness rates and bands, splicing odds, station upgrades). All are asset types, so other packs can add or override entries; edits hot-reload.

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
| `src/main/java/com/genetics/labstation/` | Lab Station interaction, custom screen and tier |
| `src/main/java/com/genetics/tier/` | Complexity tiers, cost Scaling rules, paying material costs |
| `src/main/java/com/genetics/process/` | Processing and splicing, and their screens |
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
