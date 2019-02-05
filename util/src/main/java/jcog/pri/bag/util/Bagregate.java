package jcog.pri.bag.util;

import com.google.common.collect.Iterables;
import jcog.data.NumberX;
import jcog.math.FloatRange;
import jcog.pri.PLink;
import jcog.pri.PriReference;
import jcog.pri.bag.Bag;
import jcog.pri.bag.impl.PLinkArrayBag;
import jcog.pri.op.PriMerge;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * a bag which wraps another bag, accepts its value as input but at a throttled rate
 * resulting in containing effectively the integrated / moving average values of the input bag
 * TODO make a PLink version of ArrayBag since quality is not used here
 */
public class Bagregate<X> implements Iterable<PriReference<X>> {

    public final Bag<?, PriReference<X>> bag;
    private final Iterable<? extends PriReference<X>> src;
    private final NumberX scale;
//    private final AtomicBoolean busy = new AtomicBoolean();


    public Bagregate(Stream<PriReference<X>> src, int capacity, float scale) {
        this(src::iterator, capacity, scale);
    }

    public Bagregate(Iterable<? extends PriReference<X>> src, int capacity, float scale) {
        this.bag = new PLinkArrayBag<X>(PriMerge.max /*PriMerge.replace*/, capacity) {
            @Override
            public void onRemove(PriReference<X> value) {
                Bagregate.this.onRemove(value);
            }
        };
        this.src = src;
        this.scale = new FloatRange(scale, 0f, 1f);
    }

    protected void onRemove(PriReference<X> value) {

    }

    public boolean commit() {
        if (src==null /*|| !busy.compareAndSet(false, true)*/)
            return false;

//        try {



        float scale = this.scale.floatValue();

        bag.commit();

        src.forEach(x -> {
            X xx = x.get();
            if (include(xx)) {
                float pri = x.pri();
                if (pri==pri)
                    bag.putAsync(new PLink<>(xx, pri*scale ));
            }
        });



//        } finally {
//            busy.setAt(false);
//        }
        return true;
    }

    /**
     * can be overridden to filter entry
     */
    private boolean include(X x) {
        return true;
    }

    @Override
    public final Iterator<PriReference<X>> iterator() {
            return bag.iterator();
    }

    @Override
    public final void forEach(Consumer<? super PriReference<X>> action) {
            bag.forEach(action);
    }

    public void clear() {
        bag.clear();
    }


    /** compose */
    public Iterable<X> iterable() {
        return Iterables.transform(Iterables.filter(bag, Objects::nonNull) /* HACK */, Supplier::get);
    }

    /** compose */
    public <Y> Iterable<Y> iterable(Function<X, Y> f) {
        return Iterables.transform(Iterables.filter(bag, Objects::nonNull) /* HACK */, (b)->f.apply(b.get()));
    }


    




}
