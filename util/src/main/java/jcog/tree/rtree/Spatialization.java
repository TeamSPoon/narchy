package jcog.tree.rtree;

import jcog.tree.rtree.split.AxialSplitLeaf;
import jcog.tree.rtree.split.LinearSplitLeaf;
import jcog.tree.rtree.split.QuadraticSplitLeaf;
import org.eclipse.collections.api.tuple.primitive.ObjectDoublePair;

import java.util.function.Function;

public class Spatialization<X> {

    public static final double EPSILON = Math.sqrt(Float.MIN_NORMAL);
    public static final float EPSILONf = (float) Math.sqrt(Float.MIN_NORMAL);

    public final Split<X> split;
    public final Function<X, HyperRegion> bounds;
    public final short max;       


    public Spatialization(@Deprecated final Function<X, HyperRegion> bounds, final Split<X> split, final int max) {
        this.max = (short) max;
        this.bounds = bounds;
        this.split = split;
    }

    public HyperRegion bounds(/*@NotNull*/ X x) {
        return bounds.apply(x);
    }

    public Leaf<X> newLeaf() {
        return new Leaf<>(max);
    }

    public Node<X> newBranch(Leaf<X> a, Leaf<X> b) {
//        int as = a.size();
//        int bs = b.size();
//        if (as + bs <= max) {
//            //merge two leaves but doesnt seem to happen
//            Leaf<X> l = new Leaf(max);
//            System.arraycopy(a.data, 0, l.data, 0, as);
//            System.arraycopy(b.data, as, l.data, as, bs);
//            return l;
//        } else {
            return new Branch<>(max, a, b);
//        }
    }

    public Node<X> split(X x, Leaf<X> leaf) {
        return split.split(x, leaf, this);
    }


    /** existing may be the same instance, or .equals() to the incoming */
    protected void onMerge(X existing, X incoming) {
        //default: do nothing
    }

    public final Leaf<X> transfer(ObjectDoublePair<X>[] sortedMbr, int from, int to) {
        final Leaf<X> l = newLeaf();
        for (int i = from; i < to; i++)
            l.add(sortedMbr[i].getOne(), true, this, new boolean[] { false });
        return l;
    }

    public double epsilon() {
        return EPSILON;
    }


    /**
     * Different methods for splitting nodes in an RTree.
     */
    @Deprecated public enum DefaultSplits {
        AXIAL {
            @Override
            public <T> Split<T> get() {
                return new AxialSplitLeaf<>();
            }
        },
        LINEAR {
            @Override
            public <T> Split<T> get() {
                return new LinearSplitLeaf<>();
            }
        },
        QUADRATIC {
            @Override
            public <T> Split<T> get() {
                return new QuadraticSplitLeaf<>();
            }
        };

        abstract public <T> Split<T> get();

    }
}
