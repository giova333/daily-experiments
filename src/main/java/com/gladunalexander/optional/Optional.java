package com.gladunalexander.optional;

import java.util.function.Consumer;
import java.util.function.Function;

interface Optional<T> {

    <R> Optional<R> map(Function<? super T, ? extends R> function);

    <R> Optional<R> flatMap(Function<? super T, Optional<? extends R>> function);

    void ifPresent(Consumer<? super T> consumer);

    T get();

    boolean isPresent();

    static <T> Optional<T> of(T value) {
        return new DefaultOptional<>(value);
    }

    @SuppressWarnings("unchecked")
    static <T> Optional<T> empty() {
        return (Optional<T>) DefaultOptional.EMPTY;
    }
}
