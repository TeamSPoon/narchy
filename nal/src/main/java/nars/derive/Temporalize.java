package nars.derive;

import jcog.list.FasterList;
import jcog.random.XorShift128PlusRandom;
import nars.$;
import nars.Op;
import nars.Task;
import nars.control.Derivation;
import nars.term.Compound;
import nars.term.Term;
import nars.term.container.TermContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static nars.Op.CONJ;
import static nars.Op.NEG;
import static nars.time.Tense.*;

/**
 * set missing temporal relations in a derivation using constraint solver
 *
 * @see http://choco-solver.readthedocs.io/en/latest/1_overview.html#directly
 */
public class Temporalize {

    final static Logger logger = LoggerFactory.getLogger(Temporalize.class);

    /**
     * constraint graph (lazily constructed)
     */
    final Map<Term, FasterList<Event>> constraints = new HashMap();
    final Random random;

    /**
     * for testing
     */
    protected Temporalize() {
        this(new XorShift128PlusRandom(1));
    }

    public Temporalize(Random random) {
        this.random = random;
    }

    abstract static class Event implements Comparable<Event> {

        public final Term term;

        Event(Term term) {
            this.term = term;
        }

        abstract public Time start(HashMap<Term, Time> trail);

        abstract public Time end(HashMap<Term, Time> trail);

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Event)) return false;

            Event event = (Event) o;

            return term.equals(event.term);
        }

        @Override
        public int hashCode() {
            return term.hashCode();
        }

        /**
         * return a new instance with the term negated
         */
        abstract public Event neg();

        public Event neg(boolean isNeg) {
            return isNeg ? neg() : this;
        }

        @Override
        public int compareTo(@NotNull Temporalize.Event o) {
            if (this == o) return 0;
            else {
                if (getClass() == o.getClass()) {
                    //same class, rank by term volume
                    if (this instanceof RelativeEvent) {
                        Term x = ((RelativeEvent) this).rel.term();
                        Term y = ((RelativeEvent) o).rel.term();
                        int vc = Integer.compare(x.volume(), y.volume());
                        if (vc == 0) {
                            return x.compareTo(y);
                        } else {
                            return vc;
                        }
                    }
                }

                if (this instanceof AbsoluteEvent)
                    return -1;
                else
                    return +1;

            }
        }

        abstract public void apply(HashMap<Term, Time> trail);
    }

    static int dt(Event a, Event b, HashMap<Term, Time> trail) {

        if (a instanceof AbsoluteEvent || b instanceof AbsoluteEvent) {
            //at least one or both are absolute, forming a valid temporal grounding
            return dt(a.start(trail), b.start(trail));
            //return dt(a.start(times), b.end(times));
        } else if (a instanceof RelativeEvent && b instanceof RelativeEvent) {
            RelativeEvent ra = (RelativeEvent) a;
            RelativeEvent rb = (RelativeEvent) b;
            if (ra.rel.equals(rb.rel)) {
                //easy case
                return rb.end - ra.start;
            }
            if (ra.rel.equals(rb.term) && rb.rel.equals(ra.term)) {
                //circular dependency: choose either, or average if they are opposite polarity
                int forward = -ra.start;
                int reverse = rb.start;
                if (Math.signum(forward) == Math.signum(reverse)) {
                    return (forward + reverse) / 2;
                }
            }
        }

        return XTERNAL;
    }

    static int dt(Time a, Time b) {

        assert (a.base != XTERNAL);
        assert (b.base != XTERNAL);

        if (a.base == ETERNAL && b.base == ETERNAL) {
            return b.offset - a.offset; //relative offsets within an eternal context
        } else if (a.base != ETERNAL && b.base != ETERNAL) {
            return (int) (b.abs() - a.abs()); //TODO check for numeric precision loss
        } else {
            throw new UnsupportedOperationException(a + " .. " + b); //maybe just return DTERNAL
        }
    }

    static class AbsoluteEvent extends Event {

        public final long start, end;

        AbsoluteEvent(Term term, long start, long end) {
            super(term);

            assert ((start == ETERNAL && end == ETERNAL) || (start != ETERNAL && end != ETERNAL)) :
                    "invalid semi-eternalization: " + start + " " + end;

            if (start <= end) {
                this.start = start;
                this.end = end;
            } else {
                this.start = end;
                this.end = start;
            }
        }

        @Override
        public void apply(HashMap<Term, Time> trail) {
            trail.put(term, Time.the(start, 0)); //direct set
        }

        @Override
        public Event neg() {
            return new AbsoluteEvent($.neg(term), start, end);
        }

        @Override
        public Time start(@Nullable HashMap<Term, Time> ignored) {
//            if(ignored!=null) {
//                Time existingTime = ignored.get(term);
//                if (existingTime != null) {
//                    System.out.println("conflict?: " + existingTime + " " + Time.the(start, 0));
//                }
//            }
            return Time.the(start, 0);
        }

        @Override
        public Time end(HashMap<Term, Time> ignored) {
            return Time.the(end, XTERNAL);
        }

        @Override
        public String toString() {
            if (start != ETERNAL) {
                if (start != end)
                    return term + ("@[" + timeStr(start) + ".." + timeStr(end)) + "]";
                else
                    return term + "@" + timeStr(start);
            } else
                return term + "@ETE";
        }

    }

    public class SolutionEvent extends AbsoluteEvent {

        SolutionEvent(Term term, long start) {
            super(term, start, start != ETERNAL ? start + term.dtRange() : ETERNAL);
        }

//        SolutionEvent(Term unknown) {
//            super(unknown, XTERNAL, XTERNAL);
//        }
    }

    /**
     * used for preserving an offset within an eternal context
     */
    static class Time {

        //        public static Time Unknown = new Time(ETERNAL, XTERNAL);
        public final long base;
        public final int offset;

        @Override
        public String toString() {
            return str(base) + "|" + str(offset);
        }

        static String str(int offset) {
            if (offset == XTERNAL)
                return "+-";
            else
                return Integer.toString(offset);
        }

        static String str(long base) {
            if (base == ETERNAL)
                return "ETE";
            else
                return Long.toString(base);
        }


        static Time the(long base, int offset) {
//            if (base == ETERNAL && offset == XTERNAL)
//                return Unknown;
//            else {

            if (base != ETERNAL && offset != DTERNAL && offset != XTERNAL)
                return new Time(base + offset, 0); //direct absolute
            else
                return new Time(base, offset);
//            }
        }

        private Time(long base, int offset) {
            this.base = base;
            this.offset = offset;
        }


        public Time add(int offset) {

            if (offset == 0)
                return this;

            assert (this.offset != DTERNAL && offset != DTERNAL);

            if (this.offset == XTERNAL)
                return Time.the(base, offset); //set initial dt
            else
                return Time.the(base, this.offset + offset);
        }

        public long abs() {
//            if (base == ETERNAL) {
//                return ETERNAL;
//            }
//
//            assert(offset!=XTERNAL);
//            assert(offset!=DTERNAL);
//            return base + offset;
            if (base != ETERNAL && offset != DTERNAL && offset != XTERNAL)
                return base + offset;
            else
                return ETERNAL;
        }
    }

    class RelativeEvent extends Event {
        private final Term rel;
        private final int start, end;

        public RelativeEvent(Term term, Term relativeTo, int start) {
            this(term, relativeTo, start, start + term.dtRange());
        }

        public RelativeEvent(Term term, Term relativeTo, int start, int end) {
            super(term);
            //assert (!term.equals(relativeTo));
            this.rel = relativeTo;
            this.start = start;
            this.end = end;
        }

        @Override
        public void apply(HashMap<Term, Time> trail) {
            Time t = resolve(this.start, trail);
            if (t != null)
                trail.putIfAbsent(term, t); //direct set
        }

        @Override
        public Event neg() {
            return new RelativeEvent($.neg(term), rel, start, end);
        }

        @Override
        @Nullable
        public Time start(HashMap<Term, Time> trail) {
            return resolve(this.start, trail);
        }

        @Override
        public Time end(HashMap<Term, Time> trail) {
            return resolve(this.end, trail);
        }

        @Nullable
        protected Time resolve(int offset, HashMap<Term, Time> trail) {

            Time rt = solveTime(rel, trail);
            if (rt != null) {
                return rt.add(offset);
            } else {
                return null;
            }
        }

        @Override
        public String toString() {
            if (start != end) {
                return term + "@[" + timeStr(start) + ".." + timeStr(end) + "]->" + rel;
            } else {
                return term + "@" + timeStr(start) + "->" + rel;
            }
        }

    }


    protected void print() {

        constraints.forEach((k, v) -> {
            for (Event vv : v)
                System.out.println(k + " " + vv);
        });

        System.out.println();
    }

//    void add(Event v, MutableValueGraph<Event, Integer> g) {
//
////        if (g instanceof RelativeEvent) {
////            add((((RelativeEvent) v).rel), g); //add relation first
////        }
//
//        if (!g.addNode(v))
//            return; //already added
//
//        Term vt = v.term;
//        if (vt.op().temporal) {
//            int d = vt.dt();
//            if (d != XTERNAL) {
//                int s = vt.size();
//                if (s == 2) {
//                    Term a = vt.sub(0);
//                    Term b = vt.sub(1);
//                    if (!a.equals(b)) {
//                        Event ae = events.get(a);
//                        if (ae != null) {
//                            Event be = events.get(b);
//                            if (be != null) {
//                                int at = vt.subtermTime(a);
//                                int bt = vt.subtermTime(b);
//
//                                Event from, to;
//                                if (ae.term.compareTo(be.term) < 0) {
//                                    from = ae; to = be;
//                                } else {
//                                    from = be; to = ae;
//                                }
//
//                                if (null == g.edgeValueOrDefault(from, to, null)) {
//                                    int delta;
//                                    if (from == ae) {
//                                        delta = bt - at;
//                                    } else {
//                                        delta = at - bt;
//                                    }
//                                    g.putEdgeValue(from, to, delta);
//                                }
//                            }
//                        }
//                    }
//
//                } else if (s > 2) {
//                    //connect all the edges
//                }
//            }
//        }
//
//
//    }


    @Nullable
    public static Term solve(@NotNull Derivation d, Term pattern, long[] occ) {



        /*
        unknowns to solve otherwise the result is impossible:
            - derived task start time
            - derived task end time
            - dt intervals for any XTERNAL appearing in the input term
        knowns:
            - for each task and optional belief in the derived premise:
                - start/end time of the task
                - start/end time of any contained events
            - possible relations between events referred to in the conclusion that
                appear in the premise.  this may be partial due to variable introduction
                and other reductions. an attempt can be made to back-solve the result.
                if that fails, a heuristic could decide the match. in the worst case,
                the derivation will not be temporalizable and this method returns null.
        */
        Task task = d.task;
        Task belief = d.belief;


        Temporalize model = new Temporalize(d.random);

        model.know(task, d);
        if (belief != null)
            model.know(belief, d);

        HashMap<Term, Temporalize.Time> times = new HashMap();
        Event e = model.solve(pattern, times);
        if (e != null) {
            if (e instanceof AbsoluteEvent) {
                AbsoluteEvent a = (AbsoluteEvent) e; //faster, preferred since pre-calculated
                occ[0] = a.start;
                occ[1] = a.end;
            } else {
                occ[0] = e.start(times).abs();
                occ[1] = e.end(times).abs();
            }
            return e.term;
        }
        return null;
    }

    public void know(Task task, Derivation d) {
        //assert (task.end() == task.start() + task.dtRange());
        Term taskTerm = task.term();
        AbsoluteEvent root = new AbsoluteEvent(taskTerm, task.start(), task.end());
        know(root, taskTerm, 0, taskTerm.dtRange());

        Term t2 = d.transform(taskTerm);
        if (!t2.equals(taskTerm)) {
            know(root, t2, 0, t2.dtRange());
        }
    }


    /**
     * convenience method for testing: assumes start offset of zero, and dtRange taken from term
     */
    Temporalize knowTerm(Term term, long when) {
        Event e;
        //if (when != ETERNAL) {
        e = new AbsoluteEvent(term, when, when != ETERNAL ? when + term.dtRange() : ETERNAL);
//        } else {
//            e = null; //assume eternal otherwise
//        }

        know(e, term, 0, term.dtRange());
        return this;
    }


    /**
     * recursively calculates the start and end time of all contained events within a term
     *
     * @param occ superterm occurrence, may be ETERNAL
     */
    void know(@Nullable Event parent, Term term, int start, int end) {

        //TODO support multiple but different occurrences  of the same event term within the same supercompound
        if (parent == null || parent.term != term) {
            List<Event> exist = constraints.get(term);
            if (exist != null)
                return;
        }

        if (parent != null)
            add(parent, term, start, end);

        if (term instanceof Compound) {
            Compound c = (Compound) term;
            Op o = c.op();
            if (o.temporal) {
                int dt = c.dt();

                if (dt == XTERNAL) {

                    //TODO UNKNOWN TO SOLVE FOR
                    //throw new RuntimeException("no unknowns may be added during this phase");

                } else {
                    if (dt == DTERNAL)
                        dt = 0;

                    TermContainer tt = c.subterms();
                    if (dt < 0 && o.commutative) {
                        dt = -dt;
                        tt = c.reverse();
                    }

                    int t = start;

                    int l = tt.size();

                    //System.out.println(tt + " presubs " + t + "..reverse=" + reverse);
                    int lastSubStart = DTERNAL;
                    for (int i = 0; (i < l); i++) {

                        Term st = tt.sub(i);
                        int sdt = st.dtRange();

                        int subStart = t;
                        int subEnd = t + sdt;
                        //System.out.println("\t" + st + " sub(" + i + ") " + subStart + ".." + subEnd);
                        know(parent, st, subStart, subEnd);

                        t = subEnd; //the duration of the event

                        if (i < l - 1)
                            t += dt; //the dt offset (doesnt apply to the first term which is early/left-aligned)

                        if (i > 0) {
                            //crosslink adjacent subterms
                            add(tt.sub(i - 1), new RelativeEvent(tt.sub(i - 1), tt.sub(i), lastSubStart - subStart));
                            add(tt.sub(i), new RelativeEvent(tt.sub(i), tt.sub(i - 1), subStart - lastSubStart));
                        }
                        lastSubStart = subStart;
                    }


//                    //for conjunctions: by the end of the iteration we should be at the exact end of the interval
//                    if (o == CONJ) {
//                        int expectedEnd = end;
////                        if (t!=expectedEnd) {
////                            throw new RuntimeException(term + " with dtRange=" + term.dtRange() + " mis-aligned: " + start + "," + end + " but t=" + t);
////                        }
//                        assert (t == expectedEnd) : term + " with dtRange=" + term.dtRange() + " mis-aligned: " + start + "," + end + " but t=" + t;
//                    }

                    //for others: they are "pointers" which relate time points but do not define durations

                }

            } else {
                //all these subterms will share their supercompounds time
//                if (o.isSet() ) {
//                    c.subterms().forEach(s -> know(root, s, start, end));
//                }

                /*c.subterms().recurseTerms((s) -> {
                    know(root, s, start, end);
                });*/
            }
        }

    }

    Event add(@NotNull Temporalize.Event root, Term term, int start, int end) {
        Event event;

        if (term.equals(root.term)) {
            event = root;
        } else {
            Time occ = root.start(null);
            assert (occ.base != XTERNAL);
            event = (occ.base != ETERNAL ?
                    new AbsoluteEvent(term, occ.abs() + start, occ.abs() + end) :
                    new RelativeEvent(term, root.term, start, end)
            );
        }

        add(term, event);
        return event;
    }


    void add(Term term, Event event) {
        FasterList<Event> l = constraints.computeIfAbsent(term, (t) -> new FasterList<>());
        l.add(event);
        if (l.size() > 1) {
            l.sortThis();
        }
    }

    Event solve(Term target) {
        return solve(target, new HashMap<>(target.volume()));
    }

    Event solve(Term target, HashMap<Term, Time> times) {
        boolean isNeg = target.op() == NEG;
        if (isNeg)
            target = target.unneg();

        Event known = solveEvent(target, times);
        if (known != null)
            return known.neg(isNeg);

        if (target instanceof Compound) {

            Compound c = (Compound) target;
            Op o = c.op();
            if (o.temporal) {
                int dt = c.dt();

                if (dt == XTERNAL) {
                    TermContainer tt = c.subterms();

                    if (tt.size() == 2) {

                        Event ea, eb;
                        if (random.nextBoolean()) {
                            //forward order: sub 0 first
                            ea = solveSub(times, tt, 0);
                            if (ea == null) return null;
                            eb = solveSub(times, tt, 1);
                            if (eb == null) return null;
                        } else {
                            //reverse order: sub 1 first
                            eb = solveSub(times, tt, 1);
                            if (eb == null) return null;
                            ea = solveSub(times, tt, 0);
                            if (ea == null) return null;
                        }

                        Term a = ea.term;
                        Time at = ea.start(times);

                        if (at != null) {

                            Term b = eb.term;
                            Time bt = eb.start(times);

                            if (bt != null) {

                                try {
                                    if (o == CONJ && (a.op() == CONJ || b.op() == CONJ)) {
                                        //conjunction merge, since the results could overlap
                                        //either a or b, or both are conjunctions. and the result will be conjunction

                                        long ata = at.abs();
                                        long bta = bt.abs();
                                        if (ata == ETERNAL && bta == ETERNAL) {
                                            ata = at.offset;
                                            bta = bt.offset + a.dtRange();
                                        } else if (ata == ETERNAL ^ bta == ETERNAL) {
                                            return null; //one is eternal the other isn't
                                        }
                                        Term newTerm = $.negIf(Op.conjMerge(a, ata, b, bta), isNeg);
                                        long start = Math.min(at.abs(), bt.abs());
                                        Event e = new SolutionEvent(newTerm, start);
                                        return e;
                                    } else {
                                        int sd = dt(ea, eb, times);
//                                        if (o == CONJ && sd != DTERNAL && sd != XTERNAL) {
//                                            sd -= a.dtRange(); sd -= b.dtRange();
//                                        }
                                        if (sd != XTERNAL) {

                                            Term newTerm = $.negIf(o.the(sd, a, b), isNeg);

                                            long start = o == CONJ ? Math.min(at.abs(), bt.abs()) : at.abs();

                                            Event e = new SolutionEvent(newTerm, start);
                                            return e;
                                        }
                                    }
                                } catch (UnsupportedOperationException e) {
                                    logger.warn("temporalization solution: {}", e.getMessage());
                                    return null; //TODO
                                }
                            }
                        }
                    } else {
                        logger.warn("TODO unsupported xternal: " + target);
                    }
                }
            }
        }

        return null;
    }

    private Event solveSub(HashMap<Term, Time> times, TermContainer tt, int subterm) {
        Term a = tt.sub(subterm);
        return solve(a, times);
    }

    @Nullable
    private Time solveTime(Term target, HashMap<Term, Time> trail) {


        Time existing = trail.get(target);
        if (existing != null) {
//            if (existing == Unknown)
//                return null;
//            else
            return existing;
        }
        if (trail.containsKey(target))
            return null; //being procesed beneath in the stack

//        System.out.println(target + " ? " + trail);

        trail.put(target, null); //placeholder to prevent infinite loop

        Event e = solveEvent(target, trail);
        Time t = e.start(trail);
        if (t != null) {
            trail.put(target, t);
        } else {
            trail.remove(target); //remove the null plaeholder
        }

        return t;
    }


    private Event solveEvent(Term target, HashMap<Term, Time> trail) {

        List<Event> ea = constraints.get(target);
        if (ea == null)
            return null; //no idea how to solve that term

        int eas = ea.size();
        assert (eas > 0);


        for (int i = 0, eaSize = eas; i < eaSize; i++) {
            Event x = ea.get(i);
            Time xs = x.start(trail);
            if (xs != null) {
                return x;
            }
//                if (xs != Unknown) {
            //System.out.println(target + " @ " + xs + " " + trail);
//                    return x;
//                }
        }

        return null;
    }

    @Override
    public String toString() {
        return constraints.values().stream().flatMap(Collection::stream).map(Object::toString).collect(Collectors.joining(","));
    }


    static String timeStr(long when) {
        return when != ETERNAL ? Long.toString(when) : "ETE";
    }

    static String timeStr(int when) {
        return when != DTERNAL ? (when != XTERNAL ? Integer.toString(when) : "?") : "DTE";
    }
}