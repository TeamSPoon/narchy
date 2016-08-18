package nars.concept.table;

import nars.NAR;
import nars.Param;
import nars.Task;
import nars.concept.Concept;
import nars.nal.Stamp;
import nars.task.Revision;
import nars.task.TruthPolation;
import nars.truth.Truth;
import nars.util.Util;
import nars.util.data.list.FasterList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static nars.concept.table.BeliefTable.rankTemporalByConfidence;
import static nars.nal.Tense.ETERNAL;

/**
 * stores the items unsorted; revection manages their ranking and removal
 */
public class MicrosphereTemporalBeliefTable extends FasterList<Task> implements TemporalBeliefTable {

    static final int MAX_TRUTHPOLATION_SIZE = 64;
    static final ThreadLocal<TruthPolation> truthpolations = ThreadLocal.withInitial(() -> {
        return new TruthPolation(MAX_TRUTHPOLATION_SIZE);
    });

    private int capacity;

    public MicrosphereTemporalBeliefTable(int initialCapacity) {
        super();
        this.capacity = initialCapacity;
    }


    public void capacity(int newCapacity, long now, @NotNull List<Task> removed) {
        this.capacity = newCapacity;

        synchronized (this) {
            int s = removeAlreadyDeleted(removed);
            //compress(displ, now);
            if (s > newCapacity) {
                do {
                    remove(weakest(now), removed);
                } while (this.size() > newCapacity);
            }
        }

    }

    @Override
    public final int capacity() {
        return capacity;
    }


     /** according to a balance of temporal proximity and confidence */
     public static float rank(@NotNull Task t, long when, long now) {
        //return rankTemporalByConfidenceAndOriginality(t, when, now, -1);
        return rankTemporalByConfidence(t, when, now, -1);
    }

    @Nullable
    @Override
    public final boolean add(@NotNull Task input, EternalTable eternal, @NotNull List<Task> displ, Concept concept, @NotNull NAR nar) {

        int cap = capacity();
        if (cap == 0)
            return false;

        //the result of compression is processed separately
        Task next;
        synchronized (this) {
            next = compress(input, nar.time(), eternal, displ, concept);
        }

        if (next!=null && !isFull()) {

            synchronized (this) {
                add(input);
            }

            if (next != input)
                nar.inputLater(next);

            return true;
        }


        //not compressible with respect to this input, so reject the input
        // HACK DOES THIS HAPPEN and WHY, IS IT DANGEROUS
        //if (Global.DEBUG)
        //throw new RuntimeException(this + " compression failed");
        return false;

    }

    @Override
    public final boolean isFull() {
        return size() == capacity();
    }

//    @Override
//    public void minTime(long minT) {
//        this.min = minT;
//    }
//
//    @Override
//    public void maxTime(long maxT) {
//        this.max = maxT;
//    }


    @Override
    public final void range(long[] t) {
        for (Task x : this.items) {
            if (x != null) {
                long o = x.occurrence();
                if (o < t[0]) t[0] = o;
                if (o > t[1]) t[1] = o;
            }
        }
    }


    @Override
    public boolean remove(Object object) {
        return super.remove(object);
    }

    private final boolean remove(@NotNull Task removed, @NotNull List<Task> displ) {
        int i = indexOf(removed);
        if (i == -1)
            return false;

        Task x = remove(i, displ);
        if (x != removed) {
            throw new RuntimeException("equal but different instances: " + removed);
        }
        return x!=null;
    }


//    @Override
//    public final boolean remove(Object object) {
//        if (super.remove(object)) {
//            invalidRangeIfLimit((Task)object);
//            return true;
//        }
//        return false;
//    }


    @Nullable
    private final Task remove(int index, @NotNull List<Task> displ) {
        @Nullable Task t = this.remove(index);
        if (t != null) {
            displ.add(t);
        }
        return t;
    }

    @Nullable
    public Task weakest(long now) {
        return weakest(now, null, Float.POSITIVE_INFINITY);
    }


    @Nullable
    public Task weakest(long now, @Nullable Task toMergeWith, float minRank) {
        Task weakest = null;
        float weakestRank = minRank;
        int n = size();

        long[] mergeEvidence = toMergeWith != null ? toMergeWith.evidence() : null;
        for (int i = 0; i < n; i++) {

            Task ii = get(i);
            if (toMergeWith != null &&
                    ((!Param.REVECTION_ALLOW_MERGING_OVERLAPPING_EVIDENCE &&
                            (/*Stamp.isCyclic(iiev) || */Stamp.overlapping(mergeEvidence, ii.evidence()))
                    )))
                continue;

            //consider ii for being the weakest ranked task to remove
            float r = rank(ii, now, now);
            //(toMergeWith!=null ? (1f / (1f + Math.abs(ii.freq()-toMergeWith.freq()))) : 1f); //prefer close freq match
            if (r < weakestRank) {
                weakestRank = r;
                weakest = ii;
            }

        }

        return weakest;
    }


//    @Nullable
//    protected Task compress(@NotNull List<Task> displ, long now) {
//        return compress(null, now, null, displ, null);
//    }

    /**
     * frees one slot by removing 2 and projecting a new belief to their midpoint. returns the merged task
     */
    @Nullable
    protected Task compress(@Nullable Task input, long now, @Nullable EternalTable eternal, @NotNull List<Task> displ, @Nullable Concept concept) {

        int cap = capacity();
        if (size() < cap || removeAlreadyDeleted(displ) < cap) {
            return input; //no need for compression
        }


        float inputRank = input != null ? rank(input, now, now) : Float.POSITIVE_INFINITY;

        Task a = weakest(now, null, inputRank);
        if (a == null)
            return null;

        if (!remove(a, displ)) {
            return null; //dont continue if there was a problem removing a (like it got removed already by a different thread or something)
        }

        Task b = weakest(now, a, Float.POSITIVE_INFINITY);

        if (b != null && remove(b, displ)) {
            return merge(a, b, now, concept, eternal);
        } else {
            return input;
        }

    }

    /**
     * t is the target time of the new merged task
     */
    @Nullable
    private Task merge(@NotNull Task a, @NotNull Task b, long now, Concept concept, @Nullable EternalTable eternal) {
        double ac = a.conf();
        double bc = b.conf();
        long mid = (long) Math.round(Util.lerp((double) a.occurrence(), (double) b.occurrence(), ac / (ac + bc)));
        //long mid = (long)Math.round((a.occurrence() * ac + b.occurrence() * bc) / (ac + bc));

        //more evidence overlap indicates redundant information, so reduce the confWeight (measure of evidence) by this amount
        //TODO weight the contributed overlap amount by the relative confidence provided by each task
        float overlap = Stamp.overlapFraction(a.evidence(), b.evidence());

//        /**
//         * compute an integration of the area under the trapezoid formed by
//         * computing the projected truth at the 'a' and 'b' time points
//         * and mixing them by their relative confidence.
//         * this is to represent a loss of confidence due to diffusion of
//         * truth across a segment of time spanned by these two tasks as
//         * they are merged into one.
//         */
//        float diffuseCost;
//        /*if (minTime()==ETERNAL) {
//            throw new RuntimeException(); //shouldnt happen
//        } else {*/
//        long aocc = a.occurrence();
//        long bocc = b.occurrence();
//        float aProj = projection(mid, now, aocc);
//        float bProj = projection(mid, now, bocc);
//
//        //TODO lerp blend these values ? avg? min?
//        diffuseCost =
//                //aveAri(aProj + bProj)/2f;
//                //Math.min(aProj, bProj);
//                and(aProj, bProj);
//
////        float relMin = projection(minTime(), mid, now);
////        float relMax = projection(maxTime(), mid, now);
////        float relevance = Math.max(relMin, relMax );
//
//
        float confScale = Param.REVECTION_CONFIDENCE_FACTOR * (1f - overlap);
//
//        if (confScale < Param.BUDGET_EPSILON) //TODO use NAR.confMin which will be higher than this
//            return null;
//
//        confScale = Math.min(1f, confScale);

        Truth t = truth(mid, now, eternal);

        if (t != null) {
            t = t.confMult(confScale);

            if (t != null)
                return Revision.merge(a, b, mid, now, t, concept);
        }

        return null;
    }


    @Nullable
    @Override
    public final Task strongest(long when, long now, @Nullable Task against) {

        //removeDeleted();

        int ls = size();
        if (ls == 0)
            return null;

        Task best;
        synchronized (this) {
            best = get(0);
            if (ls == 1)
                return best; //early optimization: the only task

            float bestRank = rank(best, when, now); //the first one

            for (int i = 1; i < ls; ) {
                Task x = get(i);
                if (x != null) {

                    if (x.isDeleted()) {
                        remove(i);
                        ls--;
                        continue;
                    }

                    float r = rank(x, when, now);

                    if (r > bestRank) {
                        best = x;
                        bestRank = r;
                    }

                }
                i++;
            }
        }

        return best;

    }

    @Nullable
    @Override
    public final Truth truth(long when, long now, @Nullable EternalTable eternal) {


        int s;
        Task[] copy;
        synchronized (this) {
            //clone a copy so that truthpolation can freely operate asynchronously
            s = size();
            if (s == 0) return null;
            copy = toArrayExact(new Task[s]);
        }

        Truth res;
        if (s == 1) {
            Task the = copy[0];
            res = the.truth();
            long o = the.occurrence();
            if ((now == ETERNAL || when == now) && o == when) //optimization: if at the current time and when
                return res;
            return res!=null ? Revision.project(res, when, now, o, false) : null;

        } else {
            return truthpolations.get().truth(when, now, copy);
        }

    }


    private int removeAlreadyDeleted(@NotNull List<Task> displ) {
        int s = size();
        for (int i = 0; i < s; ) {
            Task x = get(i);
            if (x == null || x.isDeleted()) {
                remove(i, displ);
                s--;
            } else {
                i++;
            }
        }
        return s;
    }


    //    public final boolean removeIf(@NotNull Predicate<? super Task> o) {
//
//        IntArrayList toRemove = new IntArrayList();
//        for (int i = 0, thisSize = this.size(); i < thisSize; i++) {
//            Task x = this.get(i);
//            if ((x == null) || (o.test(x)))
//                toRemove.add(i);
//        }
//        if (toRemove.isEmpty())
//            return false;
//        toRemove.forEach(this::remove);
//        return true;
//    }

    //    public Task weakest(Task input, NAR nar) {
//
//        //if (polation == null) {
//            //force update for current time
//
//        polation.credit.clear();
//        Truth current = truth(nar.time());
//        //}
//
////        if (polation.credit.isEmpty())
////            throw new RuntimeException("empty credit table");
//
//        List<Task> list = list();
//        float min = Float.POSITIVE_INFINITY;
//        Task minT = null;
//        for (int i = 0, listSize = list.size(); i < listSize; i++) {
//            Task t = list.get(i);
//            float x = polation.value(t, -1);
//            if (x >= 0 && x < min) {
//                min = x;
//                minT = t;
//            }
//        }
//
//        System.out.println("removing " + min + "\n\t" + polation.credit);
//
//        return minT;
//    }


    //    public @Nullable Truth topTemporalCurrent(long when, long now, @Nullable Task topEternal) {
//        //find the temporal with the best rank
//        Task t = topTemporal(when, now);
//        if (t == null) {
//            return (topEternal != null) ? topEternal.truth() : Truth.Null;
//        } else {
//            Truth tt = t.truth();
//            return (topEternal() != null) ? tt.interpolate(topEternal.truth()) : tt;
//
//            //return t.truth();
//        }
//    }


//    //NEEDS DEBUGGED
//    @Nullable public Truth topTemporalWeighted(long when, long now, @Nullable Task topEternal) {
//
//        float sumFreq = 0, sumConf = 0;
//        float nF = 0, nC = 0;
//
//        if (topEternal!=null) {
//            //include with strength of 1
//
//            float ec = topEternal.conf();
//
//            sumFreq += topEternal.freq() * ec;
//            sumConf += ec;
//            nF+= ec;
//            nC+= ec;
//        }
//
//        List<Task> temp = list();
//        int numTemporal = temp.size();
//
//        if (numTemporal == 1) //optimization: just return the only temporal truth value if it's the only one
//            return temp.get(0).truth();
//
//
////        long maxtime = Long.MIN_VALUE;
////        long mintime = Long.MAX_VALUE;
////        for (int i = 0, listSize = numTemporal; i < listSize; i++) {
////            long t = temp.get(i).occurrence();
////            if (t > maxtime)
////                maxtime = t;
////            if (t < mintime)
////                mintime = t;
////        }
////        float dur = 1f/(1f + (maxtime - mintime));
//
//
//        long mdt = Long.MAX_VALUE;
//        for (int i = 0; i < numTemporal; i++) {
//            long t = temp.get(i).occurrence();
//            mdt = Math.min(mdt, Math.abs(now - t));
//        }
//        float window = 1f / (1f + mdt/2);
//
//
//        for (int i = 0, listSize = numTemporal; i < listSize; i++) {
//            Task x = temp.get(i);
//
//            float tc = x.conf();
//
//            float w = TruthFunctions.temporalIntersection(
//                    when, x.occurrence(), now, window);
//
//            //strength decreases with distance in time
//            float strength =  w * tc;
//
//            sumConf += tc * w;
//            nC+=tc;
//
//            sumFreq += x.freq() * strength;
//            nF+=strength;
//        }
//
//        return nC == 0 ? Truth.Null :
//                new DefaultTruth(sumFreq / nF, (sumConf/nC));
//    }

}
