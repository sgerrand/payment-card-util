package com.sgerrand.paymentcardutil.ipm;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An iterator over something that can only say whether there is more by reading it.
 *
 * <p>Both file readers here work that way: a VBS file ends when a record says its length is zero,
 * and a parameter table ends when the rows stop belonging to it. Neither can answer {@code hasNext}
 * without reading, so the read is done once and held until {@code next} asks for it.
 *
 * @param <T> what one read hands back
 */
abstract class LookAheadIterator<T> implements Iterator<T> {

    private T pending;
    private boolean exhausted;

    /**
     * @return the next item, or {@code null} when there are no more
     */
    abstract T readNext();

    /** What to say when someone calls {@code next} past the end. */
    abstract String endMessage();

    @Override
    public final boolean hasNext() {
        if (pending != null) {
            return true;
        }
        if (exhausted) {
            return false;
        }
        pending = readNext();
        if (pending == null) {
            exhausted = true;
            return false;
        }
        return true;
    }

    @Override
    public final T next() {
        if (!hasNext()) {
            throw new NoSuchElementException(endMessage());
        }
        T item = pending;
        pending = null;
        return item;
    }
}
