package jcog.memoize;

import jcog.Texts;
import jcog.bag.impl.HijackBag;
import jcog.bag.impl.hijack.PriorityHijackBag;
import jcog.data.MwCounter;
import jcog.pri.Pri;
import jcog.pri.Priority;
import jcog.random.XorShift128PlusRandom;
import org.eclipse.collections.api.block.procedure.primitive.ObjectLongProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

import static jcog.Texts.n4;

/**
 * TODO add an instrumentation wrapper to collect statistics
 * about cache efficiency and also processing time of the calculations
 */
public class HijackMemoize<K, V> extends PriorityHijackBag<K, HijackMemoize.HalfWeakPair<K, V>> implements Memoize<K,V> {

    private final Random rng = new XorShift128PlusRandom();

    public static class HalfWeakPair<K,V> extends /*WeakReference*/SoftReference<V> implements Priority {
        public final K key;
        private final int hash;
        private float pri;

        public HalfWeakPair(@NotNull K key, @NotNull V value) {
            super(value);
            this.key = key;
            this.hash = key.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            HalfWeakPair h = (HalfWeakPair)obj;
            return hash == h.hash && key.equals( h.key );
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public float setPri(float p) {
            if (get()!=null)
                return this.pri = p;
            else
                return Float.NaN;
        }

        @Nullable
        @Override
        public V get() {
            V v = super.get();
            if (v == null)
                this.pri = Float.NaN;
            return v;
        }

        @Override
        public @Nullable Priority clonePri() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "$" + n4(pri) + " " +  key;
        }

        @Override
        public boolean delete() {
            clear();
            this.pri = Float.NaN;
            return true;
        }

        @Override
        public float pri() {
            get();
            return pri;
        }

        @Override
        public boolean isDeleted() {
            float p = pri();
            return p!=p;
        }


    }

    float CACHE_HIT_BOOST;
    float CACHE_DENY_DAMAGE; //damage taken by a cell in rejecting an attempted hijack

    final Function<K, V> func;

    public final MwCounter
            hit = new MwCounter(),  //existing item retrieved
            miss = new MwCounter(),  //a new item inserted that has not existed
            reject = new MwCounter(), //item prevented from insertion by existing items
            evict = new MwCounter(); //removal of existing item on insertion of new item


    //hit + miss + reject = total insertions


    public HijackMemoize(@NotNull Function<K, V> f, int initialCapacity, int reprobes) {
        super(reprobes);
        setCapacity(initialCapacity);
        this.func = f;
    }

    public float statReset(ObjectLongProcedure<String> eachStat) {
        //eachStat.accept("S" /* size */, size() );
        long H, M, R, E;
        eachStat.accept("H" /* hit */, H = hit.getThenZero());
        eachStat.accept("M" /* miss */, M = miss.getThenZero());
        eachStat.accept("R" /* reject */, R = reject.getThenZero());
        eachStat.accept("E" /* evict */, E = evict.getThenZero());
        return (H / ((float) (H + M + R + E)));
    }

    /**
     * estimates the value of computing the input.
     * easier items will introduce lower priority, allowing
     * harder items to sustain longer
     */
    public float value(@NotNull K k) {
        return 0.5f;
        //return reprobes * 2 * CACHE_HIT_BOOST;
    }

    @Override
    public void setCapacity(int i) {
        super.setCapacity(i);

        float boost = i > 0 ?
                //0.02f
                (float) (1f / Math.sqrt(capacity()))
                : 0;

        //note: cut should probably be some factor less than 1/reprobes
        // for example, 1/(N*reprobes)
        // to ammortize additional attempts where the cut was not necessary
        //TODO make this a momentum parameter
        float cut = boost / (reprobes*2);

        assert(cut > Pri.EPSILON);

        set(boost, cut);

        //reprobes / (float)Math.sqrt(i) : 0;

        //return true;

        //return false;
    }

    public void set(float boost, float cut) {
        this.CACHE_HIT_BOOST = boost;
        this.CACHE_DENY_DAMAGE = cut;
    }

    @NotNull
    @Override
    public HijackBag<K, HalfWeakPair<K, V>> commit(@Nullable Consumer<HalfWeakPair<K, V>> update) {
        return this;
    }

    @Nullable
    public V getIfPresent(@NotNull Object k) {
        HalfWeakPair<K, V> exists = get(k);
        if (exists != null) {
            V e = exists.get();
            if (e!=null) {
                exists.priAdd(CACHE_HIT_BOOST);
                hit.inc();
                return e;
            }
        }
        return null;
    }

    @Nullable
    public V removeIfPresent(@NotNull K k) {
        @Nullable HalfWeakPair<K, V> exists = remove(k);
        if (exists != null) {
            return exists.get();
        }
        return null;
    }

    @Override
    @Nullable
    public V apply(@NotNull K k) {
        V v = getIfPresent(k);
        if (v == null) {
            v = func.apply(k);
            HalfWeakPair h = new HalfWeakPair(k, v);
                    //new PLink<>(key, value)
                    //new WeakPLink<>(key, value)
            h.setPri(0.5f);
            if (put(h) != null) {
                miss.inc();
            } else {
                reject.inc();
            }
        }
        return v;
    }



    @Override
    protected boolean replace(float incoming, HalfWeakPair<K, V> existing) {
        if (!super.replace(incoming, existing)) {
            existing.priSub(CACHE_DENY_DAMAGE);
            return false;
        }
        return true;
    }

    @Override
    protected Random random() {
        return rng;
    }

    @NotNull
    @Override
    public K key(HalfWeakPair<K, V> value) {
        return value.key;
    }


    @Override
    protected Consumer<HalfWeakPair<K, V>> forget(float rate) {
        return null;
    }

    @Override
    public void onRemoved(@NotNull HijackMemoize.HalfWeakPair<K, V> value) {
        value.delete();
        evict.inc();
    }


    /**
     * clears the statistics
     */
    public String summary() {
        StringBuilder sb = new StringBuilder(64);
        sb.append("N=").append(size()).append(' ');
        float rate = statReset((k, v) -> {
            sb.append(k).append('=').append(v).append(' ');
        });
        sb.setLength(sb.length() - 1); //remove last ' '
        sb.insert(0, Texts.n2(100f * rate) + "% ");
        return sb.toString();
    }


}