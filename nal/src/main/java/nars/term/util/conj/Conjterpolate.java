package nars.term.util.conj;

import jcog.Util;
import jcog.WTF;
import jcog.data.list.FasterList;
import jcog.data.set.ArrayUnenforcedSet;
import nars.term.Term;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.primitive.LongObjectPair;
import org.eclipse.collections.impl.factory.Sets;

import java.util.Random;

/**
 * interpolate conjunction sequences
 * for each of b's events, find the nearest matching event in a while constructing a new Conj consisting of their mergers
 * * similar to conjMerge but interpolates events so the resulting
 * * intermpolation is not considerably more complex than either of the inputs
 * * assumes a and b are both conjunctions
 * <p>
 * UNTESTED
 */
public class Conjterpolate extends Conj {
//    private final Random rng;

    float addProb;

    /**
     * proportion of a vs. b, ie: (a/(a+b))
     */

    public Conjterpolate(Term a, Term b, float aProp, Random rng) {
        super();


        FasterList<LongObjectPair<Term>> aa = a.eventList();
        FasterList<LongObjectPair<Term>> bb = b.eventList();
        int na = aa.size(), nb = bb.size();

        int minLen = Math.min(na, nb);
        int prefixMatched = 0;
        for (; aa.get(prefixMatched).equals(bb.get(prefixMatched)) && ++prefixMatched < minLen; ) ;

//        int suffixMatched = 0;
//        for (; aa.get(na - 1 - suffixMatched).equals(bb.get(nb - 1 - suffixMatched)) && ++suffixMatched < minLen; ) ;
//
//        if (prefixMatched < suffixMatched) {
            //add the suffixed matched segment
//            for (int i = 0; i < suffixMatched; i++) {
//                if (!add(aa.get(na - 1 - i)))
//                    throw new WTF();
//            }
//            aa.removeAbove(suffixMatched);
//            bb.removeAbove(suffixMatched);
//        } else if (prefixMatched > 0) {
        if (prefixMatched > 0) {
            for (int i = 0; i < prefixMatched; i++) {
                if (!add(aa.get(i)))
                    throw new WTF();
            }
            aa.removeBelow(prefixMatched);
            bb.removeBelow(prefixMatched);
            na -= prefixMatched;
            nb -= prefixMatched;
        }
//        }



        int remainingEvents = Util.lerp(aProp, na, nb);
        if (remainingEvents > 0) {
            if (nb == 0 ^ na == 0) {
                addAll((aa.isEmpty() ? bb : aa)); //the remaining events
            } else {
                //add common events
                MutableSet<LongObjectPair<Term>> common = Sets.intersect(ArrayUnenforcedSet.wrap(aa), ArrayUnenforcedSet.wrap(bb));
                if (!common.isEmpty()) {
                    for (LongObjectPair<Term> c : common) {
                        if (!add(c))
                            break; //try to catch if this happens
                    }
                    aa.removeAll(common);
                    bb.removeAll(common);
                    remainingEvents -= common.size();
                }
                if (remainingEvents > 0) {
                    do {
                        FasterList<LongObjectPair<Term>> which;
                        if (!aa.isEmpty() && !bb.isEmpty())
                            which = rng.nextFloat() < aProp ? aa : bb;
                        else if (aa.isEmpty())
                            which = bb;
                        else
                            which = aa;

                        if (!add(which.remove(rng.nextInt(which.size()))))
                            break;

                    } while (--remainingEvents > 0);


                }
            }

        }

        //distribute

//        this.rng = rng;
//
//        long dt = Conj.isSeq(a) || Conj.isSeq(b) || a.dt()==0 || b.dt()==0 ? 0 : ETERNAL;
//        addProb = aProp;
//        if (add(dt, a)) {
//            addProb = 1-aProp;
//            if (add(dt, b)) {
//
//            }
//        }

    }


//
//    @Override
//    public boolean add(long at, Term x) {
//        if (rng.nextFloat() < addProb)
//            return super.add(at, x);
//        else
//            return true; //ignore
//    }

//    protected void compress(int targetVol, int interpolationThresh /* time units */) {
//
//        if (interpolationThresh < 1)
//            return;
//
//        //factor();
//        distribute();
//
//        //find any two time points that differ by less than the interpolationThresh interval
//        long[] times = this.event.keySet().toSortedArray();
//        if (times.length < 2) return;
//        for (int i = 1; i < times.length; i++) {
//            if (times[i - 1] == ETERNAL)
//                continue;
//            long dt = times[i] - times[i - 1];
//            if (Math.abs(dt) < interpolationThresh) {
//                if (combine(times[i - 1], times[i])) {
//                    i++; //skip past current pair
//                }
//            }
//        }
//    }
//
//    boolean combine(long a, long b) {
//        assert (a != b);
//        assert (a != DTERNAL && b != DTERNAL && a != XTERNAL && b != XTERNAL);
//        ByteHashSet common = new ByteHashSet();
//        events((byte[])event.remove(a), common::add);
//        events((byte[])event.remove(b), common::add);
//
//        //detect conflicting combination
//        byte[] ca = common.toArray();
//        boolean changed = false;
//        for (byte cc : ca) {
//            if (cc < 0 && common.contains((byte) -cc)) {
//                common.remove(cc);
//                common.remove((byte) -cc);
//                changed = true;
//            }
//        }
//        if (changed) {
//            ca = common.toArray();
//        }
//        if (ca.length > 0) {
//            long mid = (a + b) / 2L; //TODO better choice
//            event.put(mid, ca);
//        }
//        return true;
//    }
//
//        @Override
//        public boolean add(long bt, final Term what) {
//            assert (bt != XTERNAL);
//
//            {
//                boolean neg = what.op() == NEG;
//
//
//                byte tid = termToId.getIfAbsent(neg ? what.unneg() : what, (byte) -1);
//                if (tid == (byte) -1)
//                    return super.add(bt, what);
//
//                byte tInA = (byte) (tid * (neg ? -1 : +1));
//
//
//                LongArrayList whens = new LongArrayList(2);
//
//                aa.event.forEachKeyValue((long when, Object wat) -> {
//                    if (wat instanceof RoaringBitmap) {
//                        RoaringBitmap r = (RoaringBitmap) wat;
//                        if (r.contains(tInA) && !r.contains(-tInA)) {
//                            whens.add(when);
//                        }
//                    } else {
//                        byte[] ii = (byte[]) wat;
//                        if (ArrayUtils.indexOf(ii, tInA) != -1 && ArrayUtils.indexOf(ii, (byte) -tInA) == -1) {
//                            whens.add(when);
//                        }
//                    }
//                });
//
//
//                int ws = whens.size();
//                if (ws > 0) {
//
//                    if (whens.contains(bt))
//                        return true;
//
//                    long at;
//                    if (ws > 1) {
//                        LongToLongFunction temporalDistance;
//                        if (bt == ETERNAL) {
//                            temporalDistance = (a) -> a == ETERNAL ? 0 : Long.MAX_VALUE;
//                        } else {
//                            temporalDistance = (a) -> a == ETERNAL ? Long.MAX_VALUE : Math.abs(bt - a);
//                        }
//                        long[] whensArray = whens.toArray();
//                        ArrayUtils.sort(whensArray, temporalDistance);
//
//                        at = whensArray[whensArray.length - 1];
//                    } else {
//                        at = whens.get(0);
//                    }
//
//                    long merged = merge(at, bt);
//                    if (merged != at) {
//
//                        if ((merged == DTERNAL || merged == XTERNAL) && (at != DTERNAL && bt != DTERNAL && at != XTERNAL && bt != XTERNAL)) {
//                            //add as unique event (below)
//                        } else {
////                            boolean r = aa.remove(what, at); //remove original add the new merged
////                            if (!r) {
////                                assert (r);
////                            }
//                            return super.add(merged, what);
//                        }
//
//                    } else {
//                        return true; //exact
//                    }
//                }
//                return super.add(bt, what);
//
//            }
//
//        }

//        long merge(long a, long b) {
//            if (a == b) return a;
//            if (a == ETERNAL || b == ETERNAL)
//                return ETERNAL;
//            if (a == XTERNAL || b == XTERNAL)
//                throw new RuntimeException("xternal in conjtermpolate");
//
//
//            return Tense.dither(Revision.merge(a, b, aProp, nar), nar);
//
//        }

}
//            this.b = b;
//            this.nar = nar;

//            this.aa = Conj.from(a);
//            this.idToTerm.addAll(aa.idToTerm);
//            this.termToId.putAll(aa.termToId);


//TODO time warping algorithm: find a shift that can be applied ot adding b that allows it to match a longer sub-sequence
//for now this is a naive heuristic
//        int aShift, bShift;
//        if (a.eventFirst().equals(b.eventFirst())) {
//            aShift = bShift = 0;
//        } else if (a.eventLast().equals(b.eventLast())) {
//            bShift = a.eventRange() - b.eventRange();
//            aShift = 0;
//        } else {
//            //align center
//            int ae = a.eventRange();
//            int be = b.eventRange();
//            if (ae!=be) {
//                if (ae < be) {
//                    aShift = be/2 - ae/2;
//                    bShift = 0;
//                } else {
//                    aShift = 0;
//                    bShift = ae/2 - be/2;
//                }
//            } else {
//                aShift = bShift = 0;
//            }
//        }
//
//        if (bShift < 0) {
//            aShift += -bShift;
//            bShift = 0;
//        } else if (aShift < 0) {
//            bShift += -aShift;
//            aShift = 0;
//        }


//            //merge remaining events from 'a'
//            final boolean[] err = {false};
//            aa.event.forEachKeyValue((long when, Object wat) -> {
//                if (err[0])
//                    return; //HACK
//                if (wat instanceof RoaringBitmap) {
//                    RoaringBitmap r = (RoaringBitmap) wat;
//                    r.forEach((int ri) -> {
//                        boolean neg = (ri < 0);
//                        if (neg) ri = -ri;
//                        if (!add(when, idToTerm.get(ri-1).negIf(neg))) {
//                            err[0] = true;
//                        }
//                    });
//                } else {
//                    byte[] ii = (byte[]) wat;
//                    for (byte ri : ii) {
//                        if (ri == 0)
//                            break; //eol
//                        boolean neg = (ri < 0);
//                        if (neg) ri = (byte) -ri;
//                        if (!add(when, idToTerm.get(ri-1).negIf(neg))) {
//                            err[0] = true;
//                        }
//                    }
//                }
//            });
//add remaining
//                assert (!aa.isEmpty() && !bb.isEmpty());
//
//                ArrayHashSet<LongObjectPair<Term>> ab = new ArrayHashSet(aa.size() + bb.size());
//                ab.addAll(aa);
//                ab.addAll(bb);
//                for (int i = 0; i < remainingEvents; i++) {
//                    if (!add(ab.remove(rng))) {
//                        //oops try to prevent if this happens
//                        break;
//                    }
//                }