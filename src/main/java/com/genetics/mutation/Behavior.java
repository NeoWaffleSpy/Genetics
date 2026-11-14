package com.genetics.mutation;

import com.genetics.action.Action;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.lookup.CodecMapCodec;
import com.hypixel.hytale.protocol.MovementStates;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A mutation behavior (design doc §3.3): a trigger, referenced from data by {@code "Type"}, that runs actions from the
 * shared {@link Action} system when it fires.
 * <pre>
 * { "Type": "OnHit", "Chance": 0.15, "Cooldown": 8, "Conditions": { "Night": true },
 *   "TargetActions": [ { "Type": "ApplyEffect", "Id": "Root", "Duration": 1.5 } ] }
 * </pre>
 * Every behavior accepts {@code Cooldown} (seconds before it can fire again), {@code Actions} (run on the mutated
 * entity), an optional {@code Message} shown to the player, and optional {@code Conditions} that must hold.
 * <p>
 * Two families: <b>tick triggers</b> (checked every tick by {@link MutationSystem}: OnLowHealth, OnDamaged, AtNight,
 * Periodic, WhileHeld, OnFall, OnChargedJump, OnEffect) and <b>event triggers</b> (run by {@link MutationDamageSystems}
 * from damage events: OnHit, OnKill, OnHurt, OnLethal).
 */
public abstract class Behavior {
    public static final CodecMapCodec<Behavior> CODEC = new CodecMapCodec<>("Type");

    protected static final BuilderCodec<Behavior> BASE_CODEC = BuilderCodec.abstractBuilder(Behavior.class)
            .append(new KeyedCodec<>("Cooldown", Codec.DOUBLE), (t, v) -> t.cooldown = v, t -> t.cooldown)
            .documentation("Seconds before the behavior can fire again.").add()
            .append(new KeyedCodec<>("Actions", new ArrayCodec<>(Action.CODEC, Action[]::new)), (t, v) -> t.actions = v, t -> t.actions).add()
            .append(new KeyedCodec<>("Message", Codec.STRING), (t, v) -> t.message = v, t -> t.message)
            .documentation("Optional text or translation key shown to the player when the behavior fires.").add()
            .append(new KeyedCodec<>("Conditions", Conditions.CODEC), (t, v) -> t.conditions = v, t -> t.conditions)
            .documentation("What must hold for the behavior to fire (all of them).").add()
            .build();

    protected double cooldown;
    protected Action[] actions = new Action[0];
    @Nullable
    protected String message;
    @Nullable
    protected Conditions conditions;

    /** Registers the built-in behavior types. Call once, before assets load. */
    public static void registerBuiltIns() {
        CODEC.register(OnLowHealth.TYPE, OnLowHealth.class, OnLowHealth.CODEC);
        CODEC.register(OnDamaged.TYPE, OnDamaged.class, OnDamaged.CODEC);
        CODEC.register(AtNight.TYPE, AtNight.class, AtNight.CODEC);
        CODEC.register(Periodic.TYPE, Periodic.class, Periodic.CODEC);
        CODEC.register(WhileHeld.TYPE, WhileHeld.class, WhileHeld.CODEC);
        CODEC.register(OnFall.TYPE, OnFall.class, OnFall.CODEC);
        CODEC.register(OnChargedJump.TYPE, OnChargedJump.class, OnChargedJump.CODEC);
        CODEC.register(OnEffect.TYPE, OnEffect.class, OnEffect.CODEC);
        CODEC.register(OnHit.TYPE, OnHit.class, OnHit.CODEC);
        CODEC.register(OnKill.TYPE, OnKill.class, OnKill.CODEC);
        CODEC.register(OnHurt.TYPE, OnHurt.class, OnHurt.CODEC);
        CODEC.register(OnLethal.TYPE, OnLethal.class, OnLethal.CODEC);
    }

    public double getCooldown() {
        return Math.max(0, cooldown);
    }

    @Nonnull
    public Action[] getActions() {
        return actions != null ? actions : new Action[0];
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    /** Every action list of this behavior (its own, and those run on another entity), for validation. */
    @Nonnull
    public List<Action[]> allActions() {
        return List.<Action[]>of(getActions());
    }

    /** True if the behavior's Conditions hold (or it has none). */
    public boolean conditionsMet(@Nonnull Input input) {
        return conditions == null || conditions.test(input);
    }

    /** One tick: whether a tick trigger fires now (cooldowns are handled by the caller). Event triggers never fire here. */
    public boolean tick(@Nonnull Input input, @Nonnull Memory memory) {
        return conditionsMet(input) && check(input, memory);
    }

    /** The trigger itself, for tick triggers. */
    protected abstract boolean check(@Nonnull Input input, @Nonnull Memory memory);

    /** A short description for the Catalogue, e.g. "below 25% health". */
    @Nonnull
    public String describe() {
        String what = describeTrigger();
        return conditions != null && !conditions.describe().isEmpty() ? what + " (" + conditions.describe() + ")" : what;
    }

    @Nonnull
    protected abstract String describeTrigger();

    /** What the triggers look at, read once per tick. {@code states} is null for entities without movement. */
    public record Input(double healthRatio, double damageTaken, boolean night, boolean day, @Nullable MovementStates states,
                        double y, double dt, boolean sprintedRecently, @Nonnull Predicate<String> hasEffect) {
        public static final Input NONE = new Input(1, 0, false, true, null, 0, 0, false, id -> false);

        boolean onGround() {
            return states == null || states.onGround;
        }

        boolean still() {
            return states == null || states.horizontalIdle;
        }
    }

    /** Per-entity, per-behavior bookkeeping kept between ticks and events. */
    public static final class Memory {
        public boolean armed = true;
        public double timer;
        public double value = Double.NaN;
        public int count;
        public double windowLeft;
        @Nullable
        public Object lastTarget;
    }

    // ---------------------------------------------------------------- Conditions

    /** Optional requirements shared by every trigger. Absent fields are not checked. */
    public static final class Conditions {
        public static final BuilderCodec<Conditions> CODEC = BuilderCodec.builder(Conditions.class, Conditions::new)
                .append(new KeyedCodec<>("Night", Codec.BOOLEAN), (t, v) -> t.night = v, t -> t.night).add()
                .append(new KeyedCodec<>("Day", Codec.BOOLEAN), (t, v) -> t.day = v, t -> t.day)
                .documentation("Daytime (sunlight), not a check for open sky.").add()
                .append(new KeyedCodec<>("InWater", Codec.BOOLEAN), (t, v) -> t.inWater = v, t -> t.inWater).add()
                .append(new KeyedCodec<>("Airborne", Codec.BOOLEAN), (t, v) -> t.airborne = v, t -> t.airborne).add()
                .append(new KeyedCodec<>("SprintJump", Codec.BOOLEAN), (t, v) -> t.sprintJump = v, t -> t.sprintJump)
                .documentation("In the air within 1.5 s of sprinting.").add()
                .append(new KeyedCodec<>("Crouching", Codec.BOOLEAN), (t, v) -> t.crouching = v, t -> t.crouching).add()
                .append(new KeyedCodec<>("Still", Codec.BOOLEAN), (t, v) -> t.still = v, t -> t.still)
                .documentation("Not moving horizontally.").add()
                .append(new KeyedCodec<>("HealthBelow", Codec.DOUBLE), (t, v) -> t.healthBelow = v, t -> t.healthBelow).add()
                .append(new KeyedCodec<>("HealthAbove", Codec.DOUBLE), (t, v) -> t.healthAbove = v, t -> t.healthAbove).add()
                .build();

        private Boolean night;
        private Boolean day;
        private Boolean inWater;
        private Boolean airborne;
        private Boolean sprintJump;
        private Boolean crouching;
        private Boolean still;
        private Double healthBelow;
        private Double healthAbove;

        public boolean test(@Nonnull Input input) {
            MovementStates s = input.states();
            if (night != null && night != input.night()) return false;
            if (day != null && day != input.day()) return false;
            if (inWater != null && inWater != (s != null && (s.inFluid || s.swimming))) return false;
            if (airborne != null && airborne == input.onGround()) return false;
            if (sprintJump != null && sprintJump != (!input.onGround() && input.sprintedRecently())) return false;
            if (crouching != null && crouching != (s != null && (s.crouching || s.forcedCrouching))) return false;
            if (still != null && still != input.still()) return false;
            if (healthBelow != null && input.healthRatio() >= healthBelow) return false;
            return healthAbove == null || input.healthRatio() > healthAbove;
        }

        @Nonnull
        String describe() {
            List<String> parts = new ArrayList<>();
            if (Boolean.TRUE.equals(night)) parts.add("at night");
            if (Boolean.TRUE.equals(day)) parts.add("by day");
            if (Boolean.TRUE.equals(inWater)) parts.add("in water");
            if (Boolean.TRUE.equals(airborne)) parts.add("in the air");
            if (Boolean.TRUE.equals(sprintJump)) parts.add("after a sprint jump");
            if (Boolean.TRUE.equals(crouching)) parts.add("crouching");
            if (Boolean.TRUE.equals(still)) parts.add("standing still");
            if (healthBelow != null) parts.add("below " + Math.round(healthBelow * 100) + "% health");
            if (healthAbove != null) parts.add("above " + Math.round(healthAbove * 100) + "% health");
            return String.join(", ", parts);
        }
    }

    // ---------------------------------------------------------------- Tick triggers

    /** Fires once each time health drops below {@code Threshold} (a fraction of max health), then re-arms above it. */
    public static final class OnLowHealth extends Behavior {
        public static final String TYPE = "OnLowHealth";
        public static final BuilderCodec<OnLowHealth> CODEC = BuilderCodec.builder(OnLowHealth.class, OnLowHealth::new, BASE_CODEC)
                .append(new KeyedCodec<>("Threshold", Codec.DOUBLE), (t, v) -> t.threshold = v, t -> t.threshold)
                .documentation("Fraction of max health, e.g. 0.25.").add()
                .build();

        private double threshold = 0.25;

        @Override
        protected boolean check(@Nonnull Input input, @Nonnull Memory memory) {
            if (input.healthRatio() > threshold) {
                memory.armed = true;
                return false;
            }
            if (!memory.armed) return false;
            memory.armed = false;
            return true;
        }

        @Nonnull
        @Override
        protected String describeTrigger() {
            return "below " + Math.round(threshold * 100) + "% health";
        }
    }

    /** Fires when the entity loses at least {@code MinDamage} health in one tick (prefer OnHurt for hits). */
    public static final class OnDamaged extends Behavior {
        public static final String TYPE = "OnDamaged";
        public static final BuilderCodec<OnDamaged> CODEC = BuilderCodec.builder(OnDamaged.class, OnDamaged::new, BASE_CODEC)
                .append(new KeyedCodec<>("MinDamage", Codec.DOUBLE), (t, v) -> t.minDamage = v, t -> t.minDamage).add()
                .build();

        private double minDamage = 1;

        @Override
        protected boolean check(@Nonnull Input input, @Nonnull Memory memory) {
            return input.damageTaken() >= Math.max(0.01, minDamage);
        }

        @Nonnull
        @Override
        protected String describeTrigger() {
            return "when hurt";
        }
    }

    /** Fires while it is night, every Cooldown seconds (pair it with an effect lasting a bit longer than the cooldown). */
    public static final class AtNight extends Behavior {
        public static final String TYPE = "AtNight";
        public static final BuilderCodec<AtNight> CODEC = BuilderCodec.builder(AtNight.class, AtNight::new, BASE_CODEC).build();

        @Override
        protected boolean check(@Nonnull Input input, @Nonnull Memory memory) {
            return input.night();
        }

        @Nonnull
        @Override
        protected String describeTrigger() {
            return "at night";
        }
    }

    /** Fires every Cooldown seconds. */
    public static final class Periodic extends Behavior {
        public static final String TYPE = "Periodic";
        public static final BuilderCodec<Periodic> CODEC = BuilderCodec.builder(Periodic.class, Periodic::new, BASE_CODEC).build();

        @Override
        protected boolean check(@Nonnull Input input, @Nonnull Memory memory) {
            return true;
        }

        @Nonnull
        @Override
        protected String describeTrigger() {
            return "every " + Math.round(getCooldown()) + " s";
        }
    }

    /**
     * Fires once the Conditions have held for {@code HoldTime} seconds without a break, then every Cooldown seconds
     * while they still hold ({@code Repeat}, default true) or only once per hold.
     */
    public static final class WhileHeld extends Behavior {
        public static final String TYPE = "WhileHeld";
        public static final BuilderCodec<WhileHeld> CODEC = BuilderCodec.builder(WhileHeld.class, WhileHeld::new, BASE_CODEC)
                .append(new KeyedCodec<>("HoldTime", Codec.DOUBLE), (t, v) -> t.holdTime = v, t -> t.holdTime).add()
                .append(new KeyedCodec<>("Repeat", Codec.BOOLEAN), (t, v) -> t.repeat = v == null || v, t -> t.repeat).add()
                .build();

        private double holdTime;
        private boolean repeat = true;

        @Override
        public boolean tick(@Nonnull Input input, @Nonnull Memory memory) {
            if (!conditionsMet(input)) {
                memory.timer = 0;
                memory.armed = true;
                return false;
            }
            memory.timer += input.dt();
            if (memory.timer < holdTime) return false;
            if (repeat) return true;
            if (!memory.armed) return false;
            memory.armed = false;
            return true;
        }

        @Override
        protected boolean check(@Nonnull Input input, @Nonnull Memory memory) {
            return false;
        }

        @Nonnull
        @Override
        public String describe() {
            String held = conditions != null ? conditions.describe() : "";
            return (held.isEmpty() ? "after " : held + " for ") + trim(holdTime) + " s";
        }

        @Nonnull
        @Override
        protected String describeTrigger() {
            return "held";
        }
    }

    /** Fires once per fall, when the entity has dropped at least {@code MinDistance} blocks since leaving the ground. */
    public static final class OnFall extends Behavior {
        public static final String TYPE = "OnFall";
        public static final BuilderCodec<OnFall> CODEC = BuilderCodec.builder(OnFall.class, OnFall::new, BASE_CODEC)
                .append(new KeyedCodec<>("MinDistance", Codec.DOUBLE), (t, v) -> t.minDistance = v, t -> t.minDistance).add()
                .build();

        private double minDistance = 6;

        @Override
        protected boolean check(@Nonnull Input input, @Nonnull Memory memory) {
            MovementStates s = input.states();
            if (s == null) return false;
            if (s.onGround || s.climbing || s.swimming || s.inFluid || s.gliding || s.flying || Double.isNaN(memory.value)) {
                memory.value = input.y();
                memory.armed = true;
                return false;
            }
            memory.value = Math.max(memory.value, input.y());
            if (!memory.armed || memory.value - input.y() < minDistance) return false;
            memory.armed = false;
            return true;
        }

        @Nonnull
        @Override
        protected String describeTrigger() {
            return "when falling more than " + trim(minDistance) + " blocks";
        }
    }

    /** Fires when the entity jumps right after crouching on the ground for at least {@code CrouchTime} seconds. */
    public static final class OnChargedJump extends Behavior {
        public static final String TYPE = "OnChargedJump";
        public static final BuilderCodec<OnChargedJump> CODEC = BuilderCodec.builder(OnChargedJump.class, OnChargedJump::new, BASE_CODEC)
                .append(new KeyedCodec<>("CrouchTime", Codec.DOUBLE), (t, v) -> t.crouchTime = v, t -> t.crouchTime).add()
                .build();

        /** How long after letting go of crouch the jump still counts. */
        private static final double GRACE = 0.6;

        private double crouchTime = 1;

        @Override
        protected boolean check(@Nonnull Input input, @Nonnull Memory memory) {
            MovementStates s = input.states();
            if (s == null) return false;
            boolean crouching = s.crouching || s.forcedCrouching;
            if (crouching && s.onGround) {
                memory.timer += input.dt();
                memory.windowLeft = GRACE;
                return false;
            }
            if (memory.timer <= 0) return false;
            memory.windowLeft -= input.dt();
            boolean jumped = !s.onGround && (s.jumping || s.falling);
            boolean charged = memory.timer >= crouchTime;
            if (jumped || memory.windowLeft <= 0) memory.timer = 0;
            return jumped && charged;
        }

        @Nonnull
        @Override
        protected String describeTrigger() {
            return "jumping after crouching " + trim(crouchTime) + " s";
        }
    }

    /** Fires when the entity has one of {@code Effects} (e.g. Slow, Freeze). Pair with RemoveEffect to cleanse it. */
    public static final class OnEffect extends Behavior {
        public static final String TYPE = "OnEffect";
        public static final BuilderCodec<OnEffect> CODEC = BuilderCodec.builder(OnEffect.class, OnEffect::new, BASE_CODEC)
                .append(new KeyedCodec<>("Effects", Codec.STRING_ARRAY), (t, v) -> t.effects = v, t -> t.effects).add()
                .build();

        private String[] effects = new String[0];

        @Nonnull
        public String[] getEffects() {
            return effects != null ? effects : new String[0];
        }

        @Override
        protected boolean check(@Nonnull Input input, @Nonnull Memory memory) {
            for (String effect : getEffects()) if (input.hasEffect().test(effect)) return true;
            return false;
        }

        @Nonnull
        @Override
        protected String describeTrigger() {
            return "when slowed or frozen";
        }
    }

    // ---------------------------------------------------------------- Event triggers

    /** Base for triggers run from damage events: never fire on a tick. */
    public abstract static class EventBehavior extends Behavior {
        @Override
        public boolean tick(@Nonnull Input input, @Nonnull Memory memory) {
            return false;
        }

        @Override
        protected boolean check(@Nonnull Input input, @Nonnull Memory memory) {
            return false;
        }
    }

    /** Behaviors that read or change a stack counter: a named count on the entity that expires after a while. */
    public interface Stacking {
        @Nullable
        String stackKey();
    }

    /**
     * Fires when the entity damages another one. {@code DamageBonus} multiplies that hit (0.5 = +50%),
     * {@code HealFraction} heals the entity for part of the damage dealt, {@code TargetActions} run on the target.
     * Stacks: {@code StackKey} + {@code StackGain} add to a counter (up to {@code StackMax}, reset after
     * {@code StackWindow} s) and each stack adds {@code BonusPerStack}; {@code RequireStacks} only fires once the counter
     * reaches it, and empties it.
     */
    public static final class OnHit extends EventBehavior implements Stacking {
        public static final String TYPE = "OnHit";
        public static final BuilderCodec<OnHit> CODEC = BuilderCodec.builder(OnHit.class, OnHit::new, BASE_CODEC)
                .append(new KeyedCodec<>("Chance", Codec.DOUBLE), (t, v) -> t.chance = v, t -> t.chance).add()
                .append(new KeyedCodec<>("EveryNth", Codec.INTEGER), (t, v) -> t.everyNth = v, t -> t.everyNth)
                .documentation("Fires on every Nth hit in a row on the same target.").add()
                .append(new KeyedCodec<>("Melee", Codec.BOOLEAN), (t, v) -> t.melee = v != null && v, t -> t.melee).add()
                .append(new KeyedCodec<>("TargetHealthBelow", Codec.DOUBLE), (t, v) -> t.targetHealthBelow = v, t -> t.targetHealthBelow).add()
                .append(new KeyedCodec<>("Unnoticed", Codec.BOOLEAN), (t, v) -> t.unnoticed = v != null && v, t -> t.unnoticed)
                .documentation("Only on targets that are not targeting the entity.").add()
                .append(new KeyedCodec<>("DamageBonus", Codec.DOUBLE), (t, v) -> t.damageBonus = v, t -> t.damageBonus).add()
                .append(new KeyedCodec<>("HealFraction", Codec.DOUBLE), (t, v) -> t.healFraction = v, t -> t.healFraction).add()
                .append(new KeyedCodec<>("TargetActions", new ArrayCodec<>(Action.CODEC, Action[]::new)), (t, v) -> t.targetActions = v, t -> t.targetActions).add()
                .append(new KeyedCodec<>("StackKey", Codec.STRING), (t, v) -> t.stackKey = v, t -> t.stackKey).add()
                .append(new KeyedCodec<>("StackGain", Codec.INTEGER), (t, v) -> t.stackGain = v, t -> t.stackGain).add()
                .append(new KeyedCodec<>("StackMax", Codec.INTEGER), (t, v) -> t.stackMax = v, t -> t.stackMax).add()
                .append(new KeyedCodec<>("StackWindow", Codec.DOUBLE), (t, v) -> t.stackWindow = v, t -> t.stackWindow).add()
                .append(new KeyedCodec<>("BonusPerStack", Codec.DOUBLE), (t, v) -> t.bonusPerStack = v, t -> t.bonusPerStack).add()
                .append(new KeyedCodec<>("RequireStacks", Codec.INTEGER), (t, v) -> t.requireStacks = v, t -> t.requireStacks).add()
                .build();

        double chance = 1;
        int everyNth;
        boolean melee;
        double targetHealthBelow;
        boolean unnoticed;
        double damageBonus;
        double healFraction;
        Action[] targetActions = new Action[0];
        String stackKey;
        int stackGain;
        int stackMax = 1;
        double stackWindow = 5;
        double bonusPerStack;
        int requireStacks;

        @Nonnull
        public Action[] getTargetActions() {
            return targetActions != null ? targetActions : new Action[0];
        }

        @Nullable
        @Override
        public String stackKey() {
            return stackKey;
        }

        @Nonnull
        @Override
        public List<Action[]> allActions() {
            return List.of(getActions(), getTargetActions());
        }

        @Nonnull
        @Override
        protected String describeTrigger() {
            if (requireStacks > 0) return "on the next hit at " + requireStacks + " stacks";
            if (everyNth > 1) return "every " + everyNth + "th hit on a target";
            if (chance < 1) return Math.round(chance * 100) + "% of hits";
            return "on hit";
        }
    }

    /** Fires when the entity kills another one. {@code TargetSpecies} limits it to species (gene profile ids). */
    public static final class OnKill extends EventBehavior {
        public static final String TYPE = "OnKill";
        public static final BuilderCodec<OnKill> CODEC = BuilderCodec.builder(OnKill.class, OnKill::new, BASE_CODEC)
                .append(new KeyedCodec<>("Chance", Codec.DOUBLE), (t, v) -> t.chance = v, t -> t.chance).add()
                .append(new KeyedCodec<>("TargetSpecies", Codec.STRING_ARRAY), (t, v) -> t.targetSpecies = v, t -> t.targetSpecies).add()
                .append(new KeyedCodec<>("TargetActions", new ArrayCodec<>(Action.CODEC, Action[]::new)), (t, v) -> t.targetActions = v, t -> t.targetActions).add()
                .build();

        double chance = 1;
        String[] targetSpecies = new String[0];
        Action[] targetActions = new Action[0];

        @Nonnull
        public Action[] getTargetActions() {
            return targetActions != null ? targetActions : new Action[0];
        }

        @Nonnull
        @Override
        public List<Action[]> allActions() {
            return List.of(getActions(), getTargetActions());
        }

        @Nonnull
        @Override
        protected String describeTrigger() {
            return (chance < 1 ? Math.round(chance * 100) + "% of kills" : "on a kill") + (targetSpecies != null && targetSpecies.length > 0 ? " of some species" : "");
        }
    }

    /**
     * Fires when the entity takes damage from a hit. Filters: {@code Causes} (damage cause ids), {@code FromEntity},
     * {@code Melee}, {@code MinFraction} (of max health in that hit), {@code MinDamage}; {@code EveryNth} with
     * {@code Window} fires on every Nth hit within that many seconds. {@code AttackerActions} run on the attacker.
     * Stacks: {@code StackKey} + {@code StackGain} add to a counter; {@code ClearStacks} empties one.
     */
    public static final class OnHurt extends EventBehavior implements Stacking {
        public static final String TYPE = "OnHurt";
        public static final BuilderCodec<OnHurt> CODEC = BuilderCodec.builder(OnHurt.class, OnHurt::new, BASE_CODEC)
                .append(new KeyedCodec<>("Causes", Codec.STRING_ARRAY), (t, v) -> t.causes = v, t -> t.causes).add()
                .append(new KeyedCodec<>("FromEntity", Codec.BOOLEAN), (t, v) -> t.fromEntity = v != null && v, t -> t.fromEntity).add()
                .append(new KeyedCodec<>("Melee", Codec.BOOLEAN), (t, v) -> t.melee = v != null && v, t -> t.melee).add()
                .append(new KeyedCodec<>("MinFraction", Codec.DOUBLE), (t, v) -> t.minFraction = v, t -> t.minFraction).add()
                .append(new KeyedCodec<>("MinDamage", Codec.DOUBLE), (t, v) -> t.minDamage = v, t -> t.minDamage).add()
                .append(new KeyedCodec<>("EveryNth", Codec.INTEGER), (t, v) -> t.everyNth = v, t -> t.everyNth).add()
                .append(new KeyedCodec<>("Window", Codec.DOUBLE), (t, v) -> t.window = v, t -> t.window).add()
                .append(new KeyedCodec<>("AttackerActions", new ArrayCodec<>(Action.CODEC, Action[]::new)), (t, v) -> t.attackerActions = v, t -> t.attackerActions).add()
                .append(new KeyedCodec<>("StackKey", Codec.STRING), (t, v) -> t.stackKey = v, t -> t.stackKey).add()
                .append(new KeyedCodec<>("StackGain", Codec.INTEGER), (t, v) -> t.stackGain = v, t -> t.stackGain).add()
                .append(new KeyedCodec<>("StackMax", Codec.INTEGER), (t, v) -> t.stackMax = v, t -> t.stackMax).add()
                .append(new KeyedCodec<>("StackWindow", Codec.DOUBLE), (t, v) -> t.stackWindow = v, t -> t.stackWindow).add()
                .append(new KeyedCodec<>("ClearStacks", Codec.STRING), (t, v) -> t.clearStacks = v, t -> t.clearStacks).add()
                .build();

        String[] causes = new String[0];
        boolean fromEntity;
        boolean melee;
        double minFraction;
        double minDamage;
        int everyNth;
        double window = 5;
        Action[] attackerActions = new Action[0];
        String stackKey;
        int stackGain;
        int stackMax = 1;
        double stackWindow = 30;
        String clearStacks;

        @Nonnull
        public Action[] getAttackerActions() {
            return attackerActions != null ? attackerActions : new Action[0];
        }

        @Nullable
        @Override
        public String stackKey() {
            return stackKey;
        }

        @Nonnull
        @Override
        public List<Action[]> allActions() {
            return List.of(getActions(), getAttackerActions());
        }

        @Nonnull
        @Override
        protected String describeTrigger() {
            if (causes != null && causes.length > 0) return "when hurt by " + String.join(" or ", causes).toLowerCase();
            if (everyNth > 1) return "every " + everyNth + "rd hit taken within " + trim(window) + " s";
            if (minFraction > 0) return "when one hit takes " + Math.round(minFraction * 100) + "% of your health";
            return melee ? "when hit in melee" : "when hit";
        }
    }

    /** Fires instead of dying: the killing blow leaves 1 health and {@code Invulnerable} seconds of immunity. */
    public static final class OnLethal extends EventBehavior {
        public static final String TYPE = "OnLethal";
        public static final BuilderCodec<OnLethal> CODEC = BuilderCodec.builder(OnLethal.class, OnLethal::new, BASE_CODEC)
                .append(new KeyedCodec<>("Invulnerable", Codec.DOUBLE), (t, v) -> t.invulnerable = v, t -> t.invulnerable).add()
                .build();

        double invulnerable = 3;

        @Nonnull
        @Override
        protected String describeTrigger() {
            return "on a killing blow";
        }
    }

    @Nonnull
    static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
