# Genetics

A genetics mod for Hytale. Early development.

Built from the [Hytale plugin template](https://github.com/Kaupenjoe/Hytale-Example-Plugin) using the
[Hytale Gradle Plugin](https://github.com/AzureDoom/Hytale-Gradle-Plugin).

## Current content

- **Syringe** (`Server/Item/Items/Tools/Syringe.json`): left-click a mob to fill it. It becomes
  "<Mob>'s Genes" and stores a raw snapshot of the mob (id, model, stats, active effects) in its metadata.
  Model and icon are temporary placeholders.
- **Lab Station** (`Server/Item/Items/Bench/Lab_Station.json`): a one-slot station that only accepts syringes.
  Its custom screen (`Common/UI/Custom/Genetics/LabStation.ui`) shows the sample's max stats and status
  effects, with drag & drop between the station and the inventory. Uses the Alchemy bench's model as a placeholder.

## Layout

| Path | Purpose |
| --- | --- |
| `src/main/java/com/genetics/GeneticsPlugin.java` | Plugin entry point, registers custom interactions |
| `src/main/java/com/genetics/interaction/` | Custom interaction types |
| `src/main/java/com/genetics/genes/` | Gene samples stored on syringes |
| `src/main/java/com/genetics/labstation/` | Lab Station interaction and custom screen |
| `src/main/resources/Server/` | Server assets (items, translations) |
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
