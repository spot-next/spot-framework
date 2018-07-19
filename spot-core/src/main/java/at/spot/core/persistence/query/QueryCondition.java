package at.spot.core.persistence.query;

import java.util.function.Predicate;

import at.spot.core.types.Item;

@FunctionalInterface
public interface QueryCondition<T extends Item> extends Predicate<T> {
}
