package com.gladunalexander.optional;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

class DefaultOptional<T> implements Optional<T> {

    public static final Optional<?> EMPTY = new DefaultOptional<>(null);

    private final T value;

    public DefaultOptional(T value) {
        this.value = value;
    }

    @Override
    public <R> Optional<R> map(Function<? super T, ? extends R> function) {
        if (value == null) Optional.empty();
        return Optional.of(function.apply(value));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> Optional<R> flatMap(Function<? super T, Optional<? extends R>> function) {
        if (value == null) Optional.empty();
        return (Optional<R>) function.apply(value);
    }

    @Override
    public void ifPresent(Consumer<? super T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }

    @Override
    public T get() {
        return Objects.requireNonNull(value);
    }

    @Override
    public boolean isPresent() {
        return value != null;
    }
}
