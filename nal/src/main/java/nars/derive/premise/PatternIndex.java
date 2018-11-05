package nars.derive.premise;

import jcog.TODO;
import jcog.WTF;
import nars.Builtin;
import nars.NAR;
import nars.Op;
import nars.index.concept.MapConceptIndex;
import nars.subterm.BiSubterm;
import nars.subterm.Subterms;
import nars.term.Variable;
import nars.term.*;
import nars.term.atom.Atom;
import nars.term.atom.Atomic;
import nars.term.compound.CachedCompound;
import nars.term.util.transform.Retemporalize;
import nars.term.util.transform.TermTransform;
import nars.term.util.transform.VariableNormalization;
import nars.unify.Unify;
import nars.unify.match.Ellipsis;
import nars.unify.match.EllipsisMatch;
import nars.unify.match.Ellipsislike;
import nars.unify.mutate.Choose1;
import nars.unify.mutate.Choose2;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.jetbrains.annotations.Nullable;

import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;

import static nars.Op.*;
import static nars.time.Tense.XTERNAL;
import static nars.unify.match.Ellipsis.firstEllipsis;

/**
 * Index which specifically holds the term components of a deriver ruleset.
 */
public class PatternIndex extends MapConceptIndex {

    //final Map<InternedSubterms, Subterms> subterms = new HashMap<>(1024);
    //private final Map<Term, PrediTerm<Derivation>> pred = new HashMap<>(1024);


    public PatternIndex() {
        super(new ConcurrentHashMap<>(1024));
    }

    public PatternIndex(NAR nar) {
        this();
        this.nar = nar;
    }

    /*@NotNull*/
    private static PremisePatternCompound ellipsis(/*@NotNull*/ Compound seed, /*@NotNull*/ Subterms v, /*@NotNull*/ Ellipsis e) {
        Op op = seed.op();

        boolean commutative = (/*!ellipsisTransform && */op.commutative);

        if (commutative) {
            return new PremisePatternCompound.PremisePatternCompoundWithEllipsisCommutive(seed.op(), seed.dt(), e, v);
        } else {
            return PremisePatternCompound.PremisePatternCompoundWithEllipsisLinear.the(seed.op(), seed.dt(), e, v);
        }

    }

    @SuppressWarnings("Java8MapApi")
    @Override
    public Term get(/*@NotNull*/ Term x, boolean createIfMissing) {
        //return x.term();
        if (x.op()==NEG)
            return get(x.unneg(), createIfMissing).neg();

        if (!x.op().conceptualizable)
            return x;


        Termed y = concepts.get(x);
        if (y == null) {
            if (nar != null && x.op() == ATOM) {

                Termed xx = nar.concepts.get(x, false);
                if (xx != null) {
                    concepts.put(xx.term(), xx);
                    return (Term)xx;
                }
            }

            Term yy = patternify(x);
            concepts.put(yy, yy);
            return yy;
        } else {
            return (Term)y;
        }
    }

    public static Term patternify(Term x) {
        if (x instanceof Compound)
            return Ellipsify.transformCompound((Compound) x);
        return x;
    }


    public /*@NotNull*/ Term rule(Term x) {
        return get(new PremiseRuleNormalization().transform(x), true).term();
    }

//    public final PrediTerm<Derivation> intern(@Nullable PrediTerm<Derivation> x) {
//        if (x == null)
//            return null;
//        PrediTerm<Derivation> y = pred.putIfAbsent(x.term(), x);
//        return y != null ? y : x;
//    }


    public final Term intern(Term x) {
        return (Term) get(x, true); //.term();
    }

    public static final class PremiseRuleNormalization extends VariableNormalization {



        @Override
        public Term transform(Term x) {
            /** process completely to resolve built-in functors,
             * to override VariableNormalization's override */
            //return TermTransform.NegObliviousTermTransform.super.transform(x);
            return (x instanceof Compound) ?
                    transformCompound((Compound) x)
                    :
                    transformAtomic((Atomic) x);
        }

        @Override
        protected Term transformNonNegCompound(Compound x) {
            /** process completely to resolve built-in functors,
             * to override VariableNormalization's override */
            return transformCompound(x, x.op(), x.dt());
        }


        @Override
        public Term transformAtomic(Atomic x) {
            if (x instanceof Atom) {
                Functor f = Builtin.functor(x);
                return f != null ? f : x;
            }
            return super.transformAtomic(x);
        }

        /*@NotNull*/
        @Override
        protected Variable newVariable(/*@NotNull*/ Variable x) {


            if (x instanceof Ellipsis.EllipsisPrototype) {
                return Ellipsis.EllipsisPrototype.make((byte) count,
                        ((Ellipsis.EllipsisPrototype) x).minArity);
            } else if (x instanceof Ellipsis) {
                return x;


            } /*else if (v instanceof GenericVariable) {
                return ((GenericVariable) v).normalize(actualSerial); 
            } else {
                return v(v.op(), actualSerial);
            }*/
            return super.newVariable(x);
        }


    }

    /**
     * seems used only if op==CONJ
     */
    @Deprecated
    abstract public static class PremisePatternCompound extends CachedCompound.TemporalCachedCompound {


        PremisePatternCompound(/*@NotNull*/ Op op, int dt, Subterms subterms) {
            super(op, dt, subterms);

        }


        @Override
        public final boolean the() {
            return false;
        }

        public abstract static class PremisePatternCompoundWithEllipsis extends PremisePatternCompound {

            final Ellipsis ellipsis;
//            private final int subtermStructure;


            PremisePatternCompoundWithEllipsis(/*@NotNull*/ Op seed, int dt, Ellipsis ellipsis, Subterms subterms) {
                super(seed, dt, subterms);

//                this.subtermStructure = subterms.structure();
                this.ellipsis = ellipsis;

            }

            abstract protected boolean matchEllipsis(Term y, Unify subst);

            @Override
            public final boolean unifySubterms(Term y, Unify u) {

                if (op()!=y.op()) // && !(op()==CONJ && subs()==1))
                    return false;

                if (!Subterms.possiblyUnifiable(subterms(), y.subterms(), u))
                    return false;
//                if (!Terms.commonStructureTest(subtermStructure, y.subterms(), u))
//                    return false;

                return matchEllipsis(y, u);
            }
        }


        public static class PremisePatternCompoundWithEllipsisLinear extends PremisePatternCompoundWithEllipsis {

            public static PremisePatternCompoundWithEllipsisLinear the( Op op, int dt, Ellipsis ellipsis, Subterms subterms) {
                if (op.statement) {
                    //HACK
                    Term x = subterms.sub(0);
                    Term y = subterms.sub(1);
                    if (x instanceof Ellipsislike) {
                        //raw ellipsis, the conjunction got removed somewhere. HACK re-add it
                        x = CONJ.the(x);
                    }
                    if (y instanceof Ellipsislike) {
                        //raw ellipsis, the conjunction got removed somewhere. HACK re-add it
                        y = CONJ.the(y);
                    }
                    subterms = new BiSubterm(x, y); //avoid interning
                }
                return new PremisePatternCompoundWithEllipsisLinear(op, dt, ellipsis, subterms);
            }

            private PremisePatternCompoundWithEllipsisLinear(/*@NotNull*/ Op op, int dt, Ellipsis ellipsis, Subterms subterms) {
                super(op, dt, ellipsis, subterms);
                if (op.statement && subterms.OR(x -> x instanceof Ellipsislike))
                    throw new WTF("raw ellipsis subj/pred makes no sense here");
            }

            /**
             * non-commutive compound match
             * X will contain at least one ellipsis
             * <p>
             * match subterms in sequence
             * <p>
             * WARNING this implementation only works if there is one ellipse in the subterms
             * this is not tested for either
             */
            @Override
            protected boolean matchEllipsis(Term y, Unify u) {
                Subterms Y = y.subterms();
                int xi = 0, yi = 0;
                int xsize = subs();
                int ysize = Y.subs();


                while (xi < xsize) {
                    Term x = sub(xi++);

                    if (x instanceof Ellipsis) {
                        int available = ysize - yi;

                        Term xResolved = u.resolve(x);
                        if (xResolved == x) {


                            if (xi == xsize) {
                                //the ellipsis is at the right edge so capture the remainder
                                if (!ellipsis.validSize(available))
                                    return false;

                                return ellipsis.unify(EllipsisMatch.matched(Y, yi, yi + available), u);

                            } else {
                                //TODO ellipsis is in the center
                                throw new TODO();
                            }
                        } else {


                            if (xResolved instanceof EllipsisMatch) {
                                EllipsisMatch xe = (EllipsisMatch) xResolved;
                                if (!xe.linearMatch(Y, yi, u))
                                    return false;
                                yi += xe.subs();
                            } else {

                                if (!sub(yi).unify(xResolved, u))
                                    yi++;
                            }
                        }


                    } else {
                        if (ysize <= yi || !x.unify(Y.sub(yi++), u))
                            return false;
                    }
                }

                return true;
            }


        }


        public static final class PremisePatternCompoundWithEllipsisCommutive extends PremisePatternCompoundWithEllipsis {


            public PremisePatternCompoundWithEllipsisCommutive(Op op, int dt, Ellipsis ellipsis, Subterms subterms) {
                super(op, dt, ellipsis, subterms);
                assert (op != CONJ || dt == XTERNAL); //CONJ always XTERNAL
            }

            /**
             * commutive compound match: Y into X which contains one ellipsis
             * <p>
             * X pattern contains:
             * <p>
             * one unmatched ellipsis (identified)
             * <p>
             * <p>
             * zero or more "constant" (non-pattern var) terms
             * all of which Y must contain
             * <p>
             * zero or more (non-ellipsis) pattern variables,
             * each of which may be matched or not.
             * matched variables whose resolved values that Y must contain
             * unmatched variables determine the amount of permutations/combinations:
             * <p>
             * if the number of matches available to the ellipse is incompatible with the ellipse requirements, fail
             * <p>
             * (total eligible terms) Choose (total - #normal variables)
             * these are then matched in revertable frames.
             * <p>
             * *        proceed to collect the remaining zero or more terms as the ellipse's match using a predicate filter
             *
             * @param y the compound being matched to this
             */
            @Override
            protected boolean matchEllipsis(Term Y, Unify u) {
                if (Y.op().temporal && (dt != XTERNAL) && !Y.isCommutative())
                    throw new TODO();

                Subterms y = Y.subterms();


                MutableSet<Term> xFixed = new UnifiedSet(0);


                Ellipsis ellipsis = this.ellipsis;

//                @Nullable Versioned<MatchConstraint> uc = u.constraints(ellipsis);

                SortedSet<Term> yFree =
                        //uc==null ? y.toSetSorted() : y.toSetSorted(yy -> MatchConstraint.valid(yy, uc, u));
                        y.toSetSorted();

                Subterms ss = subterms();
                int s = ss.subs();

                for (int k = 0; k < s; k++) {


                    Term x = ss.sub(k);

                    if (x.equals(ellipsis)) {
                        Term v = u.resolve(x);
                        if (v != x) {
                            if (v instanceof EllipsisMatch) {
                                if (!((EllipsisMatch) v).rematch(y, yFree))
                                    return false;
                                ellipsis = null;
                            } else {

                                if (u.constant(v) && !yFree.remove(v))
                                    return false;
                            }
                        }

                        continue;
                    }

                    
                    
                    
                    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    

                    /*if (v instanceof EllipsisMatch) {

                        
                        if (!((EllipsisMatch) v).addWhileMatching(y, alreadyInY, ellipsis.sizeMin())) {
                            return false;
                        } else {
                            
                            ellipsisMatched = true;
                            break; 
                        }


                    } else */


                    boolean xConst = u.constant(x);
                    if (!xConst) {


                        xFixed.add(x);

                    } else {

                        if (!yFree.remove(x)) {
                            return false;
                        }


                    }


                }

                if (ellipsis == null)
                    return yFree.isEmpty();

                final int xs = xFixed.size();
                int ys = yFree.size();
                int numRemainingForEllipsis = ys - xs;


                boolean vs = ellipsis.validSize(numRemainingForEllipsis);
                if (!vs)
                    return false;

                switch (xs) {
                    case 0:

                        Term match = ys > 0 ? EllipsisMatch.matched(yFree) : EllipsisMatch.empty;


                        return this.ellipsis.unify(match, u);


                    case 1:
                        Term x0 = xFixed.getOnly();
                        if (yFree.size() == 1) {
                            assert(ellipsis.minArity == 0);
                            return x0.unify(yFree.first(), u) && this.ellipsis.unify(EllipsisMatch.empty, u);
                        } else {
                            return u.termutes.add(new Choose1(this.ellipsis, x0, yFree));
                        }

                    case 2:
                        return u.termutes.add(new Choose2(this.ellipsis, u, xFixed, yFree));

                    default:

                        throw new RuntimeException("unimpl: " + xs + " arity combination unimplemented");
                }


            }

        }


    }

    private static final TermTransform.NegObliviousTermTransform Ellipsify = new TermTransform.NegObliviousTermTransform() {


        @Override
        protected @Nullable Term transformNonNegCompound(Compound x) {
            Term __x = Retemporalize.retemporalizeAllToXTERNAL.transformCompound(x);
            if (!(__x instanceof Compound))
                return __x;

            Term _x = super.transformNonNegCompound((Compound) __x);
            if (!(_x instanceof Compound)) {
                return _x;
            }

            x = (Compound) _x;

            Term xx;
            boolean neg = x.op() == NEG;
            if (neg) xx = x.unneg(); else xx = x;

            @Nullable Ellipsislike e = firstEllipsis(xx.subterms());
            return (e != null ? ellipsis((Compound) xx, xx.subterms(), (Ellipsis) e) : xx).negIf(neg);
        }
    };
}
