package nars.term;

import nars.Op;
import nars.term.atom.Atomic;
import nars.term.var.CommonVariable;
import nars.unify.Unify;
import org.jetbrains.annotations.Nullable;

import static nars.Op.Null;
import static nars.Op.VAR_PATTERN;

/**
 * similar to a plain atom, but applies altered operating semantics according to the specific
 * varible type, as well as serving as something like the "marker interfaces" of Atomic, Compound, ..
 * <p>
 * implemented by both raw variable terms and variable concepts
 **/
public interface Variable extends Atomic {


    static boolean commonalizableVariable(Op x) {
        return x.in(Op.VAR_QUERY.bit | Op.VAR_DEP.bit | Op.VAR_INDEP.bit);
    }

    @Override
    @Nullable
    default Term normalize() {
        return this; 
    }

    @Override
    Variable normalize(byte offset);

    @Override
    default Term conceptualizableOrNull() {
        return Null;
    }

    /**
     * The syntactic complexity of a variable is 0, because it does not refer to
     * any concept.
     *
     * @return The complexity of the term, an integer
     */
    @Override
    default int complexity() {
        return 0;
    }







    








    @Override
    default float voluplexity() {
        return 0.5f;
    }

    @Override
    default boolean unify(Term _y, Unify u) {

        if (equals(_y)) return true;

        Term y = u.resolve(_y);
        Term x = u.resolve(this);

        if (x instanceof Variable) {
            return x.equals(y) || ((Variable) x).unifyVar(y, u, true);
        } else if (y instanceof Variable) {
            return ((Variable) y).unifyVar(x, u, false);
        } else {
            return x.unify(y, u);
        }
    }

    /** the direction parameter is to maintain correct provenance of variables when creating common vars.
     *  since
     *    #1 from x  is a different instance than  #1 from y
     */
    default boolean unifyVar(Term y, Unify u, boolean forward) {
        final Variable x = this;
        if (y instanceof Variable) {
            return unifyVar(x, ((Variable)y), forward, u);
        } else {
            return u.putXY(x, y);
        }
    }

    static boolean unifyVar(Variable x, Variable y, boolean forward, Unify u) {

        
        if (x == Op.imInt || x == Op.imExt || y == Op.imInt || y == Op.imExt)
            return x==y;

        
        

        Op xOp = x.op();
        Op yOp = y.op();
        if (xOp!=VAR_PATTERN && xOp == yOp) {

















            Term common = forward ? CommonVariable.common(x, y) : CommonVariable.common(y, x);






            if (u.replaceXY(x, common) && u.replaceXY(y, common)) {



                    return true;
            }

        } else {

            
            if (xOp.id < yOp.id) {
                if (u.varSymmetric)
                    return u.putXY(y, x);
                else
                    return false;
            }
        }

        return u.putXY(x, y);
    }

    @Override
    default boolean unifyReverse(Term x, Unify u) {
        if (!u.varSymmetric)
            return false;

        Term y = u.resolve(this);
        if (y!=this)
            return y.unify(x, u); 

        return
            u.matchType(op()) &&
            unifyVar(x, u, false);
    }
}
