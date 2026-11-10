package com.genetics.genome;

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One trait in a genome. Genes only hold ids and values; names, icons and Complexity are looked up from the game
 * assets and the Codices when needed, so retuning the data files applies to existing samples.
 */
public sealed interface Gene {
    @Nonnull
    BsonDocument toBson();

    /** A modifier to a vanilla stat's max, in percent. Negative values are drawbacks. */
    record Stat(@Nonnull String stat, double percent) implements Gene {
        public boolean isDrawback() {
            return percent < 0;
        }

        @Nonnull
        @Override
        public BsonDocument toBson() {
            return new BsonDocument()
                    .append("Type", new BsonString("Stat"))
                    .append("Stat", new BsonString(stat))
                    .append("Percent", new BsonDouble(percent));
        }
    }

    /** A vanilla status effect. Duration, in seconds, only applies to temporary effects. */
    record Effect(@Nonnull String effect, boolean permanent, double duration) implements Gene {
        @Nonnull
        @Override
        public BsonDocument toBson() {
            BsonDocument doc = new BsonDocument()
                    .append("Type", new BsonString("Effect"))
                    .append("Effect", new BsonString(effect))
                    .append("Permanent", new BsonBoolean(permanent));
            if (!permanent) doc.append("Duration", new BsonDouble(duration));
            return doc;
        }
    }

    /** A mutation from the Mutation Codex. */
    record Mutation(@Nonnull String mutation) implements Gene {
        @Nonnull
        @Override
        public BsonDocument toBson() {
            return new BsonDocument()
                    .append("Type", new BsonString("Mutation"))
                    .append("Mutation", new BsonString(mutation));
        }
    }

    /** Reads a gene, or returns {@code null} if the document isn't a valid gene. */
    @Nullable
    static Gene fromBson(@Nonnull BsonDocument doc) {
        if (!doc.isString("Type")) return null;
        return switch (doc.getString("Type").getValue()) {
            case "Stat" -> doc.isString("Stat") && doc.isNumber("Percent")
                    ? new Stat(doc.getString("Stat").getValue(), doc.getNumber("Percent").doubleValue()) : null;
            case "Effect" -> doc.isString("Effect")
                    ? new Effect(doc.getString("Effect").getValue(),
                    doc.isBoolean("Permanent") && doc.getBoolean("Permanent").getValue(),
                    doc.isNumber("Duration") ? doc.getNumber("Duration").doubleValue() : 0)
                    : null;
            case "Mutation" -> doc.isString("Mutation") ? new Mutation(doc.getString("Mutation").getValue()) : null;
            default -> null;
        };
    }
}
