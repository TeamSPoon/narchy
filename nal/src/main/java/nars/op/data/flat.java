package nars.op.data;

import nars.$;
import nars.Op;
import nars.subterm.Subterms;
import nars.term.Functor;
import nars.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * recursively collects the contents of setAt/list compound target argument's
 * into a list, to one of several resulting target types:
 * product
 * set (TODO)
 * conjunction (TODO)
 * <p>
 * TODO recursive version with order=breadth|depth option
 */
public abstract class flat extends Functor.AbstractInlineFunctor1 {

    public static final flat flatProduct = new flat() {

        @Override
        public Term result(List<Term> terms) {
            return $.p(terms);
        }

    };

    protected flat() {
        super("flat");
    }

    @NotNull
    public static List<Term> collect(@NotNull Term[] x, @NotNull List<Term> l) {
        for (Term a : x) {
            if (a.op() == Op.PROD || a.op().isSet() || a.op() == Op.CONJ) {
                ((Subterms) a).addTo(l);
            } else
                l.add(a);
        }
        return l;
    }

    @Override
    public @Nullable Term apply1(Term x) {

        List<Term> l = $.newArrayList(x.volume());
        collect(x.arrayClone(), l);
        return result(l);

    }

    public abstract Term result(List<Term> terms);


}
