package nars.time;

import com.google.common.collect.Iterables;
import jcog.WTF;
import jcog.data.bit.MetalBitSet;
import jcog.data.graph.FromTo;
import jcog.data.graph.ImmutableDirectedEdge;
import jcog.data.graph.MapNodeGraph;
import jcog.data.graph.Node;
import jcog.data.graph.search.Search;
import jcog.data.list.FasterList;
import jcog.data.set.ArrayHashSet;
import jcog.math.Longerval;
import jcog.util.ArrayUtils;
import nars.Op;
import nars.Param;
import nars.subterm.Subterms;
import nars.term.Term;
import nars.term.util.conj.Conj;
import nars.term.util.conj.ConjSeq;
import org.apache.commons.math3.exception.MathArithmeticException;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.api.tuple.primitive.BooleanObjectPair;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import static jcog.Util.hashCombine;
import static nars.Op.*;
import static nars.time.Tense.*;
import static nars.time.TimeSpan.TS_ZERO;
import static org.eclipse.collections.impl.tuple.Tuples.pair;

/**
 * represents a multigraph of events and their relationships
 * calculates unknown times by choosing from the possible
 * pathfinding results.
 * <p>
 * it can be used in various contexts:
 * a) the tasks involved in a derivation
 * b) as a general purpose temporal index, ie. as a meta-layer
 * attached to one or more concept belief tables
 * <p>
 * DTERNAL relationships can be maintained separate
 * from +0.
 */
public class TimeGraph extends MapNodeGraph<Event, TimeSpan> {


    /**
     * index by target
     */
//    public final Multimap<Term, Event> byTerm = MultimapBuilder
//            .hashKeys()
//            .linkedHashSetValues()
//            .build();

    public final Map<Term, Collection<Event>> byTerm = new HashMap<Term, Collection<Event>>() {
        @Override
        public Collection<Event> get(Object key) {
            Collection<Event> x = super.get(key);
            return x == null ? List.of() : x;
        }
    };

//    public final UnifiedSetMultimap<Term, Event> byTerm = new UnifiedSetMultimap<>();


    public final MutableSet<Term> autoNeg = new UnifiedSet() {
        @Override
        public boolean add(Object key) {
            return super.add(((Term) key).unneg());
        }
    };

    protected final ArrayHashSet<Event> solutions = new ArrayHashSet();
    private transient Term solving;
    private Predicate<Event> target;


    /**
     * since CONJ will be constructed with conjMerge, if x is conj the dt between events must be calculated from start-start. otherwise it is implication and this is measured internally
     */
    private int dt(Event aa, Event bb, boolean absolute) {

        assert(!aa.equals(bb));

        long aWhen = aa.start();
        long bWhen;
        if (aWhen == ETERNAL || (bWhen = bb.start()) == ETERNAL)
            return DTERNAL;
        else {
            assert (aWhen != TIMELESS && bWhen != TIMELESS);
            long d;
            if (!absolute || aWhen <= bWhen)
                d = (bWhen - aWhen) - (absolute ? 0 : aa.id.eventRange());
            else
                d = (aWhen - bWhen) - (absolute ? 0 : bb.id.eventRange());

            return occToDT(d);
        }

    }

    @Override
    public void clear() {
        super.clear();
        byTerm.clear();
    }

    /**
     * creates an event for a hypothetical target which may not actually be an event;
     * but if it is there or becomes there, it will connect what it needs to
     */
    protected Event shadow(Term v) {
        //return event(v, TIMELESS, false);
        return new Relative(v);
    }

    public final Event know(Term v) {
        //include the temporal information contained in a temporal-containing target;
        // otherwise it contributes no helpful information
        if (v.hasAny(Op.Temporal))
            return event(v, TIMELESS, TIMELESS, true);
        else
            return shadow(v);
    }

    public final Event know(Term t, long start) {
        return event(t, start, start, true);
    }

    public final Event know(Term t, long start, long end) {
        return event(t, start, end, true);
    }


    private Event event(Term t, long start, long end, boolean add) {
        if (!t.op().eventable)
            throw new WTF();

        Event event;
        if (start == TIMELESS) {
            assert (add) : "use shadow(t) if not adding";
            event = new Relative(t);
        } else {

            if (start!=ETERNAL) {
                //optional dithering
                start = occ(start);
                end = occ(end);
            }

            if (add) {

                Collection<Event> te = byTerm.get(t);
                int nte = te.size();
                if (nte > 0) {

                    boolean stable;
                    do {

                        stable = true;

                        Iterator<Event> ff = te.iterator();
                        while (ff.hasNext()) {
                            Event f = ff.next();
                            if (!(f instanceof Absolute))
                                continue;
                            Absolute af = (Absolute) f;
                            long as = af.start();
                            if (start == ETERNAL && as == ETERNAL)
                                return af;
                            if (as == ETERNAL)
                                continue;

                            if (Param.TIMEGRAPH_ABSORB_CONTAINED_EVENT) {
                                if (af.containsOrEquals(start, end)) {
                                    //add = false;
                                    //break; //dont affect the stored graph, but return the smaller interval that was input

                                    return af; //return the absorbing event
                                }
                            } else {
                                if (af.start == start && af.end() == end)
                                    return af;
                            }


                            if (add && af.containedIn(start, end)) {
                                if (Param.TIMEGRAPH_ABSORB_CONTAINED_EVENT) {
                                    //absorb existing
                                    removeNode(f);
                                    ff.remove();
                                    nte--;
                                }
                            } else {
                                if (start != ETERNAL && Param.TIMEGRAPH_MERGE_INTERSECTING_EVENTS) {
                                    long[] merged;
                                    if ((merged = af.unionIfIntersects(start, end)) != null) {

                                        //stretch
                                        start = merged[0];
                                        end = merged[1];

                                        if (add) {
                                            removeNode(f);
                                            ff.remove();
                                            nte--;
                                            stable &= nte <= 1; //try again if other nodes, because it may connect with other ranges further in the iteration
                                        }

                                        break;
                                    }
                                }
                            }


                        }
                    } while (!stable);
                }
            }

            event = (end != start) ? new AbsoluteRange(t, start, end) : new Absolute(t, start);
        }


        if (add) {
            return addNode(event).id;
        } else {
            return event;
        }
    }

    private int absoluteCount(Term t) {
        Collection<Event> tt = byTerm.get(t);
        return absoluteCount(tt);
    }

    private static int absoluteCount(Collection<Event> tt) {
        int c = 0;
        for (Event tx : tt) {
            if (tx instanceof Absolute)
                c++;
        }
        return c;
    }

    public Event event(Event e) {
        Node<Event, TimeSpan> existing = node(e);
        return existing != null ? existing.id() : e;
    }

    protected void shadow(Term x, Term y) {
        assert(!(x.equals(y)));
        shadow(shadow(x), shadow(y));
    }

    protected void shadow(Event a, Event b) {
        link(a, TimeSpan.TS_ZERO, b);
    }

    protected boolean link(Event before, TimeSpan e, Event after) {
        MutableNode<Event, TimeSpan> x = addNode(before);
        MutableNode<Event, TimeSpan> y = before.equals(after) ? x : addNode(after);

        return addEdge(x, e, y);
    }

    private void link(Event x, long dt, Event y) {

        boolean parallel = dt == ETERNAL || dt == TIMELESS || dt == 0;
        int vc = x.compareTo(y);
        if (vc == 0) { //equal?
            if (parallel) return; //no point
            y = x; //use same instance, they could differ
            if (dt < 0)
                dt = -dt; //use only positive dt values for self loops
        } else {
            if (vc > 0) {
                if (!parallel) {
                    dt = -dt;
                }
                Event z = x;
                x = y;
                y = z;
            }
        }

        link(x, TimeSpan.the(dt), y);
    }

    @Override
    protected void onAdd(Node<Event, TimeSpan> x) {
        Event event = x.id();
        Term eventTerm = event.id;

        final boolean[] newTerm = {false}, newEvent = {false};
        byTerm.compute(eventTerm, (k, v) -> {
            if (v == null) {
                newTerm[0] = newEvent[0] = true;
                return new ArrayHashSet<Event>(2).with(event);
            } else {
                if (v.add(event)) {
                    newEvent[0] = true;
                }
                return v;
            }
        });
        if (!newEvent[0])
            return;
        if (newTerm[0])
            onNewTerm(eventTerm);


//        Collection<Event> ee = byTerm.get(eventTerm);
//        if (ee.isEmpty()) {
//            byTerm.put(eventTerm, new UnifiedSet(2).with(event)); //TODO pool these Set's
//            onNewTerm(eventTerm);
//        } else {
//            if (!ee.addAt(event))
//                //if (!byTerm.put(eventTerm, event))
//                return; //already present
//        }

        if (decomposeAddedEvent(event)) {
            int edt = eventTerm.dt();


            switch (eventTerm.op()) {

//                case SECTe:
                    //TODO n-ary
//                    Event a = onlyAbsolute(eventTerm.sub(0));
//                    if (a != null) {
//                        Event b = onlyAbsolute(eventTerm.sub(1));
//                        if (b != null) {
//                            long as = a.start();
//                            long bs = b.start();
//                            if (as == ETERNAL) {
//                                know(eventTerm, bs, b.end());
//                            } else if (bs == ETERNAL) {
//                                know(eventTerm, as, a.end());
//                            } else {
////                                Longerval u = Longerval.union(as, a.end(), bs, b.end());
////                                know(eventTerm, u.a, u.b);
//                                long[] u = Longerval.intersectionArray(as, a.end(), bs, b.end());
//                                if (u != null)
//                                    know(eventTerm, u[0], u[1]);
//                            }
//                        }
//                    }
//                    break;

//                case DIFFi:
//                case SECTi:
//                case SECTe:
//                case SIM:
//                case INH:
//                case SETe:
//                case SETi: {
//                    if (!(event instanceof Absolute)) {
//                        //naive estimate method 1:
//                        //if all components of the compound are known, assign the average of their occurrence for the compound
//                        Subterms ess = eventTerm.subterms();
//                        int essn = ess.subs();
//                        LongArrayList subOcc = new LongArrayList(essn);
//
//                        nextTerm:
//                        for (Term s : eventTerm.subterms()) {
//                            for (Event es : events().get(s)) {
//                                if (es instanceof Absolute) {
//                                    //TODO select more carefully
//                                    subOcc.addAt(es.mid());
//                                    continue nextTerm;
//                                }
//                            }
//                            //occurrence not found
//                            subOcc = null;
//                            break;
//                        }
//                        if (subOcc != null) {
//                            know(eventTerm, Math.round(subOcc.average())); //TODO estimate start/end range
//                        }
//                    }
//                }


                case IMPL:

                    Term subj = eventTerm.sub(0), pred = eventTerm.sub(1);
                    Event se = know(subj), pe = know(pred);

                    if (edt == DTERNAL) {

                        //link(se, ETERNAL, pe);

//                        //link first two events of each
//                        if (subj.hasAny(Op.CONJ)) {
                        subj.eventsWhile((w, y) -> {
                            link(know(y), ETERNAL, pe);
                            return true;
                        }, 0, false, true, true);

//
                        pred.eventsWhile((w, y) -> {
                            link(se, ETERNAL, know(y));
                            return true;
                        }, 0, false, true, true);

                    } else if (edt == XTERNAL) {

                    } else {
                        link(se, edt + subj.eventRange(), pe);

//                        if (!(subj.op()==CONJ && subj.dt()==XTERNAL)) {
//                            int st = subj.eventRange();
//                            link(se, (edt + st), pe);

//                            Term sf = se.id.eventLast();
//                            Term pl = se.id.eventFirst();


//                            if (subj.op() == Op.CONJ && subj.dt() == DTERNAL) { //HACK to decompose ordinarily non-decomposed &&
//                                subj.eventsWhile((w, y) -> {
//                                    link(know(y), edt + st - w, pe);
//                                    return true;
//                                }, 0, false, true, false, 0);
//                            }
//
//                            if (pred.op() == Op.CONJ && pred.dt() == DTERNAL) { //HACK to decompose ordinarily non-decomposed &&
//                                pred.eventsWhile((w, y) -> {
//                                    link(se, edt + st + w, know(y));
//                                    return true;
//                                }, 0, false, true, false, 0);
//                            }
//                        }
                    }

                    break;


                case CONJ:


                    long eventStart = event.start(), eventEnd = event.end();

                    switch (edt) {

                        case XTERNAL:
                            for (Term sub : eventTerm.subterms())
                                know(sub);
                            break;


                        case 0:
                            Subterms es = eventTerm.subterms();
                            int esn = es.subs();
                            for (int i = 0; i < esn; i++)
                                link(event, 0,
                                        eventStart==TIMELESS ?
                                            know(es.sub(i))
                                            :
                                            (eventStart==ETERNAL ? know(es.sub(i), ETERNAL) :
                                                    know(es.sub(i), eventStart, eventEnd))
                                );

                            break;

                        case DTERNAL:
                        default:

                            if (eventStart != ETERNAL && eventStart != TIMELESS) {
                                //chain the events to the absolute parent
                                long range = eventEnd - eventStart;
                                eventTerm.eventsWhile((w, y) -> {

                                    link(event, w - eventStart, know(y, w, w + range));
                                    return true;
                                }, eventStart, false, true, false);
                            } else {
                                //chain the events together relatively
                                final Event[] prev = {event};
                                final long[] prevDT = {0};
                                eventTerm.eventsWhile((w, y) -> {
                                    if (!y.equals(eventTerm)) {
                                        Event next = know(y);

                                        link(prev[0], w - prevDT[0], next);
                                        prevDT[0] = w;
                                        prev[0] = next;
                                    }
                                    return true;
                                }, 0, false, false, false);
                            }

                            break;
                    }

                    break;
            }
        }

    }

    protected boolean decomposeAddedEvent(Event event) {
        //return true;
        return event.id.op().temporal;
    }

    private void onNewTerm(Term t) {
        if (autoNeg != null && autoNeg.contains(t.unneg())) {
            link(shadow(t), 0, shadow(t.neg()));
        }
    }


    private boolean solveDT(Term x, Predicate<Event> each) {

        if (!termsEvent(x)) return true;

        assert (x.dt() == XTERNAL);

        Subterms xx = x.subterms();

        //assert(!xx.hasXternal()): "dont solveDTTrace if subterms have XTERNAL";

        int subs = xx.subs();
        if (subs == 2) {
            Term a = xx.sub(0), b = xx.sub(1);

            boolean aEqB = a.equals(b);

            return solveDTTrace(x, each, a, b, aEqB) && solveDTAbsolutePair(x, each, a, b, aEqB);

        } else {
            //throw new TODO();
            assert (x.op() == CONJ);
            //HACK try any two and then solve that with the 3rd
            if (subs == 3) {
                Term a = xx.sub(0), b = xx.sub(1), c = xx.sub(2);
                //go in reverse complexity order
                return solveDT(CONJ.the(XTERNAL, c, b), (bc) -> {
                    return solveDT(CONJ.the(XTERNAL, bc.id, a), each);
                });
            }


            return true;
        }


    }


    private boolean solveDTTrace(Term x, Predicate<Event> each, Term _a, Term _b, boolean aEqB) {

        Term a = _a;//.eventLast();
        Term b = _b;//.eventFirst();

        Collection<Event> ab = null;

        Collection<Event> aa = byTerm.get(a);
        if (!aa.isEmpty())
            ab = new FasterList(aa);
        else if (aEqB)
            return true; //nothing

        if (!aEqB) {
            Collection<Event> bb = byTerm.get(b);
            if (!bb.isEmpty()) {
                if (ab != null)
                    ab.addAll(bb);
                else
                    ab = new FasterList(bb);
            } else {
                if (ab == null)
                    return true; //nothing
            }
        }


        int s = ab.size();
        assert (s > 0);

        if (s > 1) {
            ((FasterList) ab).shuffleThis(random());

            //then sort the Absolute events to be tried first
            ((FasterList<Event>) ab).sortThisByBoolean(e -> !(e instanceof Absolute));
        }

        return bfsPush(ab, new CrossTimeSolver() {
            @Override
            protected boolean next(BooleanObjectPair<FromTo<Node<Event, TimeSpan>, TimeSpan>> move, Node<Event, TimeSpan> next) {

                Node<Event, TimeSpan> s = pathStart(path);
                Node<Event, TimeSpan> e = pathEnd(path);
                Event ss = s.id();
                Term st = ss.id;
                Event ee = e.id();
                Term et = ee.id;

                boolean dir;
                if (at(a, st) && at(b, et)) dir = true;
                else if (at(a, et) && at(b, st)) dir = false;
                else {
                    return true; //not the target destination
                }

                long start;

                long[] pt = pathTime(path);
                if (pt == null)
                    return true;

                long dt = pt[0];

                if (dt == ETERNAL) dt = 0; //HACK

                if (!(ss instanceof Absolute) && !(ee instanceof Absolute)) {
                    start = TIMELESS;
                } else {
                    if (dir) {
                        if (ss instanceof Absolute) {
                            start = ((Absolute) ss).start();
                        } else {
                            start = ((Absolute) ee).start() - dt - a.eventRange();
                        }
                    } else {
                        if (ee instanceof Absolute) {
                            start = ((Absolute) ee).start();
                        } else {
                            start = ((Absolute) ss).start() - dt - b.eventRange();
                        }
                    }
                }

                if (dt != ETERNAL && !dir)
                    dt = -dt;

                long dur = pt[1];
                switch (x.op()) {
                    case IMPL:
                        start = TIMELESS; //the start time does not mean the event occurrence time
                        if (dt != ETERNAL)
                            dt -= _a.eventRange();
                        break;
                }

                int idt = occToDT(dt);

                return solveDT(x, start, idt, dur, path, dir, each);
            }
        });


    }

    /**
     * weather subEvent time occurrs at the start of the event
     */
    private static boolean at(Term x, Term y) {
        if (y.equals(x))
            return true;
        int xv = x.volume(), yv = y.volume();
        if (xv > yv)
            return atStart(y, x);
        else if (yv > xv)
            return atStart(x, y);
        else
            return false;
    }

    private static boolean atStart(Term subEvent, Term event) {
        if (event.op() == CONJ && event.dt() != XTERNAL) {
            boolean seq = Conj.isSeq(event);
            if (!seq) {
                if (event.contains(subEvent))
                    return true;
            } else if (seq) {
                if (event.eventFirst().equals(subEvent))
                    return true;
            }

        }
        return false;
    }

    private boolean solveDTAbsolutePair(Term x, Predicate<Event> each, Term a, Term b, boolean aEqB) {
        if (!a.hasXternal() && !b.hasXternal()) {
            UnifiedSet<Event> ae = new UnifiedSet(2);
            //solveExact(a, ax -> {
            solveOccurrence(a, ax -> {
                if (ax instanceof Absolute) ae.add(ax);
                return true;
            });
            int aes = ae.size();
            if (aes > 0) {

                if (aEqB && aes > 1) {

                    Event[] aa = eventArray(ae);

                    for (int i = 0; i < aa.length; i++) {
                        Event ii = aa[i];
                        for (int j = i + 1; j < aa.length; j++) {
                            if (!solveDTAbsolutePair(x, ii, aa[j], each))
                                return false;
                        }
                    }


                } else {
                    UnifiedSet<Event> be = new UnifiedSet(2);
                    //solveExact(b, bx -> { //less exhaustive
                    solveOccurrence(b, bx -> {
                        if ((bx instanceof Absolute) && (!ae.contains(bx)))
                            be.add(bx);
                        return true;
                    });


                    if (!be.isEmpty()) {
                        for (Event ax : (ae)) {
                            for (Event bx : (be)) {
                                if (ax != bx) {
                                    if (!solveDTAbsolutePair(x, ax, bx, each))
                                        return false;
                                }
                            }
                        }

                    }
                }
            }
        }
        return true;
    }

    private static final Event[] EMPTY_EVENT_ARRAY = new Event[0];

    private Event[] eventArray(UnifiedSet<Event> ae) {
        Event[] aa = ae.toArray(EMPTY_EVENT_ARRAY);
        if (aa.length > 1) ArrayUtils.shuffle(aa, random());
        return aa;
    }

    private boolean solveDTAbsolutePair(Term x, Event a, Event b, Predicate<Event> each) {
        assert (!(a.equals(b)));
        if (a.start() == b.start() && at(a.id, b.id))
            return true; //same event
        //TODO additional checking



        if (x.op() == CONJ) {
            int dt = dt(a, b, true);
            return solveConj2DT(each, a, dt, b);
        } else {
            //for impl and other types cant assume occurrence corresponds with subject
            int dt = dt(a, b, false);
            return solveDT(x, TIMELESS, dt, durMerge(a, b), null, true, each);
        }
    }

    /**
     * solution vector for 2-ary CONJ
     */
    private boolean solveConj2DT(Predicate<Event> each, Event a, int dt, Event b) {

        if (dt != DTERNAL && dt != 0) {
            assert (dt != XTERNAL);
            //swap to correct sequence order
            if (a.start() > b.start()) {
                Event z = a;
                a = b;
                b = z;
//                dt = -dt;
            }
        }

        Term c = ConjSeq.sequence(a.id, dt == DTERNAL ? ETERNAL : 0, b.id, dt == DTERNAL ? ETERNAL : dt);


        return solveOccurrence(c, a.start(), durMerge(a, b), each);
    }

    private static long durMerge(Event a, Event b) {
        if (a instanceof Absolute && b instanceof Absolute) {
            long ad = a.dur();
            long bd = b.dur();
            return (a.id.op() == IMPL || b.id.op() == IMPL) ?
                    Math.max(ad, bd) //implications are like temporal pointers, not events.  so they shouldnt shrink duration
                    :
                    Math.min(ad, bd);
        } else if (a instanceof Absolute && !(b instanceof Absolute)) {
            return a.dur();
        } else if (b instanceof Absolute && !(a instanceof Absolute)) {
            return b.dur();
        } else {
            return 0;
        }
    }

    /**
     * tests whether the two terms refer to the same sub-events,
     * which have known multiple occurrences
     * which would cause incorrect results if interpreted literally
     * this prevents separate instances of events from being welded together or arranged in the incorrect temporal order
     * across time when there should be some non-zero dt
     * <p>
     * a volume ideally should be less than b's
     */
    private boolean commonSubEventsWithMultipleOccurrences(Term a, Term b) {
        if (a.op() == CONJ && b.op() == CONJ) {
            if (a.volume() > b.volume()) {
                Term c = a; //swap
                a = b;
                b = c;
            }

            UnifiedSet<Term> eventTerms = new UnifiedSet(2);
            a.eventsWhile((w, aa) -> {
                if (absoluteCount(aa) > 1) {
                    eventTerms.add(aa);
                }
                return true;
            }, 0, true, false, true);

            if (eventTerms.isEmpty())
                return false;

            return !b.eventsWhile((w, bb) -> {
                return !eventTerms.remove(bb);
            }, 0, true, false, true);
        }
        return false; //TODO test conj -> nonConj common subevent?
    }

    /**
     * TODO make this for impl only because the ordering of terms is known implicitly from 'x' unlike CONJ
     */
    private boolean solveDT(Term x, long start, int dt, long dur,
                            @Nullable List<BooleanObjectPair<FromTo<Node<Event, TimeSpan>, TimeSpan>>> path, boolean dir, Predicate<Event> each) {

        return solveOccurrence(dt(x, dir, dt), start, dur, each);


    }

    private static boolean termsEvent(Term e) {
        //Op eo = e.op();
        //return eo.conceptualizable || eo.var;
        return e.op().eventable;
    }

    private boolean solveOccurrence(Term y, long start, long dur, Predicate<Event> each) {
        if (!termsEvent(y))
            return true; //keep trying

        return start != TIMELESS ?
                each.test(event(y, start, (start != ETERNAL) ? start + dur : start, false))
                :
                solveOccurrence(y, each);
    }


    /**
     * override to filter dt (ex: dither)
     */
    public int dt(int dt) {
        return dt;
    }

    protected int occToDT(long x) {
        assert (x != TIMELESS);
        int idt;
        if (x == ETERNAL)
            idt = DTERNAL;
        else
            idt = Tense.occToDT(x);
        return idt;
    }

    /**
     * override to filter occ (ex: dither)
     */
    public long occ(long when) {
        return when;
    }




    /**
     * preprocess the dt used to construct a new target.
     * ex: dithering
     */
    @Deprecated
    protected Term dt(Term x, boolean dir, int dt) {

        assert (dt != XTERNAL);

        if (dt == DTERNAL)
            return x.dt(DTERNAL);

        assert (!x.subterms().hasXternal());

        Op xo = x.op();
        Term x0 = x.sub(0);
        if (xo == IMPL) {
            return x.dt(dt);
        } else if (xo == CONJ) {
            if (dt == 0) {
                return x.dt(dt);
            } else {

                if (x.dt() == XTERNAL) {

                    //use the provided 'path' and 'dir'ection, if non-null, to correctly order the sequence, which may be length>2 subterms
                    Term x1 = x.sub(1);

                    if (dir) {
                        return ConjSeq.sequence(x0, 0, x1, dt);
                    } else {
                        return ConjSeq.sequence(x1, 0, x0, -dt);
                    }


                } else {

                    int early = Conj.conjEarlyLate(x, true);
                    if (early == 1)
                        dt = -dt;


                    Term xEarly = x.sub(early);
                    Term xLate = x.sub(1 - early);

                    return ConjSeq.sequence(
                            xEarly, 0,
                            xLate, dt);
                }

            }
        }

        throw new UnsupportedOperationException();
    }


    protected final boolean solution(Event y) {
        if (!(y.start() == TIMELESS && solving.equals(y.id)) && validPotentialSolution(y.id)) {
            if (solutions.add(y)) {
                if (!target.test(y))
                    return false;
            }
        }
        return true;
    }


    private boolean solveExact(Event f, Predicate<Event> each) {
        if (f instanceof Absolute && !f.id.hasXternal())
            if (!each.test(f))
                return false;

        Term x = f.id;
        //try exact absolute solutions
        for (Event e : byTerm.get(x)) {
            if (e instanceof Absolute && ((!(f instanceof Absolute)) || !e.equals(f)) && !each.test(e))
                return false;
        }
        return true;
    }

//    private boolean solveExact(Term x, Predicate<Event> each) {
//        return solveExact(null, each, x);
//    }

    //    @Nullable
//    private Event onlyAbsolute(Term x) {
//        Event first = null;
//        for (Event e : byTerm.get(x)) {
//            if (e instanceof Absolute) {
//                if (first == null)
//                    first = e;
//                else
//                    return null; //more than one, ambiguous
//            }
//        }
//        return first;
//    }

    /**
     * main entry point to the solver
     *
     * @seen callee may need to clear the provided seen if it is being re-used
     */
    public boolean solve(Term x, Predicate<Event> target) {


        this.target = target;
        this.solving = x;
        this.solutions.clear();

        return solveAll(x, this::solution);

    }

    protected boolean validPotentialSolution(Term y) {
        return termsEvent(y);
    }

    private boolean solveAll(Term x, Predicate<Event> each) {
        if (!x.hasXternal()) {
            return solveOccurrence(x, each);
        } else {
            if (!solveRootMatches(x, each))
                return false;

            if (x.subterms().hasXternal()) {
                return solveDTAndOccRecursive(x, each);
            } else {
                return solveDtAndOccTop(x, each);
            }
        }
    }

    private boolean solveRootMatches(Term x, Predicate<Event> each) {

        Op xop = x.op();

        if (x.equals(x.root())) {
            //try any absolute events which have different target ID but the same target root as these will be readily valid solutions
            FasterList<Event> toTry = null; //buffer to avoid concurrent modification exception
            for (Map.Entry<Term, Collection<Event>> e : byTerm.entrySet()) {
                Term et = e.getKey();
                if (et.op() == xop && !x.equals(et) && x.equals(et.root())) {
                    if (toTry == null) toTry = new FasterList(e.getValue());
                    else
                        toTry.addAll(e.getValue());
                }
            }
            if (toTry != null) {
                if (toTry.size() > 1)
                    toTry.shuffleThis(random());
                if (!toTry.allSatisfy(z -> each.test(z)))
                    return false;
            }

        }


        return true;
    }

    private boolean solveDTAndOccRecursive(Term x, Predicate<Event> each) {

        Map<Term, Set<Term>> subSolved = new UnifiedMap(4);

        x.subterms().recurseTerms(Term::hasXternal, y -> {
            if (y.dt() == XTERNAL && !y.subterms().hasXternal()) {

                subSolved.computeIfAbsent(y, (yy) -> {

                    Set<Term> s = new UnifiedSet(2);

//                    int yv = yy.volume();
                    solveDT(yy, z -> {
//                        if (z.id.volume()<yv)
//                            return true; //something probably collapsed

                        //TODO there could be multiple solutions for dt
                        assert (z.id.dt() != XTERNAL);
                        s.add(z.id);
                        return true;
                    });
                    return s.isEmpty() ? java.util.Set.of() : s;
                });
            }
            return true;
        }, null);

        subSolved.values().removeIf(java.util.Set::isEmpty);

        int solvedTerms = subSolved.size();
        switch (solvedTerms) {
            case 0:
                //continue below
                break;
            case 1:
                //randomize the entries
                Map.Entry<Term, Set<Term>> xy = subSolved.entrySet().iterator().next();

                Set<Term> sy = xy.getValue();
                if (!sy.isEmpty()) {
                    Term xyx = xy.getKey();
                    Term[] two = sy.toArray(EmptyTermArray);
                    if (two.length > 1) ArrayUtils.shuffle(two, random());

//                    int xv = x.volume();
                    for (Term sssi : two) {
                        Term y = x.replace(xyx, sssi);

                        if (!solveDtAndOccIfConceptualizable(x, y, each))
                            return false;
                    }
                }
                break;
            default:
                //TODO cartesian product of terms. could be expensive
                //for now randomize and start with first entry
                List<Pair<Term, Term[]>> substs = new FasterList();
                final int[] permutations = {1};
                subSolved.forEach((h, w) -> {
                    Term[] ww = w.toArray(EmptyTermArray);
                    assert (ww.length > 0);
                    permutations[0] *= ww.length;
                    substs.add(pair(h, ww));
                });
                int ns = substs.size();
                assert (ns > 0);
                Random rng = random();


                while (permutations[0]-- > 0) {
                    Map<Term, Term> m = new UnifiedMap(ns);
                    for (Pair<Term, Term[]> si : substs) {
                        Term[] ssi = si.getTwo();
                        Term sssi = ssi[ssi.length > 1 ? rng.nextInt(ssi.length) : 0];
                        m.put(si.getOne(), sssi);
                    }
                    Term z = x.replace(m);

                    if (!solveDtAndOccIfConceptualizable(x, z, each))
                        return false;
                }
                //break;
        }

        return true;
    }

    /**
     * collapse any relative nodes to unique absolute nodes,
     * possibly also map less-specific xternal-containing terms to more-specific unique variations
     */
    public void compact() {
        for (Collection<Event> e : byTerm.values()) {
            if (e.size() == 2) {
                Iterator<Event> ee = e.iterator();
                Event a = ee.next(), r = ee.next();
                if (a instanceof Absolute ^ r instanceof Absolute) {
                    //System.out.println("BEFORE: "); print();

                    if (r instanceof Absolute) {
                        //swap
                        Event x = a;
                        a = r;
                        r = x;
                    }
                    mergeNodes(r, a);
                    ee.remove(); //e.remove(r);

                    //System.out.println("AFTER: "); print(); System.out.println();
                }
            }
        }

    }

    private boolean solveDtAndOccIfConceptualizable(Term x, Term y, Predicate<Event> each) {
        if (y == null || x.equals(y) || !termsEvent(y))
            return true;

        if (y.hasXternal()) {
            if (!solveAll(y, each))
                return false;
        } else {
            if (!solveDtAndOccTop(y, each))
                return false;
        }

        return true;

    }

    /* solve xternal occurring at the root of a compound (without any internal xternal remaining) */
    private boolean solveDtAndOccTop(Term x, Predicate<Event> each) {
        if (!termsEvent(x)) return true;

        if (x.dt() == XTERNAL) {
            return solveDT(x, y -> !termsEvent(y.id) || solveOccurrence(y, each));
        } else {
            //dont solve if more specific dt solved further in previous solveDT call
            return solveOccurrence(x, each);
        }
    }


    /**
     * solves the start time for the given Unsolved event.  returns whether callee should continue iterating
     */
    private boolean solveOccurrence(Term x, Predicate<Event> each) {
        if (!termsEvent(x)) return true;

        return solveOccurrence(shadow(x), each);
    }

    final boolean bfsPush(Event root, Search<Event, TimeSpan> tv) {
        return bfsPush(List.of(root), tv);
    }

    private boolean bfsPush(Collection<Event> roots, Search<Event, TimeSpan> tv) {


        MetalBitSet created = null;
        {
            int n = 0;
            for (Event r : roots) {
                if (addNewNode(r)) {
                    if (created == null) {
                        created = MetalBitSet.bits(roots.size());
                    }
                    created.set(n);
                }
                n++;
            }
        }

        Queue<Pair<List<BooleanObjectPair<FromTo<Node<Event, nars.time.TimeSpan>, TimeSpan>>>, Node<Event, nars.time.TimeSpan>>> q = new ArrayDeque<>(roots.size() /* estimate TODO find good sizing heuristic */);

//        Iterable<Node<Event,TimeSpan>> rr = Iterables.transform(roots, r -> {
//            Node<Event, TimeSpan> n = node(r);
//            if (n == null) {
//                //virtual node
//                n = new AbstractNode<>(r) {
//                    @Override
//                    public Iterable edges(boolean in, boolean out) {
//                        if (out) {
//                            return ()->((TimeSolver)tv).dynamicLink(this);
//                        } else {
//                            return List.of();
//                        }
//                    }
//                };
//
//            }
//            return n;
//        });
//        boolean result = bfs(q, rr, tv);

        boolean result = bfs(roots, q, tv);

        if (created != null && result /* tail call optimization  - dont bother removing if we're done anyway */) {
            int m = 0;
            for (Event x : roots) {
                if (created.get(m++))
                    removeNode(x);
            }
        }

        return result;
    }

    private boolean solveOccurrence(Event x, Predicate<Event> each) {
        return solveExact(x, each) &&
                solveCrossTime(List.of(x), each) &&
                solveSelfLoop(x, each) &&
                solveLastResort(x, each);
    }

    /**
     * check for any self-loops and propagate forward and/or reverse
     */
    private boolean solveSelfLoop(Event x, Predicate<Event> each) {
        if (!solutions.isEmpty()) {
            Term t = x.id;
            /** clone the list because modifying solutions while iterating will cause infinite loop */
            return new FasterList<>(solutions.list).allSatisfy((s) -> {
                if (s instanceof Absolute && !(s.equals(x)) && s.id.equals(t)) {
                    for (Event e : byTerm.get(t)) {
                        {
                            //TODO shuffle found self-loops, there could be sevreal
                            Node<Event, TimeSpan> ne = node(e);
                            if (ne != null) {
                                for (FromTo<Node<Event, TimeSpan>, TimeSpan> ee : ne.edges(false, true)) {
                                    long dt = ee.id().dt;
                                    if (dt != 0 && dt != ETERNAL && dt != TIMELESS) {
                                        if (ee.loop()) {
//                                        if (random().nextBoolean())
//                                            dt = -dt;
                                            if (!each.test(((Absolute) s).shift(dt)))
                                                return false;
                                            if (!each.test(((Absolute) s).shift(-dt)))
                                                return false;

                                        }
                                    }
                                }
                            }

                        }
                    }
                }
                return true;
            });
        }
        return true;
    }

    private boolean solveLastResort(Event x, Predicate<Event> each) {
        return !(x instanceof Relative) || each.test(x);
    }

    private boolean solveCrossTime(Collection<Event> x, Predicate<Event> each) {
        return bfsPush(x, new CrossTimeSolver() {

            @Override
            protected boolean next(BooleanObjectPair<FromTo<Node<Event, TimeSpan>, TimeSpan>> move, Node<Event, TimeSpan> n) {

                Event end = n.id();

                if (!(end instanceof Absolute))
                    return true;

                long pathEndTime = end.start();


                long startTime, endTime;
                if (pathEndTime == ETERNAL) {

                    startTime = endTime = ETERNAL;

                } else {

                    long[] pt = pathTime(path);
                    if (pt == null || pt[0] == ETERNAL)
                        return true;

                    startTime = pathEndTime - (pt[0] /* pathDelta*/);
                    assert (startTime != TIMELESS);
                    endTime = startTime + pt[1]; /* pathRange */

                }

                return each.test(
                        event(pathStart(path).id().id, startTime, endTime, false)
                );
            }

        });
    }


    protected Random random() {
        return ThreadLocalRandom.current();
    }


    /**
     * absolutely specified event
     */

    public static class Absolute extends Event {
        static final long SAFETY_PAD = 32 * 1024;
        final long start;

        Absolute(Term t, long start, int hashCode) {
            super(t, hashCode);


            assert (start != TIMELESS);
            if (!(start == ETERNAL || start > ETERNAL + SAFETY_PAD))
                throw new MathArithmeticException();
            if (!(start < TIMELESS - SAFETY_PAD))
                throw new MathArithmeticException();

            this.start = start;
        }

        Absolute(Term t, long start) {
            this(t, start, hashCombine(t.hashCode(), start));
        }

        @Override
        public final long start() {
            return start;
        }

        public long end() {
            return start;
        }

        @Override
        public long dur() {
            return 0;
        }

//        /**
//         * contained within but not true if equal
//         */
//        public final boolean containedInButNotEqual(long cs, long ce) {
//            return containedIn(cs, ce) && (start != cs || end() != ce);
//        }

        final boolean containedIn(long cs, long ce) {
            return (cs <= start && ce >= end());
        }

        final boolean containsOrEquals(long cs, long ce) {
            return (cs >= start && ce <= end());
        }

//        public boolean intersectsWith(long cs, long ce) {
//            return Longerval.intersects(start, end(), cs, ce);
//        }


//        /**
//         * contains or is equal to
//         */
//        public boolean containsOrEqual(long cs, long ce) {
//            return (start <= cs && end() >= ce);
//        }

        @Nullable long[] unionIfIntersects(long start, long end) {
            long thisStart = this.start;

            long thisEnd = end();

            return Longerval.intersects(start, end, thisStart, thisEnd) ?
                    Longerval.unionArray(start, end, thisStart, thisEnd) :
                    null;
        }

        Event shift(long dt) {
            assert (dt != 0);
            if (this instanceof AbsoluteRange) {
                return new AbsoluteRange(id, start + dt, end() + dt);
            } else {
                return new Absolute(id, start + dt);
            }
        }
    }

    public static class AbsoluteRange extends Absolute {
        final long end;

        AbsoluteRange(Term t, long start, long end) {
            super(t, start, hashCombine(hashCombine(t.hashCode(), start), end));
            if (end <= start || start == ETERNAL || start == XTERNAL || end == XTERNAL)
                throw new RuntimeException("invalid AbsoluteRange start/end times: " + start + ".." + end);
            this.end = end;
        }

        public long end() {
            return end;
        }

        @Override
        public long dur() {
            return end - start;
        }
    }

    /**
     * TODO RelativeRange?
     */
    public static class Relative extends Event {

        Relative(Term id) {
            super(id, id.hashCode() /*hashCombine(id.hashCode(), TIMELESS)*/);
        }

        @Override
        public final long start() {
            return TIMELESS;
        }

        @Override
        public long end() {
            return TIMELESS;
        }

        @Override
        public long dur() {
            throw new UnsupportedOperationException();
            //return 0;
        }
    }

    /**
     * floating, but potentially related to one or more absolute event
     */

    abstract static class TimeSolver extends Search<Event, TimeSpan> {


    }

    private abstract class CrossTimeSolver extends TimeSolver {

        @Override
        protected Iterable<FromTo<Node<Event, nars.time.TimeSpan>, TimeSpan>> next(Node<Event, TimeSpan> n) {

            Iterable<FromTo<Node<Event, TimeSpan>, TimeSpan>> exist = n.edges(true, true);

            return Iterables.concat(exist, () -> {
                Collection<Event> ee = byTerm.get(n.id().id);
                if (!ee.isEmpty()) {

                    List<FromTo<Node<Event, TimeSpan>, TimeSpan>> dyn = null;

                    for (Event x : ee) {
                        Node<Event, TimeSpan> xx = node(x);
                        if (xx != null && xx != n && !log.hasVisited(xx)) {
                            if (dyn == null)
                                dyn = new FasterList<>(1);
                            dyn.add(new ImmutableDirectedEdge<>(n, TS_ZERO, xx));
                        }
                    }
                    if (dyn != null)
                        return dyn.iterator();
                }
                return Collections.emptyIterator();
            });


//            Iterator<Event> x = ee.iterator();
//            return
//                    Iterators.transform(
//                            Iterators.filter(Iterators.transform(
//                                    x,
//                                    TimeGraph.this::node),
//                                    e -> e != null && e != n && !log.hasVisited(e)),
//                            that -> new ImmutableDirectedEdge<>(n, TS_ZERO, that));

        }

    }

    /**
     * TODO fair sampling
     */
    @Deprecated
    private int choose(int[] subTimes) {
        if (subTimes == null)
            return DTERNAL;
        else {
            return subTimes[subTimes.length == 1 ? 0 : random().nextInt(subTimes.length)];
        }
    }

    /**
     * computes dt, the length of time spanned from start to the end of the given path [0],
     * and the range [1]
     */
    @Nullable
    private static long[] pathTime(List<BooleanObjectPair<FromTo<Node<Event, TimeSpan>, TimeSpan>>> path) {

        long durEventMin = Long.MAX_VALUE, durImplMin = Long.MAX_VALUE;

        //compute the maximum duration first
        int i = 0;
        for (BooleanObjectPair<FromTo<Node<Event, nars.time.TimeSpan>, TimeSpan>> span : path) {
            FromTo<Node<Event, nars.time.TimeSpan>, TimeSpan> event = span.getTwo();

            if (i++ == 0) {
                //first iter only
                Event e = event.from().id();
                if (e instanceof Absolute) {
                    long d = e.dur();
                    if (e.id.op() == IMPL) {
                        durImplMin = Math.min(durImplMin, d);
                    } else {
                        durEventMin = Math.min(durEventMin, d);
                    }
                }
            }

            {
                Event e = event.to().id();
                if (e instanceof Absolute) {
                    long d = e.dur();
                    if (e.id.op() == IMPL) {
                        durImplMin = Math.min(durImplMin, d);
                    } else {
                        durEventMin = Math.min(durEventMin, d);
                    }
                }
            }

        }

        boolean allImpl = durEventMin == Long.MAX_VALUE && durImplMin != Long.MAX_VALUE;


        long dt = 0, dur = allImpl ? durImplMin : durEventMin;

        for (BooleanObjectPair<FromTo<Node<Event, nars.time.TimeSpan>, TimeSpan>> span : path) {

            FromTo<Node<Event, nars.time.TimeSpan>, TimeSpan> event = span.getTwo();

            long spanDT = event.id().dt;

            assert (spanDT != TIMELESS);

            if (spanDT == ETERNAL) {
                dt = ETERNAL; //lock in eternal mode for the duration of the path, but still accumulate duration
            } else if (dt != ETERNAL && spanDT != 0) {
                dt += (spanDT) * (span.getOne() ? +1 : -1);
            }
        }

        if (dur == Long.MAX_VALUE)
            dur = 0; //all relative events, shrink to point

        return new long[]{dt, dur};
    }

}
