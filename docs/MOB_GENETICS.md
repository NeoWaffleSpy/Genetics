# Mob genetics: species, identities and objectives

> Generated from `Server/Genetics/MobProfiles` (one file per species). Numbers are first-pass and meant to be
> tuned in playtests. `GameRolesCoverageTest` fails when a game update adds a creature without a profile, and
> `ScienceEconomyTest` keeps the Science economy inside its band.

## Science economy

| | Science |
| --- | --- |
| Research tree, every node | **1300** |
| 264 creatures: first sample +3, +1 per gene, +3 when complete | 2638 |
| 24 buffs, +2 each | 48 |
| 24 mutations, +4 each | 96 |
| **Everything discoverable** | **2782** (2.14x the tree) |

Every creature is its own Catalogue entry, so exploring and sampling all of them is the long-term goal. Tamed,
young, patrol, dungeon, coffer and boss-phase variants fold into their creature. Creatures of one gene profile (a
"gene family") share its genes. Research costs are scaled so that everything discoverable pays about twice the
tree. Unfinished mobs (placeholder models, the Voidtaken Grizzly) are left out.

## Scaling rules

| Where | Stat rolls (single gene) | Syringe | Buffs |
| --- | --- | --- | --- |
| Zone 1 passive | 3–10% (Oxygen 8–25%) | Crude (1) | Tier 1 food buffs, small immunities |
| Zone 1 hostile, Zone 2 | 3–9% | Copper (2) | Tier 1–2, Hide T1, Poison immunity |
| Zone 3 | 5–10% | Thorium (3) | Tier 2, Freeze immunity, Might T1 |
| Zone 4, elites | 7–16% | Adamantite (4) | Tier 2–3, Fire immunity, Hide T2, Might T2 |
| Objectives and bosses | 10–30% | Thorium or Adamantite | Tier 3, Might T3, best permanent effects (25–50% chance) |

Permanent effects now only roll where the player is likely to have the capacity for them (Night Vision 40 on
bats, at a 20% chance, is the one Zone 1 exception), instead of Zone 1 frogs and bats overflowing the starting 20.

## Species

Stats are `min–max %` (one roll per `StatRolls`); drawbacks show their chance; permanent effects show their chance.


### Zone 0 - Oceans

| Species | Mobs | Syringe | Stats | Drawbacks | Buff (temporary) | Permanent |
| --- | --- | --- | --- | --- | --- | --- |
| **Sea Hunters** | Blue Jellyfish, Cyan Jellyfish, Frostgill, Green Jellyfish, Hammerhead Shark, Man Of War, Moray Eel, Red Jellyfish +2 more | 2 | Oxygen 15–30, Health 4–8 (x2) | Mana −5–8 (40%) | Might T1 2:00 |  |
| **Humpback Whale** ★ | Humpback Whale | 3 | Oxygen 35–60, Health 8–14 (x2) | Stamina −5–8 (50%) | Regen T2 4:00 |  |

### Zone 1

| Species | Mobs | Syringe | Stats | Drawbacks | Buff (temporary) | Permanent |
| --- | --- | --- | --- | --- | --- | --- |
| **Fish** | Black Piranha, Blue Tang, Bluegill, Catfish, Chevron Tang, Clownfish, Lemon Peel Tang, Minnow +6 more | 1 | Oxygen 10–25 | Stamina −3–5 (30%) | Stamina Regen 2:00 |  |
| **Crabs and Shellfish** | Black Trilobite, Crab, Lava Coelacanth, Lobster, Trilobite | 1 | Health 3–7, Oxygen 8–15 (x2) | Stamina −3–6 (30%) | Hide T1 2:00 |  |
| **Birds** | Bluebird, Crow, Flamingo, Greenfinch, Hawk, Parrot, Penguin, Raven +4 more | 1 | Stamina 3–6, Oxygen 6–12 | Health −3–5 (30%) | Fall Immunity 2:30 |  |
| **Small Critters** | Bunny, Frog, Frog_Blue, Frog_Orange, Gecko, Meerkat, Mouse, Rabbit +1 more | 1 | Stamina 5–10, Oxygen 8–15 | Health −3–5 (30%) | Speed T1 2:00 |  |
| **Spirits** | Cactee, Ember Spirit, Fen Stalker, Frost Spirit, Hatworm, Living Spark, Root Spirit, Thunder Spirit | 2 | Mana 5–12, Health 2–5 (x2) | Stamina −3–6 (40%) | Environmental Immunity 2:30 | Environmental Immunity (10%) |
| **Goblins** | Coffer_Goblin_Burner, Coffer_Goblin_Guardian, Coffer_Goblin_Lobber, Coffer_Goblin_Miner, Coffer_Goblin_Scrapper, Coffer_Goblin_Turret, Goblin Burner, Goblin Guardian +9 more | 2 | Stamina 3–7, Mana 2–5 (x2) | Health −3–6 (40%) | Fire Immunity 2:00 |  |
| **Trorks** | Trork, Trork Brawler, Trork Chieftain, Trork Elder, Trork Guard, Trork Hunter, Trork Mauler, Trork Sentry +2 more | 2 | Health 5–9, Stamina 4–7 (x2) | Mana −6–10 (40%) | Vitality T1 3:00 |  |
| **Kweebecs and Klops** | Klops Gentleman, Klops Merchant, Klops Miner, Klops_Merchant_Wandering, Kweebec Elder, Kweebec Razorleaf, Kweebec Rootling, Kweebec Sapling +5 more | 1 | Health 3–6, Mana 3–6 (x2) | Oxygen −8–12 (30%) | Regen T1 2:30 |  |
| **Goblin Glutton** ★ | Coffer_Goblin_Glutton, Goblin Glutton | 3 | Health 12–18 | Stamina −6–10 (90%) | Regen T2 4:00 |  |
| **Sheep and Goats** | Goat, Kid, Lamb, Mouflon, Mouflon Lamb, Ram, Ram Lamb, Sheep | 1 | Health 3–6, Stamina 3–6 (x2) | Oxygen −8–12 (30%) | Freeze Immunity 2:00 |  |
| **Cattle** | Bison, Bison Calf, Calf, Cow | 1 | Health 4–9 | Stamina −3–5 (30%) | Regen T1 3:00 |  |
| **Poultry** | Chick, Chicken, Desert Chick, Desert Chicken, Duck, Pigeon, Skrill, Skrill Chick +2 more | 1 | Stamina 3–7 | Health −3–5 (30%) | Fall Immunity 2:00 |  |
| **Swine** | Boar, Boar Piglet, Pig, Piglet, Warthog, Warthog Piglet, Wild Pig, Wild Piglet | 1 | Health 3–7, Stamina 2–5 (x2) | Mana −4–8 (30%) | Vitality T1 3:00 |  |
| **Wolves and Foxes** | Black Wolf, Fox, Hunting Wolf, Hyena, White Wolf, Wolf_Outlander_Priest, Wolf_Outlander_Sorcerer, Wolf_Trork_Shaman | 2 | Health 3–7, Stamina 3–7 (x2) | Mana −8–10 (40%) | Vitality T1 3:00 |  |
| **Grazers and Mounts** | Antelope, Camel, Camel Calf, Doe, Foal, Horse, Moose Bull, Moose Cow +3 more | 1 | Stamina 6–11, Health 3–6 (x2) | Mana −5–8 (30%) | Endurance T1 3:00 |  |
| **Hedera** ★ | The Hedera | 3 | Mana 12–18, Health 8–12 (x2) | Stamina −5–8 (50%) | Regen T3 4:00 | Regen T1 (20%) |
| **Bats and Owls** | Bat, Brown Owl, Ice Bat, Snowy Owl | 1 | Stamina 2–5 | Health −3–5 (30%) | Night Vision 3:00 | Night Vision (20%) |
| **Bears** | Grizzly Bear, Polar Bear | 2 | Health 5–9, Stamina 3–6 (x2) | Mana −6–10 (40%) | Regen T2 3:00 |  |
| **Skeletons** | Burnt Skeleton Alchemist, Burnt Skeleton Archer, Burnt Skeleton Gunner, Burnt Skeleton Knight, Burnt Skeleton Lancer, Burnt Skeleton Soldier, Burnt Skeleton Wizard, Frost Skeleton Archer +34 more | 2 | Oxygen 20–40, Mana 3–6 (x2) | Health −4–8 (50%) | Poison Immunity 2:30 |  |
| **Rats and Spiders** | Cave Spider, Molerat, Rat, Silk Larva, Spider, Spider_Cave_Ambush, Spider_Cave_Spiderling | 1 | Health 2–5, Stamina 2–5 (x2) | Mana −4–8 (40%) | Poison Immunity 2:00 |  |
| **Feastmaster** ★ |  | 3 | Health 18–28 | Stamina −6–10 (60%) | Regen T3 5:00 | Poison Immunity (50%) |

### Zone 2

| Species | Mobs | Syringe | Stats | Drawbacks | Buff (temporary) | Permanent |
| --- | --- | --- | --- | --- | --- | --- |
| **Feran** | Feran Burrower, Feran Civilian, Feran Cub, Feran Longtooth, Feran Sharptooth, Feran Windwalker | 2 | Stamina 5–9, Mana 2–5 (x2) | Health −3–6 (40%) | Speed T2 2:30 |  |
| **Scarak** | Scarak Defender, Scarak Fighter, Scarak Imperial Guard, Scarak Louse, Scarak Seeker | 2 | Health 4–8, Stamina 3–6 (x2) | Oxygen −8–12 (40%) | Hide T1 2:30 |  |
| **Armoured Reptiles** | Armadillo, Crocodile, Sand Lizard, Tortoise | 2 | Health 5–9, Stamina 3–6 (x2) | Oxygen −8–12 (40%) | Hide T1 3:00 | Hide T1 (15%) |
| **Scarak Broodmother** ★ | Scarak Broodmother | 3 | Health 10–16, Stamina 5–9 (x2) | Oxygen −10–15 (50%) | Hide T2 3:00 | Hide T1 (30%) |
| **Snakes and Scorpions** | Cobra, Marsh Snake, Rattlesnake, Scorpion | 2 | Stamina 4–8, Health 3–5 (x2) | Mana −5–8 (40%) | Poison Immunity 3:00 | Poison Immunity (10%) |
| **Gobliterator** ★ | Goblin_Gobliterator_Bottom_Phase3, Goblin_Gobliterator_Intro, Goblin_Gobliterator_Phase1, Goblin_Gobliterator_Phase1_HM, Goblin_Gobliterator_Phase2, Goblin_Gobliterator_Phase2_HM, Goblin_Gobliterator_Phase3, Goblin_Gobliterator_Phase3_HM +4 more | 4 | Health 20–30, Stamina 8–12 (x2) | Mana −10–15 (50%) | Might T3 4:00 | Hide T2 (40%) |

### Zone 3

| Species | Mobs | Syringe | Stats | Drawbacks | Buff (temporary) | Permanent |
| --- | --- | --- | --- | --- | --- | --- |
| **Outlanders** | Outlander Berserker, Outlander Brute, Outlander Hunter, Outlander Initiate, Outlander Marauder, Outlander Priest, Outlander Sorcerer, Outlander Stalker +1 more | 3 | Health 5–9, Mana 4–8 (x2) | Stamina −4–8 (40%) | Endurance T2 3:00 |  |
| **Yeti** ★ | Yeti | 3 | Health 10–16, Stamina 6–10 (x2) | Mana −8–12 (50%) | Endurance T3 4:00 | Freeze Immunity (25%) |
| **Big Cats** | Sabertooth Tiger, Snow Leopard | 3 | Stamina 5–9, Health 4–7 (x2) | Oxygen −10–15 (40%) | Vitality T2 3:00 |  |

### Zone 4

| Species | Mobs | Syringe | Stats | Drawbacks | Buff (temporary) | Permanent |
| --- | --- | --- | --- | --- | --- | --- |
| **Magma Beasts** | Emberwulf, Frost Snail, Magma Rhino Toad, Magma Slug, Magma Snail, Rhino Toad, Snapdragon, Trillodon | 4 | Health 8–14, Stamina 4–8 (x2) | Oxygen −10–15 (50%) | Fire Immunity 3:00 | Fire Immunity (15%) |
| **Golems** ★ | Earthen Golem, Ember Golem, Firesteel Golem, Frost Golem, Sandswept Golem, Thunder Golem | 4 | Health 10–16 | Stamina −6–10 (60%) | Hide T2 3:00 | Hide T1 (20%) |
| **Cave Rex** ★ | Cave Rex | 4 | Health 14–20, Stamina 8–12 (x2) | Mana −10–15 (60%) | Vitality T3 4:00 | Might T1 (25%) |
| **Raptors** | Archaeopteryx, Cave Raptor, Pterodactyl | 4 | Stamina 7–12, Health 4–7 (x2) | Mana −6–10 (40%) | Speed T2 2:30 |  |
| **Aberrant Zombie** ★ | Aberrant Zombie, Large Aberrant Zombie, Small Aberrant Zombie | 4 | Health 14–22 | Stamina −8–12 (80%), Mana −6–10 (50%) | Regen T3 4:00 |  |
| **Undead Champions** ★ | Burnt Skeleton Praetorian, Revenant Guardian, Shadow Knight, Skeleton_Elite_Phase_2, Wraith, Wraith's Lantern | 4 | Health 10–16, Mana 6–10 (x2) | Stamina −5–8 (50%) | Might T2 3:00 | Poison Immunity (20%) |
| **Zombies and Ghouls** | Armored Skeleton Horse, Bleached Hound, Burnt Zombie, Frost Zombie, Ghoul, Sandswept Zombie, Skeleton Horse, Undead Chicken +3 more | 3 | Health 6–12 | Stamina −5–10 (70%), Mana −5–8 (30%) | Regen T1 3:00 | Poison Immunity (10%) |
| **Werewolf** ★ | Werewolf | 4 | Health 12–18, Stamina 8–12 (x2) | Mana −8–12 (60%) | Speed T3 3:00 | Night Vision (40%) |

### Void

| Species | Mobs | Syringe | Stats | Drawbacks | Buff (temporary) | Permanent |
| --- | --- | --- | --- | --- | --- | --- |
| **Voidborn** | Crawler_Void_Anomaly, Crawler_Void_Surge, Eye_Void_Surge, Larva_Void_Surge, Void Crawler, Void Eye, Void Larva, Void Tentacle +5 more | 3 | Mana 8–14, Health 3–6 (x2) | Stamina −5–8 (60%) | Night Vision 3:00 |  |
| **Void Spawn** ★ | Void_Spawn, Void_Spawn_Entombed, Void_Spawn_Entombed_Anomaly, Void_Spawn_Surge | 4 | Mana 15–25, Health 10–15 (x2) | Stamina −6–10 (60%) | Might T2 3:00 | Environmental Immunity (25%) |

★ = major objective (below).

## Major objectives

Creatures worth a trip: bosses, rare spawns and unique mobs that carry the best genes of their kind. Each is its
own species, so it also pays full discovery Science.

| Objective | Where / why it's hard | What it's worth |
| --- | --- | --- |
| **Humpback Whale** | Zone 0 oceans, 400 HP | Oxygen up to 60%, Regen T2 |
| **Goblin Glutton** | The Feastmaster dungeon (no regular world spawn), 700 HP | Health up to 18%, Regen T2 |
| **Hedera** | Unique, spawns in its own portal, 226 HP | Mana up to 18%, Regen T3, **permanent Regen T1** (20%) |
| **Feastmaster** | Goblin dungeon boss, 1950 HP | Health up to 28%, Regen T3, **permanent Poison Immunity** (50%) |
| **Scarak Broodmother** | The Scarak dungeon (no regular world spawn), 145 HP | Health up to 16%, Hide T2, **permanent Hide T1** (30%) |
| **Gobliterator** | Goblin boss, 5000 HP (9000 in hard mode) | Health up to 30%, Might T3, **permanent Hide T2** (40%) |
| **Yeti** | Zone 3; no regular world spawn in the game data, 226 HP | Health up to 16%, Endurance T3, **permanent Freeze Immunity** (25%) |
| **Golems** | Firesteel golems spawn in Zones 1, 2 and 4; crystal golems have no regular world spawn | Health up to 16%, Hide T2, **permanent Hide T1** (20%) |
| **Cave Rex** | Zone 4, 400 HP | Health up to 20%, Vitality T3, **permanent Might T1** (25%) |
| **Aberrant Zombie** | No regular world spawn in the game data, 341–400 HP | Health up to 22%, Regen T3 |
| **Undead Champions** | Skeleton Guardian (two phases), Praetorian, Shadow Knight and Wraith, 193–400 HP | Health up to 16%, Might T2, **permanent Poison Immunity** (20%) |
| **Werewolf** | No regular world spawn in the game data, 283 HP | Health up to 18%, Speed T3, **permanent Night Vision** (40%) |
| **Void Spawn** | Void portals and entombed spawns, 700 HP | Mana up to 25%, Might T2, **permanent Environmental Immunity** (25%) |

Objectives with a boss signature (see Mutations) carry it on **50%** of samples. Each of those mobs can only be
sampled **once**: trying again tells the player it is too angry to be sampled again.

## Mutations

Every mutation reacts to something (a hit, a kill, getting hurt, a fall, crouching, daylight...). None adds a flat
stat; the two that touch stats multiply them only while their condition holds. Splicing favours mutations themed
to the two inputs' species (x4 weight, `Splicing.ThemedMutationWeight`), and bosses carry their signature one.

| Mutation | Themed to | What it does | Min tier | Complexity | Boss signature |
| --- | --- | --- | --- | --- | --- |
| **Egg Layer** | Poultry | Crouch for 5 s to lay an egg. Once every 10 minutes. | Simple | 5 |  |
| **Spring Legs** | Small Critters | Crouch for 1 s, then jump: a much higher leap, and Speed for 4 s. | Simple | 12 |  |
| **Featherfall** | Birds | Falling more than 6 blocks slows your fall and cancels fall damage. | Simple | 15 |  |
| **Photosynthesis** | Kweebecs and Klops, Spirits, Hedera | Stand still in daylight for 3 s: Health and Stamina regenerate until you move. | Simple | 15 | Hedera |
| **Webspinner** | Rats and Spiders | Hits have a 15% chance to root the target for 1.5 s. | Simple | 18 |  |
| **Scavenger** | Goblins, Feastmaster | Kills have a 10% chance to drop the victim's loot a second time. | Simple | 20 | Feastmaster |
| **Venom Glands** | Snakes and Scorpions | Every 4th hit on the same target poisons it. | Simple | 22 |  |
| **Adrenal Surge** | Grazers and Mounts, Cattle | Dropping below 30% health gives you a burst of Speed for 8 s. | Moderate | 25 |  |
| **Blood in the Water** | Sea Hunters | While swimming, hits on targets below 50% health deal +30% and give you Speed for 4 s. | Moderate | 25 |  |
| **Brawler's Rush** | Trorks | Each melee hit within 3 s of the last adds +5% damage, up to +25%. Getting hit resets it. | Moderate | 25 |  |
| **Magma Blood** | Magma Beasts | Taking fire damage makes you immune to fire and regenerates you for 6 s. | Moderate | 25 |  |
| **Silent Hunter** | Bats and Owls | At night, hits on enemies that aren't targeting you deal +50%. | Moderate | 25 |  |
| **Blink** | Voidborn | When one hit takes more than 20% of your health, you dash away from the attacker. | Complex | 30 |  |
| **Bloodfang** | Wolves and Foxes, Werewolf | Your hits heal you for 8% of the damage dealt, 15% at night. | Moderate | 30 | Werewolf |
| **Bulwark** | Golems | Crouch still for 1 s to brace: less physical damage and no knockback until you move. | Moderate | 30 | Golems |
| **Pounce** | Big Cats | Your first melee hit while in the air after a sprint deals +50% and slows the target. | Moderate | 30 |  |
| **Cold Blooded** | Yeti | When slowed or frozen, the effect is removed at once and you gain Might for 6 s. | Complex | 35 | Yeti |
| **Void Siphon** | Voidborn, Void Spawn | A kill restores 20% Mana (40% against Void creatures) and multiplies your max Mana by 1.2 for 10 s. | Complex | 35 | Void Spawn |
| **Grizzly Fury** | Bears | Below 40% health: your max Health is multiplied by 1.15 (on top of your Health genes) and you deal 10% more damage. | Complex | 40 |  |
| **Apex Roar** | Cave Rex | On a kill, nearby enemies are slowed and deal 20% less damage for 5 s. | Complex | 45 | Cave Rex |
| **Overpressure** | Gobliterator | Each hit you take builds pressure. At 5, your next attack releases a shockwave that throws nearby enemies back. | Complex | 50 | Gobliterator |
| **Frostbite** | Yeti, Big Cats | Melee attackers are slowed; a third hit within 6 s freezes them for 2 s. | Advanced | 55 |  |
| **Ember Heart** | Magma Beasts | Below 50% health your hits set targets on fire, and fire can't hurt you. | Advanced | 60 |  |
| **Undying** | Undead Champions | A killing blow leaves you at 1 health with 3 s of immunity. Once every 10 minutes. | Advanced | 70 | Undead Champions |
