package nars.subterm;

import com.google.common.io.ByteArrayDataOutput;
import jcog.data.iterator.ArrayIterator;
import jcog.util.ArrayUtils;
import nars.term.Term;
import org.eclipse.collections.api.block.function.primitive.IntObjectToIntFunction;
import org.eclipse.collections.api.block.predicate.primitive.ObjectIntPredicate;

import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Holds a vector or tuple of terms.
 * Useful for storing a fixed number of subterms
 */
public class ArrayTermVector extends TermVector {

    /*@NotNull*/
    /*@Stable*/
    private final Term[] terms;

    public ArrayTermVector(/*@NotNull */Term... terms) {
        super(terms);
        this.terms = terms;
        normalized = normalized(this);
    }



    @Override
    public String toString() {
        return Subterms.toString(terms);
    }

    @Override
    public final int intifyShallow(IntObjectToIntFunction<Term> reduce, int v) {
        for (Term t : terms)
            v = reduce.intValueOf(v, t);
        return v;
    }

    @Override
    public final int intifyRecurse(IntObjectToIntFunction<Term> reduce, int v) {
        for (Term t : terms)
            v = t.intifyRecurse(reduce, v);
        return v;
    }

    @Override
    public int indexOf(Term t) {
        //quick test for identity equality
        if (terms.length < 5) {
            int i = ArrayUtils.indexOfIdentity(terms, t);
            if (i!=-1)
                return i;
        }
        return super.indexOf(t);
    }

    @Override
    public final boolean equals(/*@NotNull*/ Object obj) {
        if (this == obj) return true;

        if (!(obj instanceof Subterms))
            return false;

        Subterms that = (Subterms) obj;
        if (hash != that.hashCodeSubterms())
            return false;

        if (obj instanceof ArrayTermVector) {

            ArrayTermVector v = (ArrayTermVector) obj;
//            if (terms == v.terms)
//                return true;
//            int n = terms.length;
//            if (v.terms.length != n)
//                return false;
//            for (int i = 0; i < terms.length; i++) {
//                Term a = terms[i];
//                Term b = v.terms[i];
//                if (a == b) continue;
//                if (a.equals(b)) {
//                    v.terms[i] = a; //share copy
//                } else {
//                    return false; //mismatch
//                }
//            }
            if (!Arrays.equals(terms, v.terms))
                return false;

        } else {
            final Term[] x = this.terms;
            int s = x.length;
            if (s != that.subs())
                return false;

            for (int i = 0; i < s; i++)
                if (!x[i].equals(that.sub(i)))
                    return false;

        }

        if (obj instanceof TermVector)
            equivalentTo((TermVector) obj);


        return true;
    }


    @Override
    /*@NotNull*/ public final Term sub(int i) {
        return terms[i];
    }


    @Override
    public final Term[] arrayClone() {
        return terms.clone();
    }

    @Override
    public final Term[] arrayShared() {
        return terms;
    }

    @Override
    public final int subs() {
        return terms.length;
    }

    @Override
    public final Iterator<Term> iterator() {
        return ArrayIterator.get(terms);
    }

    @Override
    public final void forEach(Consumer<? super Term> action, int start, int stop) {
        for (int i = start; i < stop; i++) {
            action.accept(this.terms[i]);
        }
    }


    @Override
    public boolean ANDith(ObjectIntPredicate<Term> p) {
        Term[] t = this.terms;
        for (int i = 0, tLength = t.length; i < tLength; i++) {
            if (!p.accept(t[i], i))
                return false;
        }
        return true;
    }

    @Override
    public void appendTo(ByteArrayDataOutput out) {
        Term[] t = this.terms;
        int n;
        out.writeByte(n = t.length);
        for (int i = 0; i < n; i++)
            t[i].appendTo(out);
    }

}
