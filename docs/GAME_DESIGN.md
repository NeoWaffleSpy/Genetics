# Genetics: Game Design Document

> Status: **draft, being workshopped.** Text marked **Decided** comes from the author. Text marked
> **Proposal** is a suggestion that hasn't been accepted yet. **Open** marks a question that needs an answer.
> **Tentative** means decided for now, but may change as progression is tuned.

## 1. Vision

Genetics is a complete standalone mod. It gives the player a progression track that runs alongside the
normal game progression: sample creatures, study their genes, and rewrite your own body with them.

Early on it's a gamble: inject a raw sample and see what you get. By the end it's precise engineering,
paid for with rare resources.

### Design pillars (Proposal)

1. **Every creature is worth sampling.** Each mob has a gene identity that fits what it is, so exploring
   new zones means finding new genes.
2. **Power has a price, measured by Complexity.** The more a genome holds, the riskier it is to inject
   raw and the more it costs to process. One score drives most of the balance.
3. **Control is earned.** You go from chance (Phase 1), to steering the odds (Phase 2), to choosing the
   outcome (Phase 3). Each step costs more.
4. **Uses the base game.** Gating follows vanilla zones, materials and bench tiers, and genes only use
   vanilla stats and effects. The mod's own Research tree (§9.5) only decides *what* can be crafted; the
   materials still come from vanilla progression.

## 2. Terminology

| Term | Meaning |
| --- | --- |
| **Syringe** | The tool. When empty it can draw a sample from a mob, or from yourself (a copy). |
| **Raw sample** | A filled, unprocessed syringe. Injecting it gives a random result. It can be processed. |
| **Genetic material** | A processed syringe. Injecting it gives a controlled result. It can't be processed again, but it can be spliced. |
| **Gene** | One trait in a genome: either a **stat modifier** (e.g. +6% max health) or an **effect** (e.g. Night Vision). |
| **Genome** | The full set of genes in a sample or on the player. |
| **Active genome** | The genome currently applied to the player. There is only ever one. |
| **Complexity** | A score that scales the risk of injection, the sickness it causes and the cost of processing. See §3. |
| **Genome Capacity** | An entity stat: the most Complexity an entity's body can carry. Raised by crafted Genome Expansions. See §3.4. |
| **Genetic Sickness** | An entity stat that builds up with each injection and decays over time. Causes temporary debuffs. See §9.2. |
| **Genome Expansion** | A crafted consumable that permanently raises Genome Capacity, one per Complexity tier. |
| **Complexity tier** | A band of Complexity values. Each tier has its own processing materials and acts as a progression gate. See §4. |
| **Mutation** | A special, active gene themed to some species. It comes from splicing, or from a boss's signature. Defined in the Mutation Codex. |
| **Effect Codex / Mutation Codex** | Data files that give every effect and mutation its Complexity value (and, for mutations, its behavior). See §3.2 and §3.3. |
| **Identification** | Revealing a sample's genes at the Lab Station. It is part of processing. |
| **Catalogue** | The per-player record of every creature, gene, buff and mutation discovered (formerly the Gene Journal). A tab of the Lab Station. See §9.4. |
| **Science** (placeholder name) | Points earned from Catalogue discoveries and spent in Research. See §9.4. |
| **Research** | The tech tree, a tab of the Lab Station, that unlocks reagents, Lab features, syringes and Genome Expansions with Science. See §9.5. |

## 3. Complexity (Decided)

Every sample has a **Complexity** score. It is the mod's main balancing value, and other systems may be
tied to it later.

| What Complexity scales | How | From |
| --- | --- | --- |
| **Raw injection quality** | The higher the Complexity, the lower the chance that the injected stats and effects come out good | Phase 1 |
| **Genetic sickness** | The higher the Complexity, the more sickness an injection causes (reduced for genetic material) | Phase 1 |
| **Processing cost** | The Complexity tier sets which materials processing needs and how many (§4) | Phase 2 |
| **Mutation chance when splicing** | Rises slightly with the two samples' combined Complexity | Phase 2 |
| **Splice sum** | Splicing adds two samples together; the extra power is paid for in Complexity and capacity | Phase 2 |

**Why it works:** a copy of a high-level player's genome is a raw sample with very high Complexity.
Injecting it raw gives mostly bad results and heavy sickness. Making it useful means processing it, in a
high Complexity tier, at a high cost. So copying is never a free shortcut to end-game power.

### 3.1 Calculation (Decided, numbers are Proposal)

Complexity is **calculated when a sample is processed**, from the genes the player chose to keep. It is
the sum of three parts:

```
Complexity = stat part + effect part + mutation part

stat part     = Σ over stat genes: |modifier| × weight of that stat     (higher stat sum → higher Complexity)
effect part   = Σ over effect genes: Complexity of that effect          (from the Effect Codex)
mutation part = Σ over mutations: Complexity of that mutation           (from the Mutation Codex)
```

- The **stat weights** live in the Effect Codex alongside effects (see §3.2), so every Complexity value
  is tuned in one place.
- Since processing lets the player drop genes, keeping fewer or weaker genes gives a lower Complexity and
  a cheaper tier. Deciding what to keep is the main choice in Phase 2.

**Proposal: Complexity of a raw sample.** Raw injection and sickness also depend on Complexity, so a raw
sample needs a value before processing. It uses the same formula over **all** its genes, and is shown at
identification. Processing then recalculates it over the genes that were kept.

**Drawbacks (Decided).** A negative stat gene subtracts its weight instead of adding it. Keeping a
drawback then makes a genome cheaper to process and easier to fit under the player's capacity (§3.4).

**Proposal: limits on drawbacks.** Without limits, players would stack drawbacks on stats they never use
(e.g. −Mana for a player who doesn't use magic) and get Complexity reduced for free. Three safeguards:

- A drawback counts at **60%** of the weight a positive gene of the same size would add.
- Drawbacks can reduce Complexity by at most **30%** of the positive total.
- Complexity never goes below 1.

### 3.2 Effect Codex (Decided)

Data assets that give every status effect a Complexity value, and every stat its weight, so all base
values are tuned in one place. One file per entry (implemented as Hytale asset types, so resource packs
can extend them):

```jsonc
// Server/Genetics/EffectCodex/Stats/Health.json
{ "WeightPerPercent": 0.8 }          // Stamina 0.6, Mana 0.6, Oxygen 0.2, Default 0.5

// Server/Genetics/EffectCodex/Effects/HealthRegen_Buff_T1.json
{ "Temporary": 3, "Permanent": 100, "Name": "server.genetics.effects.Regen_T1.name" }
```

Each effect has separate values for its temporary and permanent forms, since a permanent effect is worth
far more. Effects missing from the Codex use `Effects/Default.json` (5 / 40), so new or modded effects
work without an entry (and the server logs a warning so they get one).

### 3.3 Mutation Codex (Decided)

Separate data assets (`Server/Genetics/MutationCodex/<Id>.json`, one per mutation) that store each
mutation's **behavior**, **stats** and **Complexity value**. Illustrative shape (the live values are in
the files):

```jsonc
// Server/Genetics/MutationCodex/Bloodfang.json
{
  "Name": "server.genetics.mutations.Bloodfang.name",
  "Description": "server.genetics.mutations.Bloodfang.desc",
  "Complexity": 30,
  "Species": [ "Mammal_Canid", "Undead_Werewolf" ],
  "Behaviors": [
    { "Type": "OnHit", "HealFraction": 0.08 },
    { "Type": "OnHit", "HealFraction": 0.07, "Conditions": { "Night": true } }
  ],
  "Weight": 6,
  "MinTier": "Moderate"
}
```

- **Stats** and **Effects** use the same vanilla stats and effects as normal genes.
- **Behaviors** are small reusable triggers coded in Java and referenced by `Type`, so new mutations can
  mostly be made in data.
- **Weight** is how likely the mutation is to be picked when one is rolled. **MinTier** keeps powerful
  mutations from appearing on simple genomes.

**Decided 2026-11-14: mutations are active, themed and never flat.**
- **Active or reactive.** Every mutation reacts to something: a hit, a kill, getting hurt, a killing blow, a
  fall, a charged jump, crouching, daylight, a debuff. Nothing purely passive.
- **No flat stats.** A mutation never adds a stat genes can already raise. At most it *multiplies* the stat
  while its condition holds (Grizzly Fury: max Health ×1.15 below 40% health).
- **Not all powerful.** Some are combat tools, some utility (Featherfall, Photosynthesis), some gimmicks
  (Egg Layer).
- **Themed.** Each mutation lists the species it belongs to (`Species`). Splicing favours mutations themed
  to the two inputs' species (×4 weight), and bosses carry their **signature mutation** on 50% of samples.
  A mob with a signature can only be sampled **once**; trying again says it is too angry to be sampled again.

**Triggers** (behavior `Type`), all accepting `Cooldown`, `Actions`, `Message` and shared `Conditions`
(`Night`, `Day`, `InWater`, `Airborne`, `SprintJump`, `Crouching`, `Still`, `HealthBelow`, `HealthAbove`):

| Trigger | Fires | Extra fields |
| --- | --- | --- |
| `OnHit` | when the entity damages another (effect ticks don't count) | `Chance`, `EveryNth` (same target), `Melee`, `TargetHealthBelow`, `Unnoticed`, `DamageBonus`, `HealFraction`, `TargetActions`, stacks |
| `OnKill` | on a kill | `Chance`, `TargetSpecies`, `TargetActions` |
| `OnHurt` | when hit | `Causes`, `FromEntity`, `Melee`, `MinFraction`, `MinDamage`, `EveryNth` + `Window`, `AttackerActions`, stacks |
| `OnLethal` | instead of dying | `Invulnerable` (seconds) |
| `WhileHeld` | once the Conditions held for `HoldTime` | `Repeat` |
| `OnFall` | once per fall past `MinDistance` blocks | |
| `OnChargedJump` | jumping after crouching `CrouchTime` | |
| `OnEffect` | while one of `Effects` is on the entity | |
| `OnLowHealth`, `OnDamaged`, `AtNight`, `Periodic` | as before | |

**Actions** (shared with sickness events): `ApplyEffect`, `RemoveEffect`, `RestoreStat`, `AreaEffect`,
`AreaImpulse`, `PushAway`, `Impulse` (can add to velocity), `DropItem`, `DropLoot`, `PlaySound`,
`PlaySourceMobSound`, `ApplySourceMobEffect`, `CameraShake`, `MorphIntoSourceMob`. The 24 mutations are
listed in [MOB_GENETICS.md](MOB_GENETICS.md).

### 3.4 Genome Capacity (Decided)

**The idea.** Permanent effects cost a lot of Complexity, and stats add Complexity linearly, so end-game
genetic material that adds hundreds of stat points reaches hundreds of Complexity. To balance this, every
player has an innate **Genome Capacity** that grows with progression.

If an injected genome's Complexity is **over the player's capacity**:

1. The player gets **massive genetic sickness**, scaled by how far over capacity the genome is.
2. The genome **degrades** until it fits. Genes are removed one at a time, with a strong bias toward
   removing **positive permanent effects and mutations first**.
3. The player **keeps the degraded genome** as their active genome. The previous genome is not restored.

**Genome Capacity is an entity stat (Decided).** It's added as a new stat on entities, not only on the
player, so mobs can use it later (see §12). Together with Genetic Sickness (§9.2), it's an exception to
"no new stats": neither is a gene stat that samples can modify. They are the mod's own bookkeeping.

**Proposal: how the stat works**
- It's registered as a regular Hytale entity stat asset (`GenomeCapacity`), like Health or Stamina, so it
  saves with the entity, shows up in the existing stat systems, and needs no separate storage.
- The stat's **max** is the capacity. Its **current value** is the Complexity of the active genome. The
  Lab Station and the HUD can then show it like a bar: "64 / 80".
- Players start at the base capacity (20). Mobs default to 0 until the mob features exist. Later, a
  mob's capacity could depend on its tier.
- Genes and mutations **can't** modify Genome Capacity. Otherwise a genome could raise its own limit,
  and a degradation that removes such a gene would immediately leave the player over capacity again.

**Why it fits.** Processing cost and capacity limit different things:

- **Processing tiers** decide what it costs to **make** a genome. That cost is paid once.
- **Capacity** decides what a player can **carry**. It's a permanent ceiling.

Capacity also closes a gap that Complexity alone left open. A high-level player could still process
end-game genetic material and give it to a new player, who would inject it with controlled results.
With capacity, the new player's body can't hold it: they get massive sickness and the genome degrades,
losing its most valuable parts first. Copying and trading stay free and allowed, but they no longer skip
the progression.

It also gives the player a clear progress number ("my body can handle 120 Complexity now"), and it
gives drawbacks real value: a drawback is a way to fit more power under the cap.

**Proposal: details**

- **Degradation order.** Remove in this order, picking at random within each group, weighted by how much
  Complexity each gene adds:
  1. mutations;
  2. positive permanent effects;
  3. positive temporary effects;
  4. positive stats. A stat is **shrunk** rather than removed when shrinking is enough to fit.

  Drawbacks are **never** removed, because removing one would raise Complexity.
- **Sickness.** Overflow pushes Genetic Sickness straight to the **Severe** band (§9.2), plus extra
  sickness for each point over capacity. The lasting penalty is the degradation; the sickness is a
  short, chaotic aftermath.
- **Raw samples too.** The capacity check runs after the raw injection's random roll, so raw and
  processed injections follow the same rule.
- **Warning first.** The Lab Station shows the player's capacity next to the sample's Complexity, and
  the injection asks for confirmation when the genome is over capacity. Overflow should be a choice the
  player makes, not a surprise.
- **Capacity has to keep up with the curve.** If Complexity grows exponentially through the tiers but
  capacity grows linearly, end-game permanent effects become impossible to carry. Each capacity upgrade
  should roughly reach the **top of the matching Complexity tier**, so a player who can afford to process
  a tier can also carry it. Example: base 20, then 45, 80, 130 and 200 at the end.
- **How capacity grows (Decided: strictly craftable).** A **Genome Expansion** is the only way to raise
  capacity. It's a consumable crafted at the Lab Station, one per Complexity tier, from that tier's
  materials. Using it permanently sets the Genome Capacity stat's max to the expansion's value.
  Recipes and new capacity values are defined in the tier data (§4.2), so they stay modular.
- **Skipping expansions (Decided).** A player can use a higher expansion directly if they have it, without
  using the lower ones first. **Proposal:** an expansion lower than or equal to the current capacity
  can't be used and isn't consumed.

## 4. Complexity tiers (Decided: tiers exist as a progression gate; design is Proposal)

Complexity values are grouped into **tiers**. Each tier requires different materials, or different
amounts, to process a sample in it. The Lab Station's own tier limits which Complexity tiers it can
handle, which is what gates the phases.

### 4.1 Tiers (implemented; numbers are tunable)

| Tier | Complexity | Station tier needed | Processing materials | Capacity after its Genome Expansion |
| --- | --- | --- | --- | --- |
| Simple | 0–20 | Lab Station T1 | Plant fibre, Life Essence | 20 (base, no expansion needed) |
| Moderate | 21–45 | Lab Station T2 | Copper bars, Life Essence | 45 |
| Complex | 46–80 | Lab Station T3 | Thorium bars, cyan crystals | 80 |
| Advanced | 81–130 | Lab Station T3 | Adamantite bars, concentrated Life Essence | 130 |
| Exotic | 131+ | Lab Station T3 | Mithril bars, Voidstone | 200 |
| *(Supreme expansion)* | — | — | — | +50 per use (the first lifts anything below 200 to 250), up to `Capacity.HardCap` |

Every processing cost also includes one Identification Reagent (§6).

### 4.2 Modular implementation (Proposal)

The goal is that tiers, costs and anything else scaled by Complexity can be added or retuned in data,
without code changes.

**Tiers are data.** One file lists the tiers. Each tier has a range, a gate, and a list of costs. Each cost
names a scaling rule, so a tier can charge a fixed amount, an amount that grows with Complexity inside the
tier, or both:

```jsonc
// Server/Genetics/ComplexityTiers/*.json (one file per tier, shown together; Balance/Default.json holds BaseCapacity and the Library multiplier)
{
  "Tiers": [
    {
      "Id": "Simple",
      "MinComplexity": 0,
      "Requires": { "StationTier": 2 },
      "Costs": [
        { "ItemId": "Ingredient_Fibre", "Scaling": { "Type": "Flat", "Amount": 2 } },
        { "ResourceTypeId": "Essence",  "Scaling": { "Type": "Linear", "Base": 1, "PerPoint": 0.25 } }
      ]
    },
    {
      "Id": "Complex",
      "MinComplexity": 46,
      "Requires": { "StationTier": 3 },
      "Costs": [
        { "ItemId": "Ingredient_Bar_Thorium", "Scaling": { "Type": "Linear", "Base": 2, "PerPoint": 0.1 } }
      ],
      "CapacityUpgrade": {
        "NewCapacity": 80,
        "Recipe": [ { "ItemId": "Ingredient_Bar_Thorium", "Quantity": 10 }, { "ItemId": "Ingredient_Crystal", "Quantity": 3 } ]
      }
    }
  ],
  "BaseCapacity": 20,
  "MutationCostMultiplier": { "Type": "Exponential", "Base": 3 }
}
```

- A tier's range runs from its `MinComplexity` up to the next tier's, so tiers can't overlap or leave gaps.
- `PerPoint` counts from the tier's own `MinComplexity`, so cost grows smoothly inside a tier and then
  jumps to new materials at the next one.
- `MutationCostMultiplier` applies Phase 3's exponential cost: with 3 mutations, every cost is multiplied
  by `3^(3−1) = 9`.

**Code is split into small, separate pieces:**

| Piece | Job |
| --- | --- |
| `Action` (interface) with `ApplyEffect`, `PlaySound`, `Impulse`, … | Small effects registered by `Type` name, shared by mutation behaviors and sickness events. |
| `GeneLibrary` (player component) | Stores the player's extracted genes for Phase 3 (§8.1). |
| `EffectCodex`, `MutationCodex`, `SicknessEvents`, `ComplexityTiers` | Load and validate the data files at startup. Ideally registered as Hytale asset types so resource packs and other mods can extend them; plain JSON resources otherwise. |
| `ComplexityCalculator` | Genome → Complexity, using the two Codices. Has no knowledge of tiers or costs. |
| `TierResolver` | Complexity → tier. |
| `Scaling` (interface) with `Flat`, `Linear`, `Exponential` | Turns a Complexity value into an amount. Registered by `Type` name, the same way the mod already registers interaction types, so new rules can be added later. |
| `CostCalculator` | Tier + Complexity + mutation count → list of item costs. |
| `Gate` (interface), first one `StationTierGate` | Says whether a tier is allowed. More gates can be added later (e.g. a researched-node gate, §9.5) without changing the tier format. |
| `GenomeCapacity` (entity stat asset) | The capacity itself: max = capacity, value = Complexity of the active genome. Works on any entity. |
| `GeneticSickness` (entity stat asset) | Current sickness, decaying over time. Works on any entity. |
| `ActiveGenome` (entity component) | Stores the active genome. Usable on any entity, so mobs can carry genomes later. Persists through death. |
| `GeneJournal` (player component), to become the Catalogue | Stores discovered mobs, genes and mutations, Science and researched nodes (§9.4, §9.5). |
| `CapacityCheck` + `DegradeStrategy` (interface) | Compares a genome with the player's capacity and, if it's over, degrades it. The removal order from §3.4 is the first strategy, so the order can be changed without touching injection code. |

Anything else that should scale with Complexity later (sickness, raw injection quality, processing time)
can either read the continuous Complexity value through a `Scaling` rule, or get a new per-tier field.

## 5. Core loop

```
Craft syringe -> sample a mob -> inject raw, blind (gamble)                               Phase 1
                               -> process (reveals genes, cost by tier) -> genetic material -> inject (controlled)   Phase 1-3
                                                                              |
                                              splice two genetic materials -> raw sample -> process again
      ^                                                                                                     |
      +---------------------- new zones, new mobs, better materials, bench upgrades <-----------------------+
```

## 6. Phase 1: Sampling & raw injection

**Decided**
- The player crafts Syringes, uses them on mobs, and injects the sample, raw and blind or processed.
- Injecting a raw sample gives **random stat modifiers** and a **temporary status effect** based on the
  source mob. The result is random and gets worse as Complexity rises. Permanent effects come later.
- Only **one** genome is active at a time. A new injection **replaces** the previous stat modifiers and
  permanent effects. **Temporary** effects that are already running stay until they expire.
- At any phase, using an empty syringe on yourself **copies** your active genome into it, as a raw sample.
- **Tentative:** injecting empties the syringe, and it can be used again.
- **Tentative:** no new stats are added. Genes only use vanilla stats (e.g. Health, Stamina, Mana,
  Oxygen) and vanilla effects.

**Proposal: how raw injection quality works.** Each gene in a raw sample rolls on its own:

```
chance the gene comes out as-is = 1 / (1 + Complexity / C0)        (C0 ≈ 20, tunable)
otherwise: weakened (lower roll), dropped, or turned into a drawback
```

With C0 = 20, Complexity 5 comes out as-is 80% of the time, 20 gives 50%, 60 gives 25%, and 100 gives
about 17%.

**Proposal: how a mob's genes are defined.** The raw snapshot of the mob is a weak basis for genes: most
mobs only differ in Health, and their active effects at sampling time are random (a burning mob shouldn't
give Burn genes). Instead, each mob gets a **gene profile** in data (one per role or family) that lists:

- a **stat pool**: vanilla stats it can modify, each with a weight and a roll range;
- a **temporary effect** the injection triggers (a short "taste" of the creature);
- an optional **permanent effect** pool (unlocked later in Phase 1, or by mob tier);
- optional **negative genes**: drawbacks that can roll alongside the positives.

Without new stats, much of what makes a creature distinctive has to come from **effects**.

**Implemented (2026-11-13, updated 2026-11-14): every creature has a profile.** 42 gene profiles cover every finished
creature role. Each creature is its own **species** in the Catalogue (264 of them, listed in collapsible biome and
tribe groups); tamed, young, patrol, dungeon and boss-phase variants fold into it. Unfinished mobs (placeholder
models such as the dragons, and the Voidtaken Grizzly) are left out. Rolls scale with
zone and strength (about 3–10% in Zone 1, 7–16% in Zone 4, up to 30% on bosses), and each group has its own
buff, from food buffs and immunities to two new families: **Might** (more damage) and **Hide** (less
physical damage taken). Permanent effects now roll where players can afford them. Major objectives (bosses
and rare mobs) carry the best genes and the best permanent-effect chances. The full inventory, scaling rules
and objective list are in [MOB_GENETICS.md](MOB_GENETICS.md). Props and cinematic actors stay on the
Default profile and pay no species Science.

**Proposal: making Phase 1 feel rewarding** (review item E1)

Phase 1 is where players decide whether the mod is worth their time, so its rewards must be noticeable.
Five changes, all in data except the Catalogue:

1. **Bigger rolls.** Capacity already caps the total, so single genes can be large: up to +10–20% on one
   stat in Zone 1. At a weight of 0.8, a +20% Health gene costs 16 of the starting 20 capacity, so the
   player still can't stack several of them.
2. **Longer temporary effects.** The "taste" effect lasts **2–5 minutes**, long enough to use on an
   expedition. A gene that came out weakened in a raw injection gives half the duration. Set per mob in
   the gene profile (`"TemporaryEffect": { "Id": "…", "Duration": 180 }`).
3. **An early permanent change.** A few Zone 1 species carry one weak permanent utility effect (Complexity
   8–12, e.g. night vision from a cave creature). It fits in the starting capacity with a small stat gene,
   so the first "my body changed" moment comes early.
4. **Discovery moments.** The first sample of each species adds a Catalogue entry, shows a
   notification with progress ("New genome: Cow, Zone 1 3/12") and pays Science (§9.4).
5. **Discoveries fund Research** (Decided 2026-11-12, replacing Journal milestones). Instead of fixed
   milestones (e.g. 4 species teach the Copper Syringe), every discovery pays Science, and the player
   chooses what to unlock in the Research tree (§9.5). Unlocks still use vanilla's recipe knowledge
   (`KnowledgeRequired`), and the items are still crafted from materials. This gives Phase 1 a clear goal,
   lets the player pick their own order, and leads straight into Phase 2.

**Decided: identification is part of processing** (changed after playtesting: identifying and processing
were two steps doing nearly the same thing)
- A fresh sample shows as "Unidentified <Mob> Sample". A splice result shows as "Unidentified Spliced Sample".
- Processing reveals the genes on its screen, and the result is identified genetic material. Processing
  is available on a T1 Lab Station for Simple Complexity, and costs one Identification Reagent on top of
  the tier materials.
- You can inject an unidentified sample, as a blind gamble.

**Proposal: syringe tiers gate which mobs can be sampled**

| Syringe | Materials (vanilla) | Can sample |
| --- | --- | --- |
| Crude | Stick, fibre, rubble | Passive Zone 1 creatures |
| Copper / Iron | Ore bars + glass | Hostile Zone 1–2 creatures |
| Thorium / Cobalt | … | Zone 3 creatures |
| Adamantite / Mithril | … | Zone 4 creatures, bosses (sampling a boss needs it to be weakened) |

Stronger source mobs give stronger genes and so higher Complexity, which makes raw injection a worse
gamble the further the player goes and pushes them toward Phase 2.

**Decided: stronger mobs give rarer buffs.** Splicing makes small buffs easy to stack, so strength comes
from the source instead: predators and mythic creatures roll bigger stat ranges and carry higher tiers of
vanilla buffs (Bear: Regeneration T2; Sabertooth and Yeti: Meat Buff T2; Raptor: Stamina buff T2; Rex:
Meat Buff T3). Higher tiers cost more Complexity in the Effect Codex.

## 7. Phase 2: Processing & splicing (steering the odds)

**Decided**
- **Processing** a raw sample: the screen reveals its genes (identification, §6) and the player ticks the
  stats and effects to keep. Complexity is calculated from what's kept, and the cost depends on the
  resulting Complexity tier (§4). The result is **genetic material**.
- Genetic material **can't be processed again**, but it **can be spliced**.
- **Splicing** always takes **two** samples and **adds them together** (changed after playtesting: random
  per-stat picks gave no power over the best input, so Phase 2 had no reward). The same stat in both sums
  its percentages (drawbacks subtract), the same temporary effect sums its timers, and a gene only one
  input has carries over. Genome Capacity is what limits it: the sum's Complexity rises with it. The
  mutation chance rises slightly with the two samples' combined Complexity.
- The same buff at different tiers (Meat Buff T1 and T2) can't both be in a sample. The splice screen has
  a tickbox per gene, and the player must untick one of each such pair before splicing.
- A spliced sample is unidentified until processed, then named after its Complexity tier: "Simple Spliced
  Gene", "Moderate Spliced Gene", and so on. It keeps the higher-Complexity input's mob for sickness events.
- The result of a splice is a **raw** sample. It has to be processed before it can be spliced again (or
  injected in a controlled way). Every round of splicing therefore costs a processing step.
- **Splicing cost** uses the Complexity tier system (§4). The tier is picked from the **average** of the
  two samples' Complexity.
- Splicing never mixes mutations: the result keeps one input's mutations. Several mutations in one genome
  only come from the Genetic Library (§8.1).
- **Permanent effects from temporary ones (duration threshold).** Splicing adds timers together; once a
  temporary effect lasts at least `Permanence.MinDuration` (30:00 by default, ten 3:00 rolls), processing
  offers a "Make permanent" tickbox for it. It then costs its permanent Complexity (Effect Codex) and one
  **Gene Stabilizer** (a rare crafted catalyst: cyan crystals, concentrated Life Essence and reagents) on
  top of the usual cost. Capacity is the real limit: permanent effects cost 40–220 Complexity.
- **New mutations come from splicing and bosses.** Processing never creates a mutation: it only keeps one
  that is already in the sample. Splicing rolls new ones (favouring those themed to the inputs' species),
  and bosses carry their signature mutation on 50% of samples (§3.3).

**Proposal: details**
- Phase 2 is the Lab Station's tier 2 upgrade (vanilla benches already upgrade this way), so the player
  keeps a single genetics bench.
- A temporary effect costs its Effect Codex Complexity up to `EffectReferenceDuration` (180 s), then
  proportionally more: a 6:00 Regeneration costs twice a 3:00 one. A permanent effect in both inputs is
  kept once.
- Mutation chance on splice: `2% + 0.05% × combined Complexity`, so two Complexity 20 samples give 4% and
  two Complexity 80 samples give 10%. The mutation is drawn from the Mutation Codex by `Weight`, among
  mutations whose `MinTier` the result reaches; those themed to an input's species weigh 4 times more
  (`Splicing.ThemedMutationWeight`).
- When both inputs carry a mutation, the higher-Complexity input's is kept. A new mutation is only rolled
  when neither input has one.

## 8. Phase 3: Gene editing (choosing the outcome)

**Decided**
- The player has fine control over processing, at a higher resource cost.
- Multiple mutations can coexist, at a cost that grows **exponentially** with each extra mutation.

### 8.1 Genetic Library (Decided)

Fine control comes from a **Genetic Library**. The player **extracts** specific stats, effects or
mutations from samples into the library, then **combines** them at will into new genetic material. The
cost scales with the Complexity of what's combined.

This replaces random splicing as the late-game way to build genomes. Splicing stays useful (it's the only
way to create new mutations), but once a gene is in the library, the player no longer has to gamble for it.

**Decided 2026-11-13: how it works** (implemented; changed after the first playtest; numbers are in
`Balance/Default.json` → `Library`)

- **Unlock.** The Genetic Library research (Lab branch, after Make Permanent) unlocks the Library tab and the
  Lab Station T3 upgrade (thorium, cyan crystals, concentrated Life Essence), and teaches the Gene Enhancer.
- **Extract.** Put a genetic material in the Lab Station T3 and pick **one** gene from it (its Extract
  button on the Process tab). The material is consumed. Any gene can be extracted: stats, drawbacks,
  effects (temporary or permanent) and mutations. The cost is the materials of that gene's Complexity tier,
  plus **Gene Enhancers** for stats and effects: one per 10 points of the gene's Complexity (at least one).
- **One entry per gene, at its best value.** The library keeps a single entry per stat, drawback, effect
  form and mutation. Extracting a better version raises the entry; a weaker or equal one can't be
  extracted (the button says why). Entries are knowledge, not items: they are never used up.
- **Combine.** Tick entries in the Library tab and choose **how much** of each to use, from 0 to the
  entry's best value (a slider, for stats and temporary effects; permanent effects and mutations are all
  or nothing). The result goes into an empty syringe in the station's slot, named "<Tier> Designed Gene",
  and its cost uses the Complexity tier of the result.
- **Fine control.** Each amount has a slider and a number field (one decimal): percent for stats, minutes
  for temporary effects.
- **Timer cap.** A temporary effect never lasts more than 30:00 when timers add up (splicing, the Library),
  so nobody builds a near-permanent buff for a temporary one's price (`Splicing.MaxEffectDuration`).
- **Rules.** A bonus and a drawback of the same stat can't be combined; two tiers of the same buff
  conflict; a permanent effect replaces its temporary form.
- **Several mutations.** Only the Library can put several mutations in one genome (up to 4); the material
  cost is multiplied by 3 per extra mutation. Splicing keeps all of one input's mutations, never a mix, and
  every mutation's behaviors run on the player.
- **Dropped after the playtest:** stat fusion (several copies of a stat stacking at falling rates) and
  refining entries past their creature's range. The late-game spike now comes from extracting better
  versions (splicing pushes values up) and from mixing genes and mutations freely, with capacity and
  Complexity as the limits. Genes fused by the first version keep their full Complexity (`Cost`).
- **Storage.** The library belongs to the player (a `GeneLibrary` component, like the Catalogue), so it's
  never lost with a block. Players share genes by giving each other genetic material to extract.

### 8.2 Negative genes in the library (Decided 2026-11-13: option 1, Complexity reducers)

Being able to extract **negative stats** too is an interesting idea, but the implementation isn't
decided yet. Three possible uses, which can be combined:

1. **Complexity reducers.** Adding a negative entry to a combination lowers its Complexity, so more power
   fits under capacity. The drawback limits from §3.1 still apply (per-stat drawback weight and the 30%
   cap), so this can't go infinite. This uses the existing rule that drawbacks lower Complexity, and
   needs no new system.
2. **Bad luck becomes a resource.** Negative genes mostly come from raw injections that went wrong.
   Copying that genome, processing it and extracting the drawback turns a failed gamble into something
   useful. This gives Phase 1's failures a late-game value.
3. **Weapons, later.** Once genomes can be applied to mobs (§12), a genome made only of negative genes
   could weaken hostile mobs, e.g. as a syringe dart.

Decided: **1**, which gives **2** for free; **3** waits for the mob features. A drawback entry lowers
the combination's Complexity like any drawback, and the 30% drawback cap still applies.

## 9. Cross-cutting systems

### 9.1 Copying your genome (Decided)

An empty syringe used on yourself creates a **raw** sample of your active genome. This is available
**from the start**, with the basic syringe. High-Complexity copies only become useful after expensive
processing, and even processed material can't be carried beyond the receiver's capacity (§3.4), so
copying never bypasses the progression.

### 9.2 Genetic sickness (Decided: an entity stat, scales with Complexity, reduced for genetic material)

Every injection causes sickness based on its Complexity. Genetic material causes **reduced** sickness.
This also gives swapping a cost, even late in the game.

**Genetic Sickness is an entity stat (Decided)**, like Genome Capacity. Any entity can have it, so mobs
can get sick later, and it can be shown as a bar.

**Sickness is a fun side effect, not a punishment (Decided).** It triggers random events, interactions
and effects, more or less often depending on how sick the player is. It's **short**: it wears off in
minutes. **Raw** injections build up a **lot** of sickness, so injecting raw samples back to back quickly
turns into constant chaos. Processed injections build up much less. Syringes don't stack, so a player
can't carry a large supply of processed samples, and processing on the go is hard because of the cost.

**Proposal: how it works**
- It's registered as an entity stat asset (`GeneticSickness`), with a value from 0 to 100.
- Sickness gained = `Complexity × S`. Raw injections use a high `S`, so one raw injection lands in the
  Mild band and two or three in quick succession reach Severe. Genetic material uses about 0.3× that.
- It decays quickly: about **3 minutes** from Severe to zero, so each episode is short. Decay uses
  vanilla stat regeneration with a negative rate if that's supported, otherwise a small ticking system.
- It **persists through death**, like the genome, so dying doesn't cut an episode short.
- Genes and mutations can't modify it, for the same reason as Genome Capacity.

**Proposal: severity bands**

| Band | Sickness | Events | Feel |
| --- | --- | --- | --- |
| Healthy | 0–19 | None | — |
| Mild | 20–49 | One every 60–90 s | Funny and harmless |
| Moderate | 50–79 | One every 30–45 s | Inconvenient |
| Severe | 80–100 | One every 10–20 s | Chaotic, a real reason to stop injecting |

**Proposal: Sickness Event Codex.** Events are data, like mutations, in a third Codex file. Each event
has a weight, a minimum band, a cooldown, and one or more actions. Many events use the **source mob of
the last injection**, so sickness is different for every creature and stays funny:

```jsonc
// Server/Genetics/SicknessEvents/*.json (one file per event, shown together; illustrative)
{
  "Events": {
    "MobCall":    { "MinBand": "Mild",     "Weight": 10, "Cooldown": 30,
                    "Actions": [ { "Type": "PlaySourceMobSound" } ] },
    "Hiccups":    { "MinBand": "Mild",     "Weight": 8,  "Cooldown": 60,
                    "Actions": [ { "Type": "Impulse", "Up": 0.4, "Repeat": 3, "Interval": 1.5 } ] },
    "Echo":       { "MinBand": "Mild",     "Weight": 6,  "Cooldown": 90,
                    "Actions": [ { "Type": "ApplySourceMobEffect", "Duration": 5 } ] },
    "Admirers":   { "MinBand": "Moderate", "Weight": 5,  "Cooldown": 120,
                    "Actions": [ { "Type": "AttractSourceSpecies", "Radius": 16, "Duration": 20 } ] },
    "Sneeze":     { "MinBand": "Moderate", "Weight": 6,  "Cooldown": 45,
                    "Actions": [ { "Type": "Impulse", "Back": 0.8 }, { "Type": "PlaySound", "Id": "…" } ] },
    "Nausea":     { "MinBand": "Severe",   "Weight": 6,  "Cooldown": 60,
                    "Actions": [ { "Type": "ApplyEffect", "Id": "…", "Duration": 6 } ] },
    "Butterfingers": { "MinBand": "Severe", "Weight": 3, "Cooldown": 120,
                    "Actions": [ { "Type": "DropHeldItem" } ] }
  }
}
```

Example events: making the source mob's sound, hiccup hops, a 5-second flash of the source mob's effect,
animals of that species following you, sneezes that push you back, nausea, dropping your held item.

**Actions are shared with mutation behaviors** (§3.3): `ApplyEffect`, `PlaySound`, `Impulse` and the
rest are written once in Java and used by both Codices. Adding an action makes it available to both
mutations and sickness events.

### 9.3 Death (Decided)

Death doesn't affect the active genome.

### 9.4 Catalogue and Science (Decided; numbers are Proposal)

The **Catalogue** (formerly the Gene Journal) is the per-player record of everything discovered. It is a
tab of the Lab Station, with four sub-tabs, each with a found / total count:

| Sub-tab | Shows |
| --- | --- |
| Creatures | Species grouped by zone. Unknown species stay "???" but show their zone. A species shows its gene ranges, how many genes are still missing (counted, not revealed) and the Science it has paid. |
| Genes | Every stat gene found, with the species that carry it and the best roll seen. |
| Buffs | Every effect found (temporary and permanent), with its tiers and sources. |
| Mutations | Every mutation obtained, with its behaviors. Unknown mutations show only their tier. |

A gene counts as discovered when it is **revealed by processing** (identification is part of processing,
§6), not when the sample is drawn. Drawing the first sample of a species is discovered at sampling time.
(Called a Catalogue so it isn't confused with the Codices, which are data files.)

**Science** (placeholder name) is earned from discoveries and spent in Research (§9.5). It is a per-player
counter, saved with the Catalogue. Placeholder rewards:

| Discovery | Science |
| --- | --- |
| First sample of a species | +3 |
| Each new gene of a species | +1 |
| Every gene of a species found (species complete) | +3 |
| New buff (first time any species shows it) | +2 |
| New mutation | +4 (was +10, lowered when 24 mutations made them a large pool) |

The rewards live in the Balance file, like the old milestones did. Repeating a discovery pays nothing, so
Science can't be farmed by sampling the same mob. A species is a gene profile, not an NPC role, so variants
of one creature pay once (saved Catalogues are migrated).

**Economy (implemented 2026-11-13, updated 2026-11-14).** Everything discoverable pays about **twice** the
Research tree: 2782 Science against 1300 (264 creatures 2638, 24 buffs 48, 24 mutations 96). Since 2026-11-14 every creature is its own species (variants fold into it), and Research costs were multiplied by 5 to match. Players can research everything without
finding everything. `ScienceEconomyTest` fails if data changes push the ratio out of 1.8–2.4.

### 9.5 Research (Decided; tree contents are Proposal)

**Research** is a tech tree, the last tab of the Lab Station, that replaces the Journal milestones. Each
node costs Science and has prerequisites; researching it unlocks a recipe (through vanilla recipe
knowledge) or a Lab feature. **Research is a gate, not a substitute for gathering:** the unlocked item or
station upgrade still costs its materials.

| Branch | Nodes, in order (placeholder costs) |
| --- | --- |
| Lab | Processing (starter) → Splicing (50, unlocks the Splice tab and the Lab Station T2 upgrade) → Make Permanent (75) → Genetic Library (125, unlocks the Library tab and the T3 upgrade) |
| Reagents | Identification Reagent (starter) → Gene Stabilizer (60, needs Make Permanent). The Gene Enhancer recipe comes with Genetic Library. Idea for later: Mutagen (raises the mutation chance of one splice) |
| Syringes | Crude (starter) → Copper (25) → Thorium (75) → Adamantite (150) |
| Genome | Expansion · Moderate (40, capacity 45) → Complex (75, 80) → Advanced (125, 130) → Exotic (200, 200) → Supreme (300: +50 per use, again and again up to `Capacity.HardCap`, 10 000; the first one lifts a capacity below 200 to 250) |

- Starter nodes are researched from the beginning, so the first loop (sample, process, inject raw) needs
  no Science.
- A node shows as Researched, Available (affordable or not, with its price) or Locked (with what it needs).
  Cross-branch prerequisites are allowed (Gene Stabilizer needs Make Permanent).
- More branches can be added later (Phase 3 Library features, faction-specific reagents), since the tree
  is data.
- **Data:** a `ResearchNode` asset type (`Server/Genetics/Research/`) with `Branch`, `Cost`, `Requires`
  (node ids), and what it `Unlocks` (recipe ids and/or feature flags such as `Splicing`,
  `MakePermanent`, `StationTier3`). Researched nodes are stored on the player with the Catalogue.
- **Migration:** players who already reached a Journal milestone get its node researched for free.

## 10. Progression & gating summary (Proposal)

| Phase | Unlocked by | Main resources | Player gets |
| --- | --- | --- | --- |
| 1 | Workbench → Syringe and Lab Station T1 (Zone 1) | Basic materials, ID reagent | Raw injection, Simple processing, copying; capacity 20 |
| 1+ | Research: better syringes (ore tiers) | Science, ores, glass | Stronger mobs, first permanent effects, riskier raw injections |
| 2 | Research: Splicing, then the Lab Station T2 upgrade | Science, Simple and Moderate tier materials | Moderate processing, splicing, making effects permanent, single mutation; capacity up to 45 |
| 3 | Research: Genetic Library, then the Lab Station T3 upgrade | Science, Gene Enhancers, Complex, Advanced and Exotic tier materials | Genetic Library: extract genes and combine them at chosen amounts; multiple mutations; capacity 200, then Supreme expansions |

## 11. Balancing levers

All of these live in data files or config rather than code:

- Effect Codex: stat weights, effect Complexity values
- Mutation Codex: mutation Complexity, stats, behaviors, weights, minimum tiers
- Complexity tiers: ranges, station gates, materials, scaling rules, mutation cost multiplier
- Capacity: base value, value after each upgrade, overflow sickness rate, degradation order
- Drawback discount (60%) and cap (30%)
- Raw injection curve (C0) and what a failed gene turns into
- Sickness per Complexity point (raw and processed), decay rate, band thresholds, event weights and cooldowns
- Library: extraction cost (Gene Enhancers per Complexity), combination costs, mutation multiplier
- Splice effect reference duration; mutation base chance and Complexity factor
- Roll counts and ranges per mob profile
- Science rewards per discovery; Research node costs and prerequisites

## 12. Future ideas

- **Applying genomes to mobs:** inject tamed or farm animals to make them stronger, or to make them
  produce custom harvestable resources (e.g. a spliced sheep growing a reagent fibre). This could also be
  the source of rare processing materials, closing the loop.
- **More systems scaled by Complexity**, through the `Scaling` rules in §4.2.
- **New stats** (movement speed, damage, defense), if vanilla stats and effects turn out to be too
  limited.

## 13. Design review (2026-11-10)

A pass over the whole design, looking at three things: whether the loop keeps players engaged, what
players could exploit, and whether early and mid game stay balanced. The late game is meant to be a big
power spike, so it's only checked for whether that spike can actually be reached. Each finding shows
where it stands after the author's answers.

### 13.1 Engagement

**What works**
- **Exploring is rewarded.** Every new zone brings new mobs, so new genes, and the syringe tiers make
  sampling follow the vanilla progression.
- **Each phase changes how the player plays.** Phase 1 is a gamble, Phase 2 is choosing and combining,
  Phase 3 is building exactly what you want from the Genetic Library. That's three different activities,
  not one activity with bigger numbers.
- **There are clear goals at every stage:** the next syringe tier, the next Research node, the next Lab
  Station tier, the next Genome Expansion, the next mutation, the next library entry.
- **The design can grow.** The Codices, tiers and mob profiles are all data, so content can be added
  without code.
- **Sickness adds personality.** Events based on the source mob make every injection a little story.

| # | Risk | Status |
| --- | --- | --- |
| E1 | **Phase 1 rewards feel small.** With vanilla stats only, early injections were barely noticeable. | **Proposal** in §6: bigger rolls, 2–5 minute effects, an early permanent utility effect, discovery moments, and discoveries that pay Science for the Research tree (decided, §9.4–9.5). |
| E2 | **The late-game spike had no source.** Splicing and processing never made a stat bigger than the best mob roll. | **Addressed:** additive splicing pushes values past any single roll, and the Genetic Library (§8.1) stores the best of each gene and mixes them freely, with Complexity and capacity still limiting them. |
| E3 | **Mid game has long stretches of chance.** Every splice is random per stat, and every round needs a processing step. | **Addressed:** splicing is no longer random; it adds both inputs together, and the screen shows the result before paying. |
| E4 | **Splicing rules had gaps.** | **Decided:** cost uses the tier of the two samples' average Complexity, and a gene only one input has always carries over. |

### 13.2 Exploits

| # | Exploit | Status |
| --- | --- | --- |
| X1 | **Free potions.** Reusable syringes, unlimited mobs and temporary effects that survive an override could make cheap raw samples a free potion supply. | **Addressed.** Raw injections build up a lot of sickness, so spamming them quickly reaches Severe. Processed samples cause less, but syringes don't stack and processing costs resources, so a player can't carry many. **Proposal kept:** copies of yourself carry no temporary effect, so copying can't be used as a free buff either. |
| X2 | **Gifted expansions.** A high-level player can give a new player end-game expansions and genetic material. | **Accepted as a feature.** The base game lets players gift end-game items too. |
| X3 | **Free drawbacks.** A drawback on an unused stat (−Mana, −Oxygen) costs nothing but lowers Complexity. This matters more now that negative genes may be extracted into the library. | **Open.** Recommendation: give each stat its own drawback weight in the Effect Codex, low for stats that are easy to ignore. |
| X4 | **Dying cures sickness.** | **Addressed:** sickness persists through death (§9.2). Check once the stat exists, because it depends on how vanilla handles stats on respawn. |
| X5 | **Mutation farming** by splicing cheap materials for the mutation roll. | **Mostly handled:** `MinTier` is required for every mutation. Two weak mutations are still `Simple`, so they can come from cheap splices; acceptable as long as they stay weak. |
| X6 | **Lucky raw injections** giving processed quality for free. | **No change needed:** the odds are very low, capacity still applies, and raw sickness limits retries. |
| X7 | **Library power creep.** Reusable library entries could, in theory, build very large genomes. | **Handled by design:** one entry per gene (no stacking copies), temporary timers capped at 30:00, and capacity caps what the player can carry. This is the intended late-game spike. |
| X8 | **Science from role variants.** Calves, tamed and coloured variants of one creature could each pay first-sample Science. | **Addressed:** the Catalogue counts a species per gene profile, not per role (§9.4). |

### 13.3 Early and mid game balance

| # | Issue | Status |
| --- | --- | --- |
| B1 | **Permanent effects could arrive too early** with the example costs (permanent Speed at 30 fits mid-game capacity 45). | **Deferred:** values live in the Effect Codex and will be tuned during balancing. Keep in mind when setting them: combat and movement effects should cost more than mid-game capacity. |
| B2 | **Phase 1 permanent effects were out of reach,** because they exceeded starting capacity. | **Proposal** in §6: a few Zone 1 species carry weak permanent utility effects that fit in 20. |
| B3 | **Sickness numbers needed real units.** | **Proposal** in §9.2: bands with event frequencies, and about 3 minutes from Severe to zero. |
| B4 | **The starting capacity is tight but fair.** | No change. With bigger Phase 1 rolls (§6), one strong gene or two or three small ones fit in 20. |
| B5 | **Solo players can skip expansions,** but still can't process high tiers without the matching Lab Station. | No change. |

## 14. Open questions

1. **E1 / B2:** bigger Phase 1 rolls and an early permanent effect that fits in the starting capacity
   (today Night Vision costs 40 and Fall Immunity 100, both above 20).
2. **Science:** final name ("Science" is a placeholder). The economy is sized so that every discovery pays
   about twice what the whole Research tree costs (§9.4); to be tuned in playtests.
3. **X3:** give each stat its own drawback weight?
4. **Supreme expansion:** a flat cost all the way to the 10 000 hard cap gives no reason to stop; scale the
   cost, or lower the cap until content needs more capacity.

## 15. Implementation status

| Feature | Status |
| --- | --- |
| Syringe: sample a mob into item metadata | Done (genome format v1: genes, raw/processed flag, Complexity, source) |
| Lab Station: one-slot screen showing stats and effects | Done |
| Effect Codex, Mutation Codex, Complexity tiers (data + loaders) | Done (tiers with Flat, Linear and Exponential Scaling rules, station gate) |
| Complexity calculation | Done (raw samples, with drawback limits) |
| Genome Capacity stat, Genome Expansion, degradation | Done (stat, over-capacity check and degradation, warning screen, Moderate expansion) |
| Mob gene profiles | Done (45 species covering every creature role, scaled by zone and strength; Might and Hide buffs; see MOB_GENETICS.md) |
| Automated tests | Done (JUnit, test source set only, never in the mod jar: data files decode and cross-reference, Complexity, splicing, Library, degradation, tiers, Science economy, profile coverage of the installed game, UI selector spacing) |
| Raw self-injection and active genome | Done (quality roll, override rule, kept through death, syringe emptied) |
| Copying your genome | Done |
| Genetic Sickness stat and debuffs | Done (stat, bands, decay, kept through death; shared Action system; 10 events across the three bands) |
| Gene Journal, discovery notifications, milestone recipe unlocks | Replaced by the Catalogue, Science and Research |
| Catalogue tab, Science and Research tree | Done (Catalogue with four sub-tabs, Science rewards, Research nodes as data with recipe and feature unlocks, milestone migration) |
| Lab Station UI rework | Done (one tabbed panel: Process, Splice, Catalogue, Research; slot B for splicing; capacity gauge; Genetics look) |
| Identification and reagent | Done (merged into processing; the reagent is part of every processing cost) |
| Syringe tiers | Done (Crude, Copper, Thorium, Adamantite; tier from profile or max health) |
| Phase 2 processing and splicing | Done (Lab Station T2 upgrade; processing screen to choose genes; additive splicing with the result shown before paying, spliced samples named after their tier; new mutations only when none is inherited) |
| Mutations and behaviors | Done (24 themed, active mutations; 12 triggers incl. hit, kill, hurt, killing blow, hold, fall, charged jump, effect; shared conditions and stacks; boss signature mutations at 50%, bosses sampled once each; themed splicing rolls) |
| Phase 3 Genetic Library | Done (Lab Station T3; Library tab; extract any gene with Gene Enhancers; one entry per gene at its best value; combine at chosen amounts, drawbacks as reducers; several mutations with the x3 cost multiplier; Complex, Advanced and Exotic expansions) |

## 16. TODO

Planned work, noted 2026-11-12, in no particular order:

- [x] **More genetic profiles**: every creature, factions included (MOB_GENETICS.md). Still to do: signature
      mutations on bosses (a proposal in MOB_GENETICS.md).
- [x] **More mutations** with unique effects: 24, themed to species, with boss signatures (MOB_GENETICS.md).
- [x] **UI rework** of the Lab Station: one panel with Process, Splice, Catalogue and Research tabs, and the
      Genetics visual identity (lab teal, helix ornament), from the Hytale-UI Figma proposal. Still to do:
      real textures for the panels and buttons (today flat colours).
- [x] **Catalogue, Science and Research** (§9.4, §9.5), replacing the Journal milestones.
- [ ] **Game feel**: processing time, animations, sounds and other feedback.
- [ ] **Textures and models** for the mod's own items and blocks (the Lab Station, syringes and reagents use vanilla placeholders today).
