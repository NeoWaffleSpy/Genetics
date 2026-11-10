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
   vanilla stats and effects. The mod has no separate tech tree of its own.

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
| **Mutation** | A special gene that never appears on a source mob. Defined in the Mutation Codex. |
| **Effect Codex / Mutation Codex** | Data files that give every effect and mutation its Complexity value (and, for mutations, its behavior). See §3.2 and §3.3. |
| **Identification** | Revealing a sample's genes at the Lab Station. |

## 3. Complexity (Decided)

Every sample has a **Complexity** score. It is the mod's main balancing value, and other systems may be
tied to it later.

| What Complexity scales | How | From |
| --- | --- | --- |
| **Raw injection quality** | The higher the Complexity, the lower the chance that the injected stats and effects come out good | Phase 1 |
| **Genetic sickness** | The higher the Complexity, the more sickness an injection causes (reduced for genetic material) | Phase 1 |
| **Processing cost** | The Complexity tier sets which materials processing needs and how many (§4) | Phase 2 |
| **Mutation chance when splicing** | Rises slightly with the two samples' combined Complexity | Phase 2 |
| **Splice bias** | When two samples are spliced, the one with higher Complexity is slightly favoured | Phase 2 |

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

A data file that gives every status effect an arbitrary Complexity value. It is the base for the effect
part of the calculation. **Proposal:** it also holds the weight of each stat, so all base values are in
one file.

```jsonc
// Server/Genetics/EffectCodex.json
{
  "Stats": {
    "Health":  { "WeightPerPercent": 0.8 },
    "Stamina": { "WeightPerPercent": 0.6 },
    "Mana":    { "WeightPerPercent": 0.6 },
    "Oxygen":  { "WeightPerPercent": 0.2 }
  },
  "Effects": {
    "Regeneration": { "Temporary": 3, "Permanent": 25 },
    "NightVision":  { "Temporary": 1, "Permanent": 10 },
    "Speed":        { "Temporary": 4, "Permanent": 30 }
  },
  "DefaultEffect": { "Temporary": 5, "Permanent": 40 }
}
```

**Proposal:** each effect has separate values for its temporary and permanent forms, since a permanent
effect is worth far more. Effects missing from the Codex use `DefaultEffect`, so new or modded effects
work without an entry (and the server logs a warning so they get one).

### 3.3 Mutation Codex (Decided)

A separate data file that stores each mutation's **behavior**, **stats** and **Complexity value**.

```jsonc
// Server/Genetics/MutationCodex.json
{
  "Mutations": {
    "TitanBlood": {
      "Name": "server.genetics.mutations.TitanBlood.name",
      "Complexity": 35,
      "Stats":   { "Health": 20 },
      "Effects": [],
      "Behaviors": [],
      "Weight": 10,
      "MinTier": "Complex"
    },
    "AdrenalSurge": {
      "Name": "server.genetics.mutations.AdrenalSurge.name",
      "Complexity": 45,
      "Stats":   {},
      "Effects": [],
      "Behaviors": [
        { "Type": "OnLowHealth", "Threshold": 0.25, "Apply": "Speed", "Duration": 6, "Cooldown": 60 }
      ],
      "Weight": 5,
      "MinTier": "Advanced"
    }
  }
}
```

- **Stats** and **Effects** use the same vanilla stats and effects as normal genes.
- **Behaviors** are small reusable triggers coded in Java and referenced by `Type`, so new mutations can
  mostly be made in data. A starting set (Proposal): `OnLowHealth`, `OnHit`, `OnKill`, `InWater`,
  `AtNight`, each applying an effect.
- **Weight** is how likely the mutation is to be picked when one is rolled. **MinTier** (Proposal) keeps
  powerful mutations from appearing on simple genomes.

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

### 4.1 Example tiers (placeholder values and materials)

| Tier | Complexity | Station tier needed | Processing materials (example) | Capacity after its Genome Expansion |
| --- | --- | --- | --- | --- |
| Simple | 0–20 | Lab Station T2 | Plant fibre, Essence | 20 (base, no expansion needed) |
| Moderate | 21–45 | Lab Station T2 | Copper or iron bars, Essence | 45 |
| Complex | 46–80 | Lab Station T3 | Thorium or cobalt bars, crystals | 80 |
| Advanced | 81–130 | Lab Station T3 | Adamantite, rare zone drops | 130 |
| Exotic | 131+ | Lab Station T3 | Mithril, boss drops | 200 |

Material names follow the vanilla progression and should be checked against the current game data.

### 4.2 Modular implementation (Proposal)

The goal is that tiers, costs and anything else scaled by Complexity can be added or retuned in data,
without code changes.

**Tiers are data.** One file lists the tiers. Each tier has a range, a gate, and a list of costs. Each cost
names a scaling rule, so a tier can charge a fixed amount, an amount that grows with Complexity inside the
tier, or both:

```jsonc
// Server/Genetics/ComplexityTiers.json
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
| `Gate` (interface), first one `StationTierGate` | Says whether a tier is allowed. More gates can be added later (e.g. a discovered-in-Journal gate) without changing the tier format. |
| `GenomeCapacity` (entity stat asset) | The capacity itself: max = capacity, value = Complexity of the active genome. Works on any entity. |
| `GeneticSickness` (entity stat asset) | Current sickness, decaying over time. Works on any entity. |
| `ActiveGenome` (entity component) | Stores the active genome. Usable on any entity, so mobs can carry genomes later. Persists through death. |
| `GeneJournal` (player component) | Stores discovered mobs, genes and mutations. |
| `CapacityCheck` + `DegradeStrategy` (interface) | Compares a genome with the player's capacity and, if it's over, degrades it. The removal order from §3.4 is the first strategy, so the order can be changed without touching injection code. |

Anything else that should scale with Complexity later (sickness, raw injection quality, processing time)
can either read the continuous Complexity value through a `Scaling` rule, or get a new per-tier field.

## 5. Core loop

```
Craft syringe -> sample a mob -> identify -> inject raw (gamble)                            Phase 1
                                          -> process (cost by tier) -> genetic material -> inject (controlled)   Phase 2-3
                                                                              |
                                              splice two genetic materials -> raw sample -> process again
      ^                                                                                                     |
      +---------------------- new zones, new mobs, better materials, bench upgrades <-----------------------+
```

## 6. Phase 1: Sampling & raw injection

**Decided**
- The player crafts Syringes, uses them on mobs, identifies the sample, and injects it.
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

Mobs without a profile fall back to a generic pool built from their stats. Without new stats, much of
what makes a creature distinctive has to come from **effects**. Example profiles (placeholders, effect
names need checking against the vanilla list):

| Mob | Stat pool | Effect | Possible drawback |
| --- | --- | --- | --- |
| Cow | Max Health +3–8% | Regeneration (temporary) | Max Stamina −5% |
| Rabbit | Max Stamina +5–10% | Speed (temporary) | Max Health −5% |
| Fish / aquatic | Max Oxygen +10–25% | Water breathing (temporary) | Max Stamina −5% |
| Wolf | Max Health +2–4%, Max Stamina +2–4% | Strength (temporary) | Max Mana −10% |

**Proposal: making Phase 1 feel rewarding** (review item E1)

Phase 1 is where players decide whether the mod is worth their time, so its rewards must be noticeable.
Five changes, all in data except the Journal:

1. **Bigger rolls.** Capacity already caps the total, so single genes can be large: up to +10–20% on one
   stat in Zone 1. At a weight of 0.8, a +20% Health gene costs 16 of the starting 20 capacity, so the
   player still can't stack several of them.
2. **Longer temporary effects.** The "taste" effect lasts **2–5 minutes**, long enough to use on an
   expedition. A gene that came out weakened in a raw injection gives half the duration. Set per mob in
   the gene profile (`"TemporaryEffect": { "Id": "…", "Duration": 180 }`).
3. **An early permanent change.** A few Zone 1 species carry one weak permanent utility effect (Complexity
   8–12, e.g. night vision from a cave creature). It fits in the starting capacity with a small stat gene,
   so the first "my body changed" moment comes early.
4. **Discovery moments.** The first sample of each species is identified for free, adds a Gene Journal
   entry, and shows a notification with progress ("New genome: Cow, Zone 1 3/12").
5. **Journal milestones teach recipes.** Sampling enough species in a zone teaches a recipe, using
   vanilla's recipe knowledge (`KnowledgeRequired` in item recipes). For example, 6 Zone 1 species teach
   the Lab Station T2 upgrade, and a full zone teaches that tier's Genome Expansion recipe. Expansions are
   still crafted, only the recipe is unlocked by exploring. This gives Phase 1 a clear goal and leads
   straight into Phase 2.

**Proposal: identification**
- A fresh sample shows as "Unidentified <Mob> Sample".
- The Lab Station identifies it for a cheap reagent, which reveals its genes and Complexity. The current
  Lab Station screen already shows this view.
- Identification shows what the sample **could** give. The real outcome is rolled at injection.
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

## 7. Phase 2: Processing & splicing (steering the odds)

**Decided**
- **Processing** a raw sample: the player chooses which stats and effects to keep. Complexity is
  calculated from what's kept, and the cost depends on the resulting Complexity tier (§4). The result is
  **genetic material**.
- Genetic material **can't be processed again**, but it **can be spliced**.
- **Splicing** always takes **two** samples. For **each stat**, one sample's value wins at random, with a
  light bias toward the sample with the higher Complexity. The mutation chance rises slightly with the
  two samples' combined Complexity.
- The result of a splice is a **raw** sample. It has to be processed before it can be spliced again (or
  injected in a controlled way). Every round of splicing therefore costs a processing step.
- **Splicing cost** uses the Complexity tier system (§4). The tier is picked from the **average** of the
  two samples' Complexity.
- Only **one mutation** can exist in a sample, or on the player, at a time.
- **New mutations only come from splicing.** Processing never creates a mutation: it only keeps one that
  is already in the source sample.

**Proposal: details**
- Phase 2 is the Lab Station's tier 2 upgrade (vanilla benches already upgrade this way), so the player
  keeps a single genetics bench.
- Splice bias: base 50/50, plus about 5% toward the higher-Complexity sample for every 10 points of
  difference, capped at 65/35.
- Mutation chance on splice: `2% + 0.05% × combined Complexity`, so two Complexity 20 samples give 4% and
  two Complexity 80 samples give 10%. The mutation is drawn from the Mutation Codex by `Weight`, among
  mutations whose `MinTier` the result reaches.
- When both inputs carry a mutation, the mutation is decided like a stat: one wins at random, with the
  same bias.
- Effects are decided like stats: each effect that appears in either input is kept from one side or the
  other at random.

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

**Proposal: how it works**

- **Extract.** Put a genetic material in the Lab Station T3 and pick **one** gene from it. The material is
  consumed, and the gene is added to the library with its exact value. The cost uses the Complexity tier
  of that gene alone.
- **Library entries are reusable.** An entry is knowledge, not an item: once extracted, it can be used in
  any number of combinations. This makes extraction a collection goal, like the Journal, and the cost of
  each combination is what limits power.
- **Combine.** Pick genes from the library into a new genome and pay for it. The result is genetic
  material: injectable, and spliceable. The cost uses the Complexity tier of the result, times the
  `MutationCostMultiplier` for multiple mutations (×1, ×3, ×9, ×27, with a hard cap of e.g. 4).
- **Stat fusion: where the late-game spike comes from** (review item E2). A combination can include the
  same stat several times. Each extra copy adds less: the best adds 100% of its value, the 2nd 50%, the
  3rd 25%, the 4th 12.5%. So three +20% Health entries give +35%. Complexity counts the **full** value
  of every copy, so fused stats get expensive fast and capacity still limits them.
- **Refine.** A library entry can be upgraded to a higher value with rare reagents, past its mob's
  normal range, at a cost that grows with each step. This is an extra late-game resource sink.
- **Storage.** The library belongs to the player (a `GeneLibrary` component, like the Journal), so it's
  never lost with a block. Players share genes by giving each other genetic material to extract.

### 8.2 Negative genes in the library (Open, ideas below)

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

Recommendation: start with **1**, which gives **2** for free, and keep **3** for the mob features.

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
// Server/Genetics/SicknessEvents.json
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

### 9.4 Gene Journal (Proposal)

A per-player log of every mob sampled and every gene and mutation discovered. It's a collection goal,
and its milestones teach recipes (§6). (Called a Journal so it isn't confused with the Codices, which are
data files.)

## 10. Progression & gating summary (Proposal)

| Phase | Unlocked by | Main resources | Player gets |
| --- | --- | --- | --- |
| 1 | Workbench → Syringe and Lab Station T1 (Zone 1) | Basic materials, ID reagent | Raw injection of low-Complexity Zone 1 samples, copying; capacity 20 |
| 1+ | Better syringes (ore tiers) | Ores, glass | Stronger mobs, first permanent effects, riskier raw injections |
| 2 | Lab Station T2 | Simple and Moderate tier materials | Processing, splicing, single mutation; capacity up to 45 |
| 3 | Lab Station T3 | Complex, Advanced and Exotic tier materials | Genetic Library: extract, combine, fuse and refine genes; multiple mutations; capacity up to 200 |

## 11. Balancing levers

All of these live in data files or config rather than code:

- Effect Codex: stat weights, effect Complexity values
- Mutation Codex: mutation Complexity, stats, behaviors, weights, minimum tiers
- Complexity tiers: ranges, station gates, materials, scaling rules, mutation cost multiplier
- Capacity: base value, value after each upgrade, overflow sickness rate, degradation order
- Drawback discount (60%) and cap (30%)
- Raw injection curve (C0) and what a failed gene turns into
- Sickness per Complexity point (raw and processed), decay rate, band thresholds, event weights and cooldowns
- Library: extraction and combination costs, fusion falloff, refine costs
- Splice bias per Complexity difference and its cap; mutation base chance and Complexity factor
- Roll counts and ranges per mob profile

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
- **There are clear goals at every stage:** the next syringe tier, Journal milestones, the next Lab
  Station tier, the next Genome Expansion, the next mutation, the next library entry.
- **The design can grow.** The Codices, tiers and mob profiles are all data, so content can be added
  without code.
- **Sickness adds personality.** Events based on the source mob make every injection a little story.

| # | Risk | Status |
| --- | --- | --- |
| E1 | **Phase 1 rewards feel small.** With vanilla stats only, early injections were barely noticeable. | **Proposal** in §6: bigger rolls, 2–5 minute effects, an early permanent utility effect, discovery moments, and Journal milestones that teach recipes. |
| E2 | **The late-game spike had no source.** Splicing and processing never made a stat bigger than the best mob roll. | **Addressed** by the Genetic Library (§8.1): stat fusion and refining let stats grow, with Complexity and capacity still limiting them. |
| E3 | **Mid game has long stretches of chance.** Every splice is random per stat, and every round needs a processing step. | **Open.** Recommendation: show each stat's odds in the Lab Station before splicing (e.g. "Health: 60% from A"). |
| E4 | **Splicing rules had gaps.** | **Partly decided:** cost uses the tier of the two samples' average Complexity. **Open:** what happens to a gene that only one input has. Recommendation: it carries over with a 50% chance, plus the Complexity bias. |

### 13.2 Exploits

| # | Exploit | Status |
| --- | --- | --- |
| X1 | **Free potions.** Reusable syringes, unlimited mobs and temporary effects that survive an override could make cheap raw samples a free potion supply. | **Addressed.** Raw injections build up a lot of sickness, so spamming them quickly reaches Severe. Processed samples cause less, but syringes don't stack and processing costs resources, so a player can't carry many. **Proposal kept:** copies of yourself carry no temporary effect, so copying can't be used as a free buff either. |
| X2 | **Gifted expansions.** A high-level player can give a new player end-game expansions and genetic material. | **Accepted as a feature.** The base game lets players gift end-game items too. |
| X3 | **Free drawbacks.** A drawback on an unused stat (−Mana, −Oxygen) costs nothing but lowers Complexity. This matters more now that negative genes may be extracted into the library. | **Open.** Recommendation: give each stat its own drawback weight in the Effect Codex, low for stats that are easy to ignore. |
| X4 | **Dying cures sickness.** | **Addressed:** sickness persists through death (§9.2). Check once the stat exists, because it depends on how vanilla handles stats on respawn. |
| X5 | **Mutation farming** by splicing cheap materials for the mutation roll. | **Mostly handled** by `MinTier`. Recommendation: make `MinTier` required for every mutation. |
| X6 | **Lucky raw injections** giving processed quality for free. | **No change needed:** the odds are very low, capacity still applies, and raw sickness limits retries. |
| X7 | **Library power creep.** Reusable library entries plus stat fusion could, in theory, build very large genomes. | **Handled by design:** fusion has diminishing returns, Complexity counts the full value of every copy, and capacity caps what the player can carry. This is the intended late-game spike. |

### 13.3 Early and mid game balance

| # | Issue | Status |
| --- | --- | --- |
| B1 | **Permanent effects could arrive too early** with the example costs (permanent Speed at 30 fits mid-game capacity 45). | **Deferred:** values live in the Effect Codex and will be tuned during balancing. Keep in mind when setting them: combat and movement effects should cost more than mid-game capacity. |
| B2 | **Phase 1 permanent effects were out of reach,** because they exceeded starting capacity. | **Proposal** in §6: a few Zone 1 species carry weak permanent utility effects that fit in 20. |
| B3 | **Sickness numbers needed real units.** | **Proposal** in §9.2: bands with event frequencies, and about 3 minutes from Severe to zero. |
| B4 | **The starting capacity is tight but fair.** | No change. With bigger Phase 1 rolls (§6), one strong gene or two or three small ones fit in 20. |
| B5 | **Solo players can skip expansions,** but still can't process high tiers without the matching Lab Station. | No change. |

## 14. Open questions

1. **E1:** adopt the five Phase 1 changes in §6, in particular Journal milestones teaching recipes?
2. **Genetic Library (§8.1):** are library entries reusable knowledge (as proposed), or consumed when
   combined? And does the library belong to the player or to the Lab Station block?
3. **Stat fusion:** adopt diminishing returns (100% / 50% / 25% / 12.5%) for the same stat?
4. **Negative genes (§8.2):** start with them as Complexity reducers, as recommended?
5. **E4:** does a gene that only one splice input has carry over with a 50% chance?
6. **X3:** give each stat its own drawback weight?
7. **E3:** show splice odds in the Lab Station?

## 15. Implementation status

| Feature | Status |
| --- | --- |
| Syringe: sample a mob into item metadata | Done (genome format v1: genes, raw/processed flag, Complexity, source) |
| Lab Station: one-slot screen showing stats and effects | Done |
| Effect Codex, Mutation Codex, Complexity tiers (data + loaders) | Codices and mob profiles done; tiers not started |
| Complexity calculation | Done (raw samples, with drawback limits) |
| Genome Capacity stat, Genome Expansion, degradation | Not started |
| Mob gene profiles | Done (placeholder profiles for cow, rabbit, wolf, fish, frog, bat/owl, and a default) |
| Raw self-injection and active genome | Done (quality roll, override rule, kept through death, syringe emptied) |
| Copying your genome | Done |
| Genetic Sickness stat and debuffs | Not started |
| Phase 2 processing and splicing | Not started |
| Phase 3 Genetic Library | Not started |
