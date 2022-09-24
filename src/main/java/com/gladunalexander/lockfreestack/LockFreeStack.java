package com.gladunalexander.lockfreestack;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

class LockFreeStack<T> {

    private AtomicReference<Node<T>> head = new AtomicReference<>();
    private AtomicLong compareAndSetCount = new AtomicLong();

    public void push(T value) {
        Node<T> prevHead;
        Node<T> newHead = new Node<>(value);
        do {
            prevHead = head.get();
            newHead.setNext(prevHead);
            compareAndSetCount.incrementAndGet();
        } while (!head.compareAndSet(prevHead, newHead));
    }

    public T pop() {
        Node<T> toPop;
        do {
            toPop = head.get();
            if (toPop == null) return null;
            compareAndSetCount.incrementAndGet();
        } while (!head.compareAndSet(toPop, toPop.getNext()));
        return toPop.getValue();
    }

    public long getCompareAndSetCount() {
        return compareAndSetCount.get();
    }
}
