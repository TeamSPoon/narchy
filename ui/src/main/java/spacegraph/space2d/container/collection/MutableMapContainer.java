package spacegraph.space2d.container.collection;

import jcog.data.map.CellMap;
import org.jetbrains.annotations.Nullable;
import spacegraph.space2d.Surface;

import java.util.Collection;
import java.util.Objects;
import java.util.function.*;

public class MutableMapContainer<K, V> extends AbstractMutableContainer {

    /**
     * "cells" of the container; maintain the mapping between the indexed keys and their "materialized" representations or projections
     */
    protected final CellMap<K, V> cells = new CellMap<K,V>() {
        @Override
        protected CacheCell<K, V> newCell() {
            return new SurfaceCacheCell<>();
        }

        @Override
        protected final void unmaterialize(CacheCell<K, V> entry) {
            MutableMapContainer.this.unmaterialize(entry.value);
        }
        //        @Override
//        protected void added(CacheCell<K, V> entry) {
//            if (parent == null)
//                return;
//
//            Surface es = ((SurfaceCacheCell) entry).surface;
//            //if (es != null && es.parent == null)
//            es.start(MutableMapContainer.this);
//        }


//        @Override
//        protected void invalidated() {
//            super.invalidated();
//        }
    };


    protected void unmaterialize(V v) {

    }


    @Override
    public void forEach(Consumer<Surface> each) {
        cells.forEachCell(e -> {
//            if (e == null)
//                throw new NullPointerException();

            Surface s = ((SurfaceCacheCell) e).surface;
            if ((s == null) && (e.value instanceof Surface))
                s = (Surface)e.value; //HACK
            if (s != null)
                each.accept(s);
        });
    }


//    public void forEachVisible(Consumer<Surface> each) {
//        forEach(x -> {
//            if (x.visible())
//                each.accept(x);
//        });
//    }
//
//    public void forEachKeySurface(BiConsumer<? super K, Surface> each) {
//        cellMap.forEachCell((cell) -> {
//            Surface ss = ((SurfaceCacheCell) cell).surface;
//            if (ss != null)
//                each.accept(cell.key, ss);
//        });
//    }

    public void forEachValue(Consumer<? super V> each) {
        cells.forEachValue(each);
    }


    @Override
    protected void doLayout(int dtMS) {

    }

    @Override
    public int childrenCount() {
        return Math.max(1, cells.size());
    }

    @Override
    protected void clear() {
        cells.clear();
    }

    protected void removeAll(Iterable<K> x) {
        cells.removeAll(x);
    }

    public Collection<K> keySet() {
        return cells.map.keySet();
    }

    @Nullable
    public V getValue(K x) {
        return cells.getValue(x);
    }

    public CellMap.CacheCell<K, V> compute(K key, Function<V, V> builder) {
        CellMap.CacheCell<K, V> y = cells.compute(key, builder);
        V v = y.value;
        if(v instanceof Surface)
            ((SurfaceCacheCell)y).surface = (Surface) v;
        return y;
    }

    public CellMap.CacheCell<K, V> computeIfAbsent(K key, Function<K, V> builder) {
        CellMap.CacheCell<K, V> y = cells.computeIfAbsent(key, builder);
        V v = y.value;
        if(v instanceof Surface)
            ((SurfaceCacheCell)y).surface = (Surface) v;
        return y;
    }

    protected CellMap.CacheCell<K, V> put(K key, V nextValue, BiFunction<K, V, Surface> renderer) {

        CellMap.CacheCell<K, V> entry = cells.map.computeIfAbsent(key, k -> cells.cellPool.get());

        ((SurfaceCacheCell<K,V>) entry).update(key, nextValue, renderer, (BiConsumer<V,Surface>)this::hide);

        return cells.update(key, entry, entry.key != null);

    }


    /** default behavior is to call Surface.stop() but caching can be implemented here */
    protected void hide(V key, Surface s) {
//        if (cache == null) {
            s.stop();
//        } else {
//            s.hide();
//        }
    }

    public V remove(Object key) {
        CellMap.CacheCell<K, V> c = cells.remove(key);
        return c!=null ? c.value : null;
    }

    @Override
    public boolean detachChild(Surface s) {
        K k = cells.firstByValue(x -> s == x);
        return k!=null && remove(k)!=null;
    }

    protected boolean removeSilently(K key) {
        return cells.removeSilently(key);
    }


    public void getValues(Collection<V> l) {
        cells.getValues(l);
    }

    @Override
    public boolean whileEach(Predicate<Surface> o) {
        return cells.whileEach(e -> {
            Surface s = ((SurfaceCacheCell) e).surface;
            return s == null || o.test(s);
        });
    }

    @Override
    public boolean whileEachReverse(Predicate<Surface> o) {
        return cells.whileEachReverse(e -> {
            Surface s = ((SurfaceCacheCell) e).surface;
            return s == null || o.test(s);
        });
    }


    public static class SurfaceCacheCell<K, V> extends CellMap.CacheCell<K, V> {

        public transient volatile Surface surface = null;

        @Override
        public void clear() {
            super.clear();

            Surface s = surface;
            surface = null;

            if (s != null) {
                s.stop();
            }
        }


        /** returns previous surface, or null if unchanged */
        private Surface setSurface(Surface next) {
            ///assert (surface == null);
            Surface prev = this.surface;
            if (next != prev) {
                this.surface = next;
                return prev;
            }
            else
                return null;
        }

        /**
         * return true to keep or false to remove from the map
         */
        void update(K nextKey, V nextValue, BiFunction<K, V, Surface> renderer, BiConsumer<V, Surface> hider) {

            Surface removed;
            if (nextValue == null) {
                this.key = null;
                set(null);
                removed = setSurface(null);
            } else {

                if (!Objects.equals(this.value, nextValue) || surface == null) {
                    Surface nextSurface = renderer.apply(nextKey, nextValue);
                    set(nextValue);
                    removed = setSurface(nextSurface);
                } else {
                    removed = null;
                }

                this.key = nextKey; //ready
            }

            if (removed!=null) {
                hider.accept(nextValue, removed);
            }

        }
    }
}
