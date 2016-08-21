package nars.index;

import nars.Op;
import nars.nal.meta.PatternCompound;
import nars.nal.meta.match.Ellipsis;
import nars.nal.meta.match.EllipsisTransform;
import nars.nal.rule.PremiseRule;
import nars.term.Compound;
import nars.term.Term;
import nars.term.Termed;
import nars.term.atom.Atomic;
import nars.term.container.TermContainer;
import nars.term.container.TermVector;
import nars.term.var.Variable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static nars.Op.NEG;

/**
 * Index which specifically holds the term components of a deriver ruleset.
 */
public class PatternIndex extends RawTermIndex {

//    public PatternIndex() {
//        super(new HashSymbolMap(
//              new ConcurrentHashMapUnsafe(512)),
//              new ConcurrentHashMapUnsafe(2048), Terms.terms, null);
//    }
    public PatternIndex() {
        super(null, 1024);

        loadBuiltins();
    }

    @Override
    protected @Nullable
    Termed theCompound(@NotNull Compound t, boolean createIfMissing) {

        //dont store the actual rules, they are guaranteed unique by other means
        if (t instanceof PremiseRule) {
            return t;
        }

        //process Patterns
        return make(t);

    }

    static protected boolean canBuildConcept(@NotNull Term y) {
        if (y instanceof Compound) {
            if (y.op() == NEG)
                return false;
            return true; //return !y.isAny(invalidConceptBitVector) && !y.hasTemporal();
        } else {
            return !(y instanceof Variable);
        }

    }

    @NotNull
    private PatternCompound make(@NotNull Compound c) {

        TermContainer s = c.subterms();
        int ss = s.size();
        Term[] bb = new Term[ss];
        boolean changed = false;//, temporal = false;
        for (int i = 0; i < ss; i++) {
            Term a = s.term(i);

            Term b;
            if (a instanceof Compound) {

                if (!canBuildConcept(a) || a.hasTemporal()) {
                    //temporal = true;//dont store subterm arrays containing temporal compounds
                    b = a;
                } else {
                    /*if (b != a && a.isNormalized())
                        ((GenericCompound) b).setNormalized();*/
                    b = theCompound((Compound) a, true).term();
                }
            } else {
                b = theAtom((Atomic) a, true).term();
            }
            if (a != b) {
                changed = true;
            }
            bb[i] = b;
        }

        TermContainer v = internSubterms(changed ? TermVector.the(bb) : s);

        Ellipsis e = Ellipsis.firstEllipsis(v);
        return e != null ?
                makeEllipsis(c, v, e) :
                new PatternCompound.PatternCompoundSimple(c, v);
    }

    @NotNull
    private static PatternCompound makeEllipsis(@NotNull Compound seed, @NotNull TermContainer v, @NotNull Ellipsis e) {


        //this.ellipsisTransform = hasEllipsisTransform(this);
        boolean hasEllipsisTransform = false;
        int xs = seed.size();
        for (int i = 0; i < xs; i++) {
            if (seed.term(i) instanceof EllipsisTransform) {
                hasEllipsisTransform = true;
                break;
            }
        }

        Op op = seed.op();

        boolean ellipsisTransform = hasEllipsisTransform;
        boolean commutative = (!ellipsisTransform && op.commutative);

        if (commutative) {
            if (ellipsisTransform)
                throw new RuntimeException("commutative is mutually exclusive with ellipsisTransform");

            return new PatternCompound.PatternCompoundWithEllipsisCommutive(seed, e, v);
        } else {
            if (ellipsisTransform) {
                if (!op.isImage() && op != Op.PROD)
                    throw new RuntimeException("imageTransform ellipsis must be in an Image or Product compound");

                return new PatternCompound.PatternCompoundWithEllipsisLinearImageTransform(
                        seed, (EllipsisTransform)e, v);
            } else if (op.isImage()) {
                return new PatternCompound.PatternCompoundWithEllipsisLinearImage(seed, e, v);
            } else {
                return new PatternCompound.PatternCompoundWithEllipsisLinear(seed, e, v);
            }
        }

    }



    @Override
    protected final boolean transformImmediates() {
        return false;
    }

}
