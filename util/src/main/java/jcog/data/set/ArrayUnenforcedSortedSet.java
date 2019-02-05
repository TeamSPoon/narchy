package jcog.data.set;

import jcog.TODO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.stream.Stream;

/** use with caution */
abstract public class ArrayUnenforcedSortedSet<X> extends ArrayUnenforcedSet<X> implements SortedSet<X> {

    public static final SortedSet empty = new ArrayUnenforcedSortedSet<Object>() {

        @Override
        public Stream<Object> stream() {
            return Stream.empty();
        }

        @Override
        public Object first() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object last() {
            throw new UnsupportedOperationException();
        }
    };

    private ArrayUnenforcedSortedSet(X... xx) {
        super((X[])xx);
    }

    /** assumes u is already sorted and deduplicated */
    public static <X> SortedSet<X> the(X[] u) {
        switch (u.length) {
            case 0: return empty;
            case 1: return the(u[0]);
            case 2: return new Two(u[0], u[1]);
            default:
                return new ArrayArrayUnenforcedSortedSet<>(u);
        }
    }

    @Override
    public boolean add(X newItem) {
        throw new TODO();
    }


    public static <X> SortedSet<X> the(X x) {
        return new One(x);
    }

    public static <X extends Comparable> SortedSet<X> the(X x, X y) {
        int c = x.compareTo(y);
        switch (c) {
            case 0:
                return new One(x);
            case 1:
                return new Two(y, x);
            default:
                return new Two(x, y);
        }
    }

    @Nullable
    @Override
    public Comparator<? super X> comparator() {
        return null;
    }

    @NotNull
    @Override
    public SortedSet<X> subSet(X x, X e1) {
        throw new TODO();
    }

    @NotNull
    @Override
    public SortedSet<X> headSet(X x) {
        throw new TODO();
    }

    @NotNull
    @Override
    public SortedSet<X> tailSet(X x) {
        throw new TODO();
    }

    private static class One<X> extends jcog.data.set.ArrayUnenforcedSortedSet<X> {

        private One(X x) {
            super(x);
        }

        @Override
        public Stream<X> stream() {
            return Stream.of(get(0));
        }

        @Override
        public X first() {
            return getFirst();
        }

        @Override
        public X last() {
            return getLast();
        }
    }

    private static class Two<X> extends jcog.data.set.ArrayUnenforcedSortedSet<X> {

        private Two(X x, X y) {
            super(x, y);
        }

        @Override
        public Stream<X> stream() {
            return Stream.of(first(), last());
        }

        @Override
        public X first() {
            return getFirst();
        }

        @Override
        public X last() {
            return getLast();
        }

    }


    private static class ArrayArrayUnenforcedSortedSet<X> extends ArrayUnenforcedSortedSet<X> {
        private final X[] u;

        public ArrayArrayUnenforcedSortedSet(X[] u) {
            super(u);
            this.u = u;
        }

        @Override
        public X first() {
            return u[0];
        }

        @Override
        public X last() {
            return u[u.length-1];
        }
    }
}
