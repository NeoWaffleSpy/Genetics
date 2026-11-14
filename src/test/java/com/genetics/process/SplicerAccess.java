package com.genetics.process;

import com.genetics.codex.MutationEntry;

import java.util.Random;
import java.util.Set;

/** Test access to Splicer's package-private mutation roll. */
public final class SplicerAccess {
    private SplicerAccess() {
    }

    public static MutationEntry pick(int tierRank, Set<String> species, Random random) {
        return Splicer.pickMutation(tierRank, species, random);
    }
}
