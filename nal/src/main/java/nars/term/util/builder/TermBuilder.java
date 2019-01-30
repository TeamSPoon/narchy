package nars.term.util.builder;

import jcog.data.byt.DynBytes;
import nars.Op;
import nars.Param;
import nars.subterm.*;
import nars.term.Compound;
import nars.term.Neg;
import nars.term.Term;
import nars.term.anon.AnonID;
import nars.term.atom.Bool;
import nars.term.compound.CachedCompound;
import nars.term.compound.CachedUnitCompound;
import nars.term.util.Statement;
import nars.term.util.TermException;
import nars.term.util.conj.Conj;
import nars.term.util.conj.ConjCommutive;
import nars.term.util.transform.CompoundNormalization;
import nars.time.Tense;
import nars.unify.ellipsis.EllipsisMatch;
import nars.unify.ellipsis.Ellipsislike;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.TreeSet;

import static nars.Op.CONJ;
import static nars.Op.NEG;
import static nars.term.Terms.sorted;
import static nars.time.Tense.DTERNAL;
import static nars.time.Tense.XTERNAL;

/**
 * interface for target and subterm builders
 * this call tree eventually ends by either:
 * - instance(..)
 * - reduction to another target or True/False/Null
 */
public abstract class TermBuilder {

    abstract public Term compound(Op o, int dt, Term... u);

    protected abstract Subterms subterms(@Nullable Op inOp, Term... u);

    public final Term compound(Op o, Term... u) {
        return compound(o, DTERNAL, u);
    }

//    protected Term resolve(Term x){
//        return x;
//    }

    public final Subterms subterms(Term... s) {
        if (s.length == 0)
            return Op.EmptySubterms;
        return subterms(null, s);
    }


    public final Subterms subterms(Collection<Term> s) {
        return subterms(s.toArray(Op.EmptyTermArray));
    }

    public static Subterms theSubterms(boolean tryAnon, Term... t) {
        final int tLength = t.length;
        if (tLength == 0)
            return Op.EmptySubterms;

        if (tryAnon && isAnon(t))
            return new AnonVector(t);
        else
            return newSubtermsVector(t);

    }

    static boolean isAnon(Term[] t) {
        for (Term x : t) {
            //assert (!(x instanceof EllipsisMatch)) : "ellipsis match should not be a subterm of ANYTHING";
            if (x instanceof AnonID)
                continue;
            if (x instanceof Neg && x.unneg() instanceof AnonID)
                continue;
            return false;
        }
        return true;
    }

    static Subterms newSubtermsVector(Term[] t) {
        Term t0 = t[0];
        switch (t.length) {
            case 0:
                throw new UnsupportedOperationException();

            case 1: {
                return new UniSubterm(t0);
            }

            case 2: {
                Term t1 = t[1];

                if (t1.equals(t0))
                    return new BiSubterm.BiRepeat(t0);

                return
//                    return (this instanceof InterningTermBuilder) ?
//                            new BiSubterm.ReversibleBiSubterm(t[0], t[1]) :
                        new BiSubterm(t0, t1);
            }

            default: {
                //TODO Param.SUBTERM_BYTE_KEY_CACHED_BELOW_VOLUME
                return new ArrayTermVector(t);
            }
        }
    }

    public final Term theSortedCompound(Op o, int dt, Collection<Term> u) {
        assert (Tense.dtSpecial(dt));
        Term[] s = sorted(u);
        if (s.length == 1 && o == CONJ)
            return s[0];

        return theCompound(o, dt, s);
    }

    public final Term theCompound(Op o, int dt, Term... u) {
        return theCompound(o, dt, u, null);
    }

    protected final Term theCompound(Op o, int dt, Term[] t, @Nullable DynBytes key) {
        assert (!o.atomic) : o + " is atomic, yet given subterms: " + Arrays.toString(t);


        boolean hasEllipsis = false;

        for (Term x : t) {
            if (x == Bool.Null)
                return Bool.Null;
            if (!hasEllipsis && (x instanceof Ellipsislike))
                hasEllipsis = true;
        }

        int s = t.length;
        assert (o.maxSubs >= s) :
                "subterm overflow: " + o + ' ' + Arrays.toString(t);
        assert (o.minSubs <= s || hasEllipsis) :
                "subterm underflow: " + o + ' ' + Arrays.toString(t);

        if (s == 1 && !AnonID.isAnonPosOrNeg(t[0])) {
            Term x = t[0];
            switch (o) {
                case NEG:
                    return NEG.the(x);
                case CONJ:
                    break; //skip below
                default:
                    return new CachedUnitCompound(o, x);
            }
        }


        //String before = (Arrays.toString(t)); //HACK TEMPORARY

        Subterms subs = subterms(o, t, dt, key);

        //String after = (Arrays.toString(t)); //HACK TEMPORARY

        Term y = CachedCompound.newCompound(o, dt, subs);

//        if (!before.equals(after))
//            System.out.println("\t" + Arrays.toString(t)); //HACK TEMPORARY

        return y;
    }

    protected Subterms subterms(Op o, Term[] t, int dt, @Nullable DynBytes key) {
        return subterms(o, t);
    }


    public static Compound newCompound(Op op, Subterms subterms) {
        return CachedCompound.newCompound(op, DTERNAL, subterms);
    }


    public Term normalize(Compound x, byte varOffset) {
        Term y = new CompoundNormalization(x, varOffset).transformCompound(x);

//        LazyCompound yy = new LazyCompound();
//        new nars.util.target.transform.CompoundNormalization(this, varOffset)
//                .transform(this, yy);
//        Term y = yy.get();

        if (varOffset == 0 && y instanceof Compound) {
            y.subterms().setNormalized();
        }

        return y;

    }

    public Term conj(final int dt, Term... u) {
        return conj(false, dt, u);
    }

    public Term conj(boolean preSorted, final int dt, Term... u) {

        if (!preSorted)
            u = Conj.preSort(dt, u);

        switch (u.length) {

            case 0:
                return Bool.True;

            case 1:
                Term only = u[0];
                if (only instanceof EllipsisMatch) {

                    return conj(dt, only.arrayShared());
                } else {

                    return only instanceof Ellipsislike ?
                            HeapTermBuilder.the.theCompound(CONJ, dt, only)
                            :
                            only;
                }

        }





        switch (dt) {
            case DTERNAL:
            case 0: {

                return ConjCommutive.theSorted(dt, u);
            }

            case XTERNAL:
                int ul = u.length;
                switch (ul) {
                    case 0:
                        return Bool.True;

                    case 1:
                        return u[0];

                    default: {
                        if (ul == 2) {
                            //special case: simple arity=2
                            if (!u[0].equals(u[1])) { // && !unfoldableInneralXternalConj(u[0]) && !unfoldableInneralXternalConj(u[1])) {
                                return HeapTermBuilder.the.theCompound(CONJ, XTERNAL, sorted(u));
                            } else
                                return HeapTermBuilder.the.theCompound(CONJ, XTERNAL, u[0], u[0]); //repeat
                        } else {

                            TreeSet<Term> uux = new TreeSet();
                            Collections.addAll(uux, u);

                            if (uux.size() == 1) {
                                Term only = uux.first();
                                return HeapTermBuilder.the.theCompound(CONJ, XTERNAL, only, only); //repeat
                            } else {
                                return HeapTermBuilder.the.theCompound(CONJ, XTERNAL, sorted(uux));
                            }
                        }
                    }


//                    case 2: {
//
//
//                        Term a = u[0];
//                        if (a.op() == CONJ && a.dt() == XTERNAL && a.subs() == 2) {
//                            Term b = u[1];
//
//                            int va = a.volume();
//                            int vb = b.volume();
//
//                            if (va > vb) {
//                                Term[] aa = a.subterms().arrayShared();
//                                int va0 = aa[0].volume();
//                                int va1 = aa[1].volume();
//                                int vamin = Math.min(va0, va1);
//
//
//                                if ((va - vamin) > (vb + vamin)) {
//                                    int min = va0 <= va1 ? 0 : 1;
//
//                                    Term[] xu = {CONJ.the(XTERNAL, new Term[]{b, aa[min]}), aa[1 - min]};
//                                    Arrays.sort(xu);
//                                    return compound(CONJ, XTERNAL, xu);
//                                }
//                            }
//
//                        }
//                        break;
//                    }
//
                }


            default: {
                if (u.length != 2)
                    throw new TermException("temporal conjunction with n!=2 subterms");

                return (dt >= 0) ?
                        Conj.sequence(u[0], 0, u[1], +dt + u[0].eventRange()) :
                        Conj.sequence(u[1], 0, u[0], -dt + u[1].eventRange());
            }
        }

    }

//    private static boolean unfoldableInneralXternalConj(Term x) {
//        return x.op() == CONJ && x.dt() == XTERNAL;
//    }

    public Term root(Compound x) {
        if (!x.hasAny(Op.Temporal))
            return x;
        return x.temporalize(
                Param.conceptualization
        );
    }

    public Term concept(Compound x) {
        Term term = x.unneg().root();

        Op op = term.op();
        assert (op != NEG) : this + " concept() to NEG: " + x.unneg().root();
        if (!op.conceptualizable)
            return Bool.Null;


        return term.normalize();
//        Term term2 = target.normalize();
//        if (term2 != target) {
//            if (term2 == null)
//                return Bool.Null;
//
//            //assert (term2.op() == op): term2 + " not a normal normalization of " + target; //<- allowed to happen when image normalization is involved
//
//            target = term2.unneg();
//        }
//
//
//        return target;
    }

    protected Term statement(Op op, int dt, Term subject, Term predicate) {
        return Statement.statement(op, dt, subject, predicate);
    }

    public final Term statement(Op op, int dt, Term[] u) {
        assert (u.length == 2) : op + " requires 2 arguments, but got: " + Arrays.toString(u);
        return statement(op, dt, u[0], u[1]);
    }

}
