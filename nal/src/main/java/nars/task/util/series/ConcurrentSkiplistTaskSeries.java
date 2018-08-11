package nars.task.util.series;

import com.google.common.collect.Iterators;
import jcog.sort.TopN;
import nars.NAR;
import nars.Param;
import nars.Task;
import nars.table.temporal.TemporalBeliefTable;
import nars.term.Term;
import nars.truth.Truth;
import nars.truth.dynamic.DynTruth;

import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

abstract public class ConcurrentSkiplistTaskSeries<T extends Task> implements TaskSeries<T> {

    /**
     * tasks are indexed by their midpoint. since the series
     * is guaranteed to be ordered and non-overlapping, two entries
     * should not have the same mid-point even if they overlap
     * slightly.
     */
    final NavigableMap<Long, T> at;
    private final int cap;

    public ConcurrentSkiplistTaskSeries(NavigableMap<Long, T> at, int cap) {
        this.at = at;
        this.cap = cap;
    }

    @Override
    public long start() {
        return at.firstEntry().getValue().start();
    }

    @Override
    public long end() {
        return at.lastEntry().getValue().end();
    }

    @Override
    public int size() {
        return at.size();
    }

    @Override
    public Stream<T> stream() {
        return at.values().stream();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        at.values().forEach(action);
    }


    @Override
    public void clear() {
        at.clear();
    }

    @Override
    public boolean whileEach(final long minT, final long maxT, boolean exactRange, Predicate<? super T> x) {


        Long low = at.floorKey(minT);
        if (low == null)
            low = minT;

        if (!exactRange) {
            Long lower = at.lowerKey(low);
            if (lower != null)
                low = lower;
        }

        Long high = at.ceilingKey(maxT);
        if (high == null)
            high = maxT;

        if (!exactRange) {
            Long higher = at.higherKey(high);
            if (higher != null)
                high = higher;
        }


        Iterator<T> ii;
        if (low != high) {
            ii = at.subMap(low, true, high, true).values().iterator();
        } else {
            T the = at.get(low);
            if (the == null)
                return true; //nothing
            ii = Iterators.singletonIterator(the);
        }

        while (ii.hasNext()) {
            T xx = ii.next();
            if (exactRange && !xx.intersects(minT, maxT))
                continue;
            if (!x.test(xx))
                return false;
        }

        return true;
    }

    @Override
    public boolean isEmpty(long start, long end) {
        try {
            return at.subMap(start, true, end, true).isEmpty();
        } catch (NoSuchElementException e) {
            return true;
        }
    }

    @Override
    public T add(Term term, byte punc, long nextStart, long nextEnd, Truth next, int dur, NAR nar) {

        T nextT = null;


        Map.Entry<Long, T> lastEntry = at.lastEntry();
        boolean removePrev = false;
        long lastStamp[] = null;
        long lastEntryKey;
        if (next != null && lastEntry != null) {
            T last = lastEntry.getValue();
            lastEntryKey = lastEntry.getKey();
            long lastStart = last.start();
            if (lastStart > nextStart)
                return null;

            long lastEnd = last.end();
            if (nextStart - lastEnd <= dur) {

                //start new task segment
                nextStart = lastEnd + 1; //stretch behind to meet previous since it is within range

                if ((nextStart - lastStart) / dur <= maxTaskDurs()) {
                    Truth lastEnds = last.truth(lastEnd, dur);
                    if (lastEnds.equals(next)) {

                        nextStart = lastStart; //start from even further, the last task's start
                        lastStamp = last.stamp();
                        removePrev = true;
                    }
                }
            }

        } else {
            lastEntryKey = Long.MIN_VALUE;
        }

        //assert(nextStart <= nextEnd);

        if (next != null) {
            nextT = newTask(term, punc, nextStart, nextEnd, next, nar, removePrev, lastStamp);
        }

        synchronized (this) {
            if (removePrev)
                at.remove(lastEntryKey);

            if (nextT != null)
                at.put((nextStart + nextEnd) / 2L, nextT);

            compress();
        }


        return nextT;

    }

    /**
     * maximum durations a steady signal can grow for
     */
    private long maxTaskDurs() {
        return Param.SIGNAL_LATCH_DUR_MAX;
    }

    abstract protected T newTask(Term term, byte punc, long nextStart, long nextEnd, Truth next, NAR nar, boolean removePrev, long[] lastStamp);

    void compress() {
        int toRemove = ( at.size()) - cap;
        while (toRemove-- > 0) {
            at.remove(at.firstKey()).delete();
        }
    }


    final static int MAX_TASKS_TRUTHPOLATED = Param.STAMP_CAPACITY - 1;

    @Override
    public DynTruth truth(long start, long end, long dur, NAR timeAware) {

        int size = size();
        if (size == 0)
            return null;

        DynTruth d = new DynTruth(Math.min(size, MAX_TASKS_TRUTHPOLATED));


        forEach(start, end, MAX_TASKS_TRUTHPOLATED, d::add);


        return d;
    }

    @Override
    public int forEach(long start, long end, int limit, Consumer<T> target) {
        TopN<Task> inner = new TopN<>(new Task[Math.min(size(), limit)],
                t -> TemporalBeliefTable.value(t, start, end, 1) //TODO this may be better as a double value comparison, long -> float could be lossy
        );

        forEach(start, end, false, inner::add);
//

////        if (inners > 0) {
////            if (inners <= limit) {
////                for (T x : inner) {
////                    target.accept(x);
////                    n++;
////                }
////            } else {
//                long mid = (start+end)/2L;
//                inner.sortThisByLong(x -> x.midTimeTo(mid));
        int l = inner.size();
        if (l > 0) {
            Task[] ii = inner.items;
            int i;
            for (i = 0; i < l; i++) {
                target.accept((T) ii[i]);
            }
            return i;
        } else {
            return 0;
        }
    }
}