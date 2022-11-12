package com.gladunalexander.optional.v2;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

interface Optional<T> {

    <R> Optional<R> map(Function<? super T, ? extends R> function);

    <R> Optional<R> flatMap(Function<? super T, Optional<? extends R>> function);

    void ifPresent(Consumer<? super T> consumer);

    T get();

    boolean isPresent();

    static <T> Optional<T> of(T value) {
        return Objects.isNull(value)
                ? none()
                : some(value);
    }

    static <T> Optional<T> some(T value) {
        return new Some<>(value);
    }

    @SuppressWarnings("unchecked")
    static <T> Optional<T> none() {
        return (Optional<T>) None.NONE;
    }

    class None<T> implements Optional<T> {

        public static final None<?> NONE = new None<>();

        @Override
        public <R> Optional<R> map(Function<? super T, ? extends R> function) {
            return (Optional<R>) NONE;
        }

        @Override
        public <R> Optional<R> flatMap(Function<? super T, Optional<? extends R>> function) {
            return (Optional<R>) NONE;
        }

        @Override
        public void ifPresent(Consumer<? super T> consumer) {
        }

        @Override
        public T get() {
            throw new NoSuchElementException();
        }

        @Override
        public boolean isPresent() {
            return false;
        }
    }

    class Some<T> implements Optional<T> {

        private final T value;

        public Some(T value) {
            Objects.requireNonNull(value);
            this.value = value;
        }

        @Override
        public <R> Optional<R> map(Function<? super T, ? extends R> function) {
            return new Some<>(function.apply(value));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> Optional<R> flatMap(Function<? super T, Optional<? extends R>> function) {
            return (Optional<R>) function.apply(value);
        }

        @Override
        public void ifPresent(Consumer<? super T> consumer) {
            consumer.accept(value);
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public boolean isPresent() {
            return true;
        }
    }
}
