package org.openhab.binding.homeconnectdirect.internal.utils;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class LimitedSizeList<E> {
    private final LinkedList<E> list = new LinkedList<>();
    private final int capacity;

    public LimitedSizeList(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0!");
        }
        this.capacity = capacity;
    }

    public synchronized void add(E element) {
        if (list.size() >= capacity) {
            list.removeFirst();
        }
        list.addLast(element);
    }

    public synchronized List<E> getAllElements() {
        return Collections.unmodifiableList(new LinkedList<>(list));
    }

    public synchronized int size() {
        return list.size();
    }

    public synchronized boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public synchronized String toString() {
        return list.toString();
    }
}
