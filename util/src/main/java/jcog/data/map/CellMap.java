package jcog.data.map;

import jcog.data.pool.MetalPool;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.*;

/**
 * concurrent map wrapping key,value pairs in cell instances
 * that are recycled in an internal pool.
 *
 * this can be useful for managing maps with setAt-like semantics
 *  --addAt
 *  --setAt
 *  --remove
 *
 * uses ConcurrentFastIteratingHashMap which maintains an additional
 * list of items that is eventually updated with any map operations.
 *
 * bulk operations should defer invalidation instead of invalidating
 * on each item.
 */
public class CellMap<K, V> {

    public final ConcurrentFastIteratingHashMap<K, CacheCell<K, V>> map =
            new ConcurrentFastIteratingHashMap<>(new CacheCell[0]);

    public final MetalPool<CacheCell<K, V>> cellPool = new MetalPool<CacheCell<K, V>>() {
        @Override
        public CacheCell<K, V> create() {
            return newCell();
        }


    };

    public CellMap() {

    }

    protected CacheCell<K,V> newCell() {
        return new CacheCell<>();
    }

    public final void forEachCell(Consumer<? super CacheCell<K,V>> each) {
        map.forEachValue(each);
    }

    public final void forEachValue(Consumer<? super V> each) {
        forEachCell(e -> {
            V s = e.value;
            if (s != null)
                each.accept(s);
        });
    }

    public void forEachKeyValue(BiConsumer<K,? super V> each) {
        forEachCell(e -> {
            V s = e.value;
            if (s != null)
                each.accept(e.key, s);
        });
    }

    @Nullable
    public CellMap.CacheCell<K, V> update(K key, CellMap.CacheCell<K, V> entry, boolean keep) {
        if (keep) {
            //added or continues
            return entry;
        } else {
            remove(key);
            return null;
        }
    }



    public boolean whileEach(Predicate<CacheCell<K,V>> o) {
        return map.whileEachValue(o::test);
    }

    public boolean whileEachReverse(Predicate<CacheCell<K,V>> o) {
        return map.whileEachValueReverse(o::test);
    }

    public void removeAll(Iterable<K> x) {
        final boolean[] changed = {false};
        x.forEach(xx -> changed[0] |= removeSilently(xx));
        if (changed[0])
            invalidated();
    }

    @Nullable
    public V getValue(Object x) {
        CacheCell<K, V> y = map.get(x);
        return y != null ? y.value : null;
    }

    public CacheCell<K, V> compute(K key, BiFunction<K, V, V> builder) {
        CacheCell<K, V> entry = map.computeIfAbsent(key, k -> cellPool.get());
        entry.update(key, builder);
        return update(key, entry, entry.key!=null);
    }

    public CacheCell<K, V> compute(K key, Function<V, V> builder) {
        return compute(key, (z, w)->builder.apply(w));
    }

    public CacheCell<K, V> computeIfAbsent(K key, Function<K, V> builder) {
        return compute(key, (z, w) -> { if (w==null) return builder.apply(z); else return w; } );
    }



    public CacheCell<K, V> remove(Object key) {
        CacheCell<K, V> entry = map.remove(key);
        if (entry != null) {
            removed(entry);
            invalidated();
            return entry;
        }
        return null;
    }

    /** removes without immediately signaling invalidation, for use in batch updates */
    public boolean removeSilently(K key) {
        CacheCell<K, V> entry = map.remove(key);
        if (entry != null) {
            removed(entry);
            return true;
        }
        return false;
    }

    protected final void removed(CacheCell<K, V> entry) {
        unmaterialize(entry);
        entry.clear();
        cellPool.put(entry);
    }

    protected void unmaterialize(CacheCell<K, V> entry) {

    }

    protected void invalidated() {
        map.invalidate();
    }

    public void getValues(Collection<V> l) {
        forEachValue(l::add);
    }

    public int size() {
        return this.map.size();
    }

    public Collection<CacheCell<K,V>> cells() {
        return map.values();
    }

    public void clear() {
        map.removeIf(e -> {
            removed(e);
            return true;
        });
    }

    @Nullable public V get(Object from) {
        CacheCell<K, V> v = map.get(from);
        return v != null ? v.value : null;
    }

    /** find first corresponding key to the provided value */
    @Nullable public K firstByValue(Predicate v) {
        for (CacheCell<K,V> c : map.valueArray()) {
            if (v.test(c.value)) {
                return c.key;
            }
        }
        return null;
    }


    /**
     * (key, value, surface) triple
     */
    public static class CacheCell<K, V> {

        public transient volatile K key;
        public transient volatile V value;

        protected CacheCell() {

        }

        protected void set(V next) {
            this.value = next;
        }

        public void clear() {
            key = null;
            value = null;
        }


        public final void update(K nextKey, Function<V, V> update) {
            update(nextKey, (k, v) -> update.apply(v));
        }


        public void update(K nextKey, BiFunction<K, V, V> update) {

            final V prev = value;

            V next = update.apply(nextKey, prev);
            if (next == prev) {
                key = next == null ? null : nextKey;
            } else {

                boolean create = false, delete = false;

                if (prev != null) {
                    if (next == null) {
                        delete = true;
                    } else {
                        create = delete = true;
                    }
                } else {
                    if (next == null) {
                        delete = true;
                    } else {
                        create = true;
                    }
                }

                if (delete) {
                    key = null;
                }

                if (create) {
                    key = nextKey;
                    set(next);
                }

            }
        }

    }
}
