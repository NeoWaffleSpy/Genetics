package com.genetics.action;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Runs lists of actions and keeps track of their repeats. One runner per entity; {@link #tick} must be called every
 * tick with that entity's context.
 */
public final class ActionRunner {
    private static final class Pending {
        final Action action;
        double delay;

        Pending(Action action, double delay) {
            this.action = action;
            this.delay = delay;
        }
    }

    private final List<Pending> pending = new ArrayList<>();

    /** Runs each action now (or after its delay), and schedules its repeats. */
    public void start(@Nonnull Action[] actions, @Nonnull ActionContext context) {
        for (Action action : actions) {
            if (action == null) continue;
            for (int i = 0; i < action.getRepeat(); i++) {
                double at = action.getDelay() + action.getInterval() * i;
                if (at <= 0) action.run(context);
                else pending.add(new Pending(action, at));
            }
        }
    }

    /** Runs the repeats that are due. */
    public void tick(float dt, @Nonnull ActionContext context) {
        if (pending.isEmpty()) return;
        List<Action> due = new ArrayList<>();
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending next = it.next();
            next.delay -= dt;
            if (next.delay <= 0) {
                due.add(next.action);
                it.remove();
            }
        }
        for (Action action : due) action.run(context);
    }

    public void clear() {
        pending.clear();
    }
}
