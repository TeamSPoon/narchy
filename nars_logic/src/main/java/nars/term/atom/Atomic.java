package nars.term.atom;

import nars.term.Compound;
import nars.term.SubtermVisitor;
import nars.term.Term;
import nars.term.Termed;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.Predicate;

/** Base class for Atomic types. */
public abstract class Atomic implements Term {



    @Override
    public final boolean isCompound() { return false; }

    @Nullable
    @Override abstract public String toString();




    @Override
    public final void recurseTerms(@NotNull SubtermVisitor v, Compound parent) {
        v.accept(this, parent);
    }

    @Override public final boolean and(@NotNull Predicate<? super Term> v) {
        return v.test(this);
    }

    @Override public final boolean or(@NotNull Predicate<? super Term> v) {
        return and(v); //re-use and, even though it's so similar
    }

    @Override
    public final String toString(boolean pretty) {
        return toString();
    }

    @Override
    public final void append(@NotNull Appendable w, boolean pretty) throws IOException {
        w.append(toString());
    }

    /** preferably use toCharSequence if needing a CharSequence; it avoids a duplication */
    @NotNull
    @Override
    public final StringBuilder toStringBuilder(boolean pretty) {
        return new StringBuilder(toString());
    }

    /** number of subterms; for atoms this must be zero */
    @Override public final int size() {
        return 0;
    }

    /** atoms contain no subterms so impossible for anything to fit "inside" it */
    @Override public final boolean impossibleSubTermVolume(int otherTermVolume) {
        return true;
    }

    @Override public final boolean containsTerm(Term t) {
        return false;
    }

    @Override public final boolean isCommutative() {
        return false;
    }


    /** default volume = 1 */
    @Override public int volume() { return 1; }


    @Override
    public abstract int varIndep();

    @Override
    public abstract int varDep();

    @Override
    public abstract int varQuery();

    @Override
    public int structure() {
        return op().bit();
    }


}
