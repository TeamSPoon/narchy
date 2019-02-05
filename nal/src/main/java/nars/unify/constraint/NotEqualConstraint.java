package nars.unify.constraint;

import com.google.common.collect.Iterables;
import nars.Op;
import nars.derive.premise.PreDerivation;
import nars.term.Term;
import nars.term.Variable;
import nars.term.control.AbstractPred;
import nars.term.control.PREDICATE;
import nars.term.var.Img;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static nars.$.$$;
import static nars.Op.INH;
import static nars.Op.NEG;


public final class NotEqualConstraint extends RelationConstraint {

    public NotEqualConstraint(Variable target, Variable other) {
        super("neq", target, other);
    }

    @Override
    protected @Nullable RelationConstraint newMirror(Variable newX, Variable newY) {
        return new NotEqualConstraint(newX, newY);
    }

    @Override
    public float cost() {
        return 0.1f;
    }

    @Override
    public boolean invalid(Term x, Term y) {
        return y.equals(x);
    }

//    @Override
//    public boolean remainInAndWith(RelationConstraint c) {
//        if (c instanceof NotEqualRootConstraint || c instanceof SubOfConstraint)
//            return false;
//        return true;
//    }

    public static final class NotEqualRootConstraint extends RelationConstraint {

        public NotEqualRootConstraint(Variable target, Variable other) {
            super("neqRoot", target, other);
        }

        @Override
        protected @Nullable RelationConstraint newMirror(Variable newX, Variable newY) {
            return new NotEqualRootConstraint(newX, newY);
        }

        @Override
        public float cost() {
            return 0.2f;
        }

        @Override
        public boolean invalid(Term x, Term y) {
            return y.equalsRoot(x);
        }

//        @Override
//        public boolean remainInAndWith(RelationConstraint c) {
//            if (c instanceof NeqRootAndNotRecursiveSubtermOf)
//                return false;
//            return true;
//        }
    }

    static final PREDICATE<PreDerivation> TaskOrBeliefHasNeg = new AbstractPred<PreDerivation>($$("TaskOrBeliefHasNeg")) {

        @Override
        public boolean test(PreDerivation d) {
            return d.taskTerm.hasAny(Op.NEG) || d.beliefTerm.hasAny(Op.NEG);
        }

        @Override
        public float cost() {
            return 0.12f;
        }
    };

    public static final class EqualNegConstraint extends RelationConstraint {

        public EqualNegConstraint(Variable target, Variable other) {
            super("eqNeg", target, other);
        }


        @Override
        protected @Nullable RelationConstraint newMirror(Variable newX, Variable newY) {
            return new EqualNegConstraint(newX, newY);
        }

        @Override
        public float cost() {
            return 0.15f;
        }

        @Override
        public boolean invalid(Term x, Term y) {
            return !x.equalsNeg(y);
        }

//        @Override
//        public boolean remainInAndWith(RelationConstraint c) {
//            if (c instanceof NeqRootAndNotRecursiveSubtermOf)
//                return false;
//            return true;
//        }
    }

    public static final class EqualPosOrNeg extends RelationConstraint {

        public EqualPosOrNeg(Variable target, Variable other) {
            super("eqPN", target, other);
        }


        @Override
        protected @Nullable RelationConstraint newMirror(Variable newX, Variable newY) {
            return new EqualNegConstraint(newX, newY);
        }

        @Override
        public float cost() {
            return 0.175f;
        }

        @Override
        public boolean invalid(Term x, Term y) {
            return !x.equals(y) && !x.equalsNeg(y) ;
        }

    }

//    /** compares target equality, unnegated */
//    public static final class NotEqualUnnegConstraint extends RelationConstraint {
//
//
//        public NotEqualUnnegConstraint(Term target, Term y) {
//            super(target, y, "neqUnneg");
//        }
//
//        @Override
//        public float cost() {
//            return 0.2f;
//        }
//
//        @Override
//        public boolean invalid(Term x, Term y) {
//
//            return
//
//                    y.equals(x);
//
//        }
//
//
//    }

    /**
     * containment test of x to y's subterms and y to x's subterms
     */
    public static final class NeqRootAndNotRecursiveSubtermOf extends RelationConstraint {

        public NeqRootAndNotRecursiveSubtermOf(Variable x, Variable y) {
            super("neqRCom", x, y);
        }

        @Override
        protected @Nullable RelationConstraint newMirror(Variable newX, Variable newY) {
            return new NeqRootAndNotRecursiveSubtermOf(newX, newY);
        }

        @Override
        public float cost() {
            return 0.5f;
        }

        @Override
        public boolean invalid(Term x, Term y) {
            return isSubtermOfTheOther(x, y, true, true);
        }

        final static Predicate<Term> limit =
                Op.recursiveCommonalityDelimeterWeak;
        //Op.recursiveCommonalityDelimeterStrong;


        static boolean isSubtermOfTheOther(Term a, Term b, boolean recurse, boolean excludeVariables) {

            if (a.equals(b) || ((excludeVariables) && (a instanceof Variable || b instanceof Variable)))
                return false;

            int av = a.volume(), bv = b.volume();


            //a > b |- a contains b?

            if (av < bv) {

                Term c = a;
                a = b;
                b = c;
            }

            Iterable<Term> bb = inhComponents(b);
            if (bb != null) {
                for (Term bbb : bb) {
                    if (test(a, recurse, excludeVariables, bbb))
                        return true;
                }
                return false;
            } else {
                if (av == bv) {
                    return false;
                } else {
                    return test(a, recurse, excludeVariables, b);
                }
            }
        }


        private static boolean test(Term a, boolean recurse, boolean excludeVariables, Term b) {
            if ((!excludeVariables || !(b instanceof Variable)) && !(b instanceof Img)) {
                return recurse ?
                        a.containsRecursively(b, true, limit) :
                        a.contains(b);
            } else
                return false;
        }

        @Nullable
        private static Iterable<Term> inhComponents(Term b) {
            switch (b.op()) {
//                case SETe:
//                case SETi:
                case SECTi:
                case SECTe: {
                    Iterable<Term> x = b.subterms();
                    if (b.hasAny(NEG))
                        x = Iterables.transform(x, Term::unneg);
                    return x;
                }
                default:
                    return null;
            }
        }


    }

    /**
     * if both are inheritance, prohibit if the subjects or predicates match.  this is to exclude
     * certain derivations which occurr otherwise in NAL1..NAL3
     */
    public static final class NoCommonInh extends RelationConstraint {

        public NoCommonInh(Variable target, Variable other) {
            super("noCommonInh", target, other);
        }

        @Override
        protected @Nullable RelationConstraint newMirror(Variable newX, Variable newY) {
            return new NoCommonInh(newX, newY);
        }

        @Override
        public float cost() {
            return 0.2f;
        }

        @Override
        public boolean invalid(Term x, Term y) {
            return (x.op() == INH && y.op() == INH && (x.sub(0).equals(y.sub(0)) || x.sub(1).equals(y.sub(1))));
        }

    }

    public static class NotSetsOrDifferentSets extends RelationConstraint {
        public NotSetsOrDifferentSets(Variable target, Variable other) {
            super("notSetsOrDifferentSets", target, other);
        }

        @Override
        protected @Nullable RelationConstraint newMirror(Variable newX, Variable newY) {
            return new NotSetsOrDifferentSets(newX, newY);
        }

        @Override
        public float cost() {
            return 0.1f;
        }

        @Override
        public boolean invalid(Term x, Term y) {
            Op xo = x.op();
            return xo.isSet() && (xo == y.op());
        }
    }
}
