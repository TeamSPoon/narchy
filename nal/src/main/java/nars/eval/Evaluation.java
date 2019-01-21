package nars.eval;

import com.google.common.collect.Iterables;
import jcog.data.iterator.CartesianIterator;
import jcog.data.list.FasterList;
import jcog.data.set.ArrayHashSet;
import jcog.version.VersionMap;
import jcog.version.Versioning;
import nars.*;
import nars.op.mental.Inperience;
import nars.subterm.Subterms;
import nars.term.Functor;
import nars.term.Term;
import nars.term.Termlike;
import nars.term.atom.Atom;
import nars.term.atom.Bool;
import nars.term.util.transform.TermTransform;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import static nars.$.$$;
import static nars.Op.PROD;
import static nars.term.atom.Bool.Null;

public class Evaluation {

    private final Predicate<Term> each;



    private FasterList<Iterable<Predicate<VersionMap<Term, Term>>>> termutator = null;

    private Versioning v;

    private VersionMap<Term, Term> subst;


    @Nullable
    public static Evaluation eval(Term x, NAR nar, Predicate<Term> each) {
        return eval(x, nar::functor, each);
    }
    @Nullable
    public static Evaluation eval(Term x, Function<Atom, Functor> resolver, Predicate<Term> each) {
        return eval(x, true, false, resolver, each);
    }

//    @Nullable
//    public static Evaluation answer(Term x, NAR nar, Predicate<Term> each) {
//        Evaluator y = new FactualEvaluator(nar::functor, nar.facts(0.75f, true));
//        return y.eval(each, x);
//         : new FactualEvaluator(resolver, facts)
//        if (y instanceof FactualEvaluator) {
//            //filter true results
//            FactualEvaluator f = (FactualEvaluator) y;
////                Predicate<Term> ee = each;
////                each = (e) -> {
////                    switch (f.truth(e, null)) {
////                        case +1:
////                            //true
////                            break;
////                        case -1:
////                            e = e.neg();
////                            break;
////                        case 0:
////                            e = $.func(Inperience.wonder, e);
////                            break;
////                        default:
////                            throw new UnsupportedOperationException();
////                    }
////
////                    return ee.test(e);
////                };
//        }
//,
//        return eval(x, nar::functor,
//
//                each);
//    }

    /**
     *
     * @param x
     * @param resolver
     *
     * @param each
     * @return
     */
    @Nullable
    private static Evaluation eval(Term x, boolean includeTrues, boolean includeFalses, Function<Atom, Functor> resolver, Predicate<Term> each) {

        if (canEval(x)) {
            Evaluator y = new Evaluator(resolver);
            return y.eval(each, includeTrues, includeFalses, x);
        } else {
            each.test(x); //didnt need evaluating, just input
            return null;
        }
    }

    public Evaluation(Predicate<Term> each) {
        this.each = each;
    }

    private boolean termute(Evaluator e, Term y) {


        int before = v.size();

        if (termutator.size() == 1) {
            Iterable<Predicate<VersionMap<Term, Term>>> t = termutator.get(0);
            termutator.clear();
            for (Predicate tt : t) {
                if (tt.test(subst)) {
                    Term z = y.replace(subst);
                    if (z!=y) {
                        if (!eval(e, z)) //recurse
                            return false; //CUT
                    }
                    //if (z!=y) {
//                        if (!each.test(z)) { //TODO check for new solvable sub-components
//                            return false;
//                        }
                    //}
                }
                v.revert(before);
            }
        } else {
            CartesianIterator<Predicate>/*<VersionMap<Term,Term>>>*/ ci =
                    new CartesianIterator(
                            Predicate[]::new, termutator.toArray((IntFunction<Iterable[]>)(Iterable[]::new)));
            termutator.clear();
            nextProduct: while (ci.hasNext()) {

                v.revert(before);

                Predicate/*<VersionMap<Term,Term>>*/[] c = ci.next();

                for (Predicate<VersionMap<Term,Term>> cc : c) {
                    if (cc == null)
                        break; //null term list
                    if (!cc.test(subst))
                        continue nextProduct;
                }

                //all components applied successfully

                Term z = y.replace(subst);
                if (z!=y) {
//                    if (canEval(z)) { // && !(ez = e.clone().query(z)).isEmpty()) {
                        if (!eval(e, z)) //recurse
                            return false; //CUT
//                    } else {
//                        if (!each.test(z))
//                            return false; //CUT
//                    }

                }
            }
        }
        return true;
    }


    public boolean eval(Evaluator e, final Term x) {
        return eval(e, x, e.discover(x, this));
    }

    /** fails fast if no known functors apply */
    public boolean evalTry(Evaluator e, Term x) {
        ArrayHashSet<Term> d = e.discover(x, this);
        if (d==null && (termutator==null || termutator.isEmpty())) {
            each.test(x);
            return true; //early exit
        }
        return eval(e, x, d);
    }

    protected boolean eval(Evaluator e, final Term x, @Nullable ArrayHashSet<Term> operations) {

        Term y = x;

        if (operations != null) {

            Term prev;
            int vStart, tried, mutStart;
            main: //iterate until stable
            do {
                prev = y;
                Iterator<Term> ii = operations.iterator();
                vStart = now();
                mutStart = termutators();
                tried = 0;
                while (ii.hasNext()) {

                    Term a = ii.next();

                    boolean removeEntry, eval;

                    if (Functor.isFunc(a)) {

                        //still a functor, but different. update it

                        //run the functor resolver for any new functor terms which may have appeared
                        Term af = Functor.func(a);

                        removeEntry = false;

                        eval = true;

                        if (!(af instanceof Functor)) {
                            //try resolving
                            Term aa = e.transform(a);
                            if (aa == a) {
                                //no change. no such functor
                                removeEntry = true;
                                eval = false;
                            } else {
                                a = aa;
                            }
                        }

                    } else {
                        eval = false;
                        removeEntry = true;
                    }

                    Term z;
                    boolean substAdded, mutAdded;
                    if (!removeEntry && eval) {
                        Functor func = (Functor) a.sub(1);
                        Subterms args = a.sub(0).subterms();
                        if (canEval(args)) {
                            //evaluate subterms recursively
                            args = args.transformSubs(new TermTransform() {
                                @Override
                                public Term transform(Term x) {
                                    return Evaluation.solveFirst(x, (Function<Atom, Functor>) e.funcResolver);
                                }
                            }, PROD);
                        }

                        z = func.apply(this, args);
                        if (z == Null) {
                            return each.test(Null);
                        }
                        substAdded = now() != vStart;
                        mutAdded = mutStart != termutators();
                        if ((z == null && (substAdded || mutAdded)) || (z instanceof Bool)) {
                            removeEntry = true;
                        }

                    } else {
                        substAdded = mutAdded = false;
                        z = a;
                    }

                    if (removeEntry) {
                        ii.remove();
                    }


                    if ((z != null && z != a) || substAdded) {
                        tried++;


                        if (z != null) {
                            y = y.replace(a, z);
                        }
                        if (substAdded) {
                            y = y.replace(subst);
                        }

                        Term finalA = a;
                        operations.list.replaceAll(o -> {
                            Term p, q;
                            if (z != null) {
                                p = o.replace(finalA, z);
                                if (o != p && !Functor.isFunc(p))
                                    return Null;
                            } else
                                p = o;

                            if (substAdded) {
                                q = p.replace(subst);
                                if (p != q && !Functor.isFunc(q))
                                    return Null;
                            } else
                                q = p;

                            return q;
                        });


                        if (!y.op().conceptualizable || operations.isEmpty())
                            break main;
                        else {
                            break; //changed so start again
                        }

                    }


                }


            } while ((y != prev) || (tried > 0));
        }

        assert (y != null);

//        if (subst!=null)
//            y = y.replace(subst);

        //if termutators, collect all results. otherwise 'cur' is the only result to return
        if (!(y instanceof Bool)) {
            int ts = termutators();
            if (ts > 0) {
                return termute(e, y);
            } else {
                //Transformed Result (possibly same)
                if (!each.test(y))
                    return false;
            }
        } else {
            //Terminal Result
            return each.test(bool(x, (Bool)y));
        }
        return true;
    }

    protected Term bool(Term x, Bool b) {
        if (b == Bool.True) {
            return boolTrue(x);
        } else if ( b == Bool.False) {
            return boolFalse(x);
        } else {
            return Null;
        }
    }

    protected Term boolTrue(Term x) {
        return x;
    }

    protected Term boolFalse(Term x) {
        return x.neg();
    }

    private int termutators() {
        return termutator!=null ? termutator.size() : 0;
    }

    private int now() {
        return v!=null ? v.size() : -1;
    }

//    private Term rewrite(List<Term[]> rewrites, Term x) {
//        if (rewrites.isEmpty())
//            return x;
//        Term y = x;
//        //TODO compile these into one replacement transform
//        for (Term[] rw : rewrites)
//            y = y.replace(rw[0], rw[1]).replace(subst);
//        return y;
//    }


    /**
     * returns first result. returns null if no solutions
     */
    public static Term solveFirst(Term x, NAR n) {
        return solveFirst(x, n::functor);
    }

    public static Term solveFirst(Term x, Function<Atom,Functor> resolver) {
        Term[] y = new Term[1];
        Evaluation.eval(x, true, false, resolver, (what) -> {
            if (what instanceof Bool) {
                if (y[0]!=null)
                    return true; //ignore and continue try to find a non-bool solution
            }
            y[0] = what;
            return false;
        });
        return y[0];
    }

    @Deprecated public static FactualEvaluator query(String s, NAR n) throws Narsese.NarseseException {
        return query($.$(s), n);
    }
    @Deprecated private static FactualEvaluator query(Term x, NAR n) {
        FactualEvaluator f = new FactualEvaluator(n::functor, n.facts(0.75f, true));
        f.eval((y)->true,true, false,  x);
        return f;
    }

    public static Set<Term> queryAll(Term x, NAR n) {
        Set<Term> solutions = new ArrayHashSet(1);
        FactualEvaluator f = new FactualEvaluator(n::functor, n.facts(0.75f, true));
        f.eval((y)->{ solutions.add(y); return true; }, true, false, x);
        return solutions;
    }

    public static Set<Term> eval(String s, NAR n) {
        return eval($$(s), n);
    }

    public static Set<Term> eval(Term x, NAR n) {
        return eval(x, true, false, n);
    }

    public static Set<Term> eval(Term x, boolean includeTrues, boolean includeFalses, NAR n) {
        return eval(x, includeTrues, includeFalses, n::functor);
    }

    private static final class MyEvaluated extends UnifiedSet<Term> implements Predicate<Term> {
        protected MyEvaluated() {
            super(1, 0.99f);
        }

        @Override
        public boolean test(Term y) {
            if (y != Null)
                add(y);
            return true;
        }
    }

    /**
     * gathers results from one truth set, ex: +1 (true)
     * TODO add limit
     */
    public static Set<Term> eval(Term x, boolean includeTrues, boolean includeFalses, Function<Atom, Functor> resolver) {
        MyEvaluated ee = new MyEvaluated();

        Evaluation.eval(x, includeTrues, includeFalses, resolver, ee);

        if (ee.isEmpty()) {
            //java.util.Set.of($.func(Inperience.wonder, x))
            return Set.of();
        } else
            return ee;
    }


    private void ensureReady() {
        if (v == null) {
            v = new Versioning<>(Param.UnificationStackMax, Param.EVALUATION_TTL);
            subst = new VersionMap<>(v, 1);
            termutator = new FasterList(1);
        }
    }


    private Evaluation clear() {
        if (v != null) {
            termutator.clear();
            v.clear();
            subst.clear();
        }
        return this;
    }



    /**
     * assign 1 variable
     * returns false if it could not be assigned (enabling callee fast-fail)
     */
    public boolean is(Term x, Term y) {
        ensureReady();
        return subst.set(x, y);
    }

    /**
     * assign 2-variables at once.
     * returns false if it could not be assigned (enabling callee fast-fail)
     */
    public boolean is(Term x, Term xx, Term y, Term yy) {
        ensureReady();
        return subst.set(x, xx) && subst.set(y, yy);
    }

    /**
     * 2-ary AND
     */
    public static Predicate<VersionMap<Term, Term>> assign(Term x, Term xx, Term y, Term yy) {
        return m -> m.set(x, xx) && m.set(y, yy);
    }
    public static Predicate<VersionMap<Term, Term>> assign(Term x, Term y) {
        return (subst) -> subst.set(x, y);
    }


    /**
     * OR, forked
     * TODO limit when # termutators exceed limit
     */
    public void canBe(Iterable<Predicate<VersionMap<Term, Term>>> x) {
        ensureReady();
        termutator.add(x);
    }

    public void canBe(Predicate<VersionMap<Term, Term>> x) {
        ensureReady();
        termutator.add(List.of(x));
    }

    public void canBe(Term x, Term y) {
        if (x.equals(y))
            return;
        canBe(assign(x, y));
    }
    public void canBe(Term x, Iterable<Term> y) {
        canBe((Iterable<Predicate<VersionMap<Term,Term>>>)Iterables.transform(y, yy -> assign(x, yy)));
    }

    public void canBe(Term a, Term b, Term x, Term y) {
        if (x.equals(y)) {
            canBe(assign(a, b));
        } else if (a.equals(b)) {
            canBe(assign(x,y));
        } else {
            canBe(assign(a, b, x, y));
        }
    }

    public static boolean canEval(Termlike x) {
        return x.hasAll(Op.FuncBits);
    }



}
