package com.genetics.mutation;

import com.genetics.TestAssets;
import com.genetics.codex.MobGeneProfile;
import com.genetics.codex.MutationEntry;
import com.hypixel.hytale.protocol.MovementStates;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Design rules for mutations (design doc §3.3): every mutation reacts to something (no purely passive ones), none adds
 * a flat stat, each is themed to existing species, and the triggers behave as described.
 */
class MutationRulesTest {
    @BeforeAll
    static void load() {
        TestAssets.load();
    }

    private static Map<String, MutationEntry> mutations() {
        return MutationEntry.getAssetMap().getAssetMap();
    }

    @Test
    void everyMutationIsActiveThemedAndDescribed() {
        Map<String, MobGeneProfile> profiles = MobGeneProfile.getAssetMap().getAssetMap();
        List<String> problems = new ArrayList<>();
        for (MutationEntry entry : mutations().values()) {
            String id = entry.getId();
            if (entry.getBehaviors().length == 0) problems.add(id + ": no behavior (mutations must react to something)");
            if (!entry.getStats().isEmpty()) problems.add(id + ": flat stats (only conditional, multiplicative effects are allowed)");
            if (entry.getEffects().length > 0) problems.add(id + ": permanent effects (mutations must be active or reactive)");
            if (entry.getDescription() == null) problems.add(id + ": no Description");
            if (entry.getSpecies().length == 0) problems.add(id + ": not themed to any species");
            for (String species : entry.getSpecies()) if (!profiles.containsKey(species)) problems.add(id + ": unknown species " + species);
        }
        assertEquals(List.of(), problems);
        assertTrue(mutations().size() >= 20, "about 20 mutations");
    }

    @Test
    void bossSignatureMutationsExist() {
        int bosses = 0;
        for (MobGeneProfile profile : MobGeneProfile.getAssetMap().getAssetMap().values()) {
            if (profile.getSignatureMutation() == null) continue;
            bosses++;
            assertNotNull(mutations().get(profile.getSignatureMutation()), profile.getId() + " signature " + profile.getSignatureMutation());
            assertEquals(0.5, profile.getSignatureChance(), profile.getId());
        }
        assertTrue(bosses >= 4);
    }

    private static Behavior behavior(String mutation, Class<? extends Behavior> type) {
        for (Behavior behavior : mutations().get(mutation).getBehaviors()) if (type.isInstance(behavior)) return behavior;
        throw new AssertionError(mutation + " has no " + type.getSimpleName());
    }

    private static Behavior.Input input(MovementStates states, double y, double dt, double health, boolean day) {
        return new Behavior.Input(health, 0, !day, day, states, y, dt, false, id -> false);
    }

    @Test
    void featherfallFiresOncePerFallPastSixBlocks() {
        Behavior fall = behavior("Featherfall", Behavior.OnFall.class);
        Behavior.Memory memory = new Behavior.Memory();
        MovementStates ground = new MovementStates();
        ground.onGround = true;
        MovementStates air = new MovementStates();
        air.falling = true;
        assertFalse(fall.tick(input(ground, 100, 0.05, 1, true), memory));
        assertFalse(fall.tick(input(air, 97, 0.05, 1, true), memory), "3 blocks");
        assertTrue(fall.tick(input(air, 93.5, 0.05, 1, true), memory), "6.5 blocks");
        assertFalse(fall.tick(input(air, 80, 0.05, 1, true), memory), "once per fall");
        assertFalse(fall.tick(input(ground, 80, 0.05, 1, true), memory));
        assertFalse(fall.tick(input(air, 78, 0.05, 1, true), memory), "re-armed, new fall starts at 80");
    }

    @Test
    void springLegsNeedsACrouchBeforeTheJump() {
        Behavior jump = behavior("SpringLegs", Behavior.OnChargedJump.class);
        Behavior.Memory memory = new Behavior.Memory();
        MovementStates crouch = new MovementStates();
        crouch.onGround = true;
        crouch.crouching = true;
        MovementStates jumping = new MovementStates();
        jumping.jumping = true;
        assertFalse(jump.tick(input(crouch, 0, 0.5, 1, true), memory));
        assertFalse(jump.tick(input(jumping, 1, 0.05, 1, true), memory), "only 0.5 s of crouching");
        for (int i = 0; i < 3; i++) jump.tick(input(crouch, 0, 0.4, 1, true), memory);
        assertTrue(jump.tick(input(jumping, 1, 0.05, 1, true), memory), "1.2 s of crouching");
    }

    @Test
    void photosynthesisWaitsThreeSecondsOfStandingInDaylight() {
        Behavior sun = behavior("Photosynthesis", Behavior.WhileHeld.class);
        Behavior.Memory memory = new Behavior.Memory();
        MovementStates still = new MovementStates();
        still.onGround = true;
        still.horizontalIdle = true;
        MovementStates walking = new MovementStates();
        walking.onGround = true;
        assertFalse(sun.tick(input(still, 0, 2, 1, true), memory));
        assertFalse(sun.tick(input(walking, 0, 2, 1, true), memory), "moving resets it");
        assertFalse(sun.tick(input(still, 0, 2, 1, true), memory));
        assertTrue(sun.tick(input(still, 0, 1.5, 1, true), memory));
        assertFalse(sun.tick(input(still, 0, 5, 1, false), memory), "not at night");
    }

    @Test
    void grizzlyFuryOnlyBelowFortyPercent() {
        Behavior fury = behavior("GrizzlyFury", Behavior.WhileHeld.class);
        assertFalse(fury.tick(input(null, 0, 0.1, 0.5, true), new Behavior.Memory()));
        assertTrue(fury.tick(input(null, 0, 0.1, 0.3, true), new Behavior.Memory()));
    }

    @Test
    void stacksCapAndExpire() {
        MutationSystem.State state = new MutationSystem.State();
        for (int i = 0; i < 7; i++) state.addStacks("Rush", 1, 5, 3);
        assertEquals(5, state.stacks("Rush"));
        state.clock += 4;
        assertEquals(0, state.stacks("Rush"), "expired after the window");
    }

    @Test
    void splicingFavoursMutationsThemedToTheInputs() {
        int themed = 0;
        int plain = 0;
        Random random = new Random(42);
        for (int i = 0; i < 4000; i++) {
            MutationEntry withCanids = com.genetics.process.SplicerAccess.pick(5, Set.of("Mammal_Canid"), random);
            MutationEntry without = com.genetics.process.SplicerAccess.pick(5, Set.of(), random);
            if (withCanids != null && withCanids.getId().equals("Bloodfang")) themed++;
            if (without != null && without.getId().equals("Bloodfang")) plain++;
        }
        assertTrue(themed > plain * 2, "themed " + themed + " vs " + plain);
    }
}
