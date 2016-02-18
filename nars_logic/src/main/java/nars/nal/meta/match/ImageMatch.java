package nars.nal.meta.match;

import nars.Op;
import nars.term.Compound;
import nars.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * the indicated relation term is inserted
 * at the index location of the original image
 * used to make products from image subterms
 */
public enum ImageMatch /*extends ArrayEllipsisMatch<Term>*/ {
    ;

//    private final Term to;
//    private final Compound origin;

    /**
     *
     * @param t the subvector of image terms collected in the ellipsis match, to be expanded with relationTerm inserted in the correct imdex position
     * @param relationTerm the term which replaces the _ in the formed term vector
     * @param y the (concrete) image being matched against the pattern
     * @return
     */
    @NotNull
    public static EllipsisMatch put(@NotNull Term[] t, Term relationTerm, @NotNull Compound y) {

        int l = t.length;
        Term[] t2 = new Term[l + 1];
        int yOffset = y.size() - l; //where 't' begins in Y
        int relOffset = y.relation() - yOffset; //where to expect _ in t
        int j = 0;
        for (Term x : t) {
            if (j == relOffset)
                t2[j++] = relationTerm;
            t2[j++] = x;
        }
        if (j < l+1)
            t2[j] = relationTerm; //it replaces the final position

        return new EllipsisMatch(t2);
    }

    @NotNull
    public static EllipsisMatch take(@NotNull EllipsisMatch m, int imageIndex) {

        //this.imageIndex = imageIndex;

        //mask the relation term
        Term[] t = m.term;
        t[imageIndex] = Op.Imdex;
        //m.init();//rehash because it changed?
        return m;
    }

//    @Override
//    public boolean applyTo(Subst substitution, Collection<Term> target, boolean fullMatch) {
//        Term relation = substitution.getXY(to);
//        if (relation == null) {
//            if (fullMatch)
//                return false;
//            else
//                relation = to;
//        }
//
//        Term[] t = origin.terms();
//        Compound origin = this.origin;
//
//        int ri = origin.relation();
//
//        int ot = t.length;
//        //int j = (ri == ot) ? 1 : 0; //shift
//        int j = 0;
//        int r = ri - 1;
//        for (int i = 0; i < ot; i++) {
//            target.add( i==r ? relation : t[j]);
//            j++;
//        }
////        System.arraycopy(ot, 0, t, 0, r);
////        t[r] = relation;
////        System.arraycopy(ot, r, t, r+1, ot.length - r);
//
//        return true;
//    }

}
