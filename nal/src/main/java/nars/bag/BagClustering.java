package nars.bag;

import jcog.data.list.FasterList;
import jcog.learn.gng.NeuralGasNet;
import jcog.learn.gng.impl.Centroid;
import jcog.pri.PriBuffer;
import jcog.pri.Prioritized;
import jcog.pri.VLink;
import jcog.pri.bag.Bag;
import jcog.pri.bag.impl.ArrayBag;
import jcog.pri.bag.impl.BufferedBag;
import jcog.pri.op.PriMerge;
import jcog.util.ArrayUtils;
import org.eclipse.collections.api.block.function.primitive.IntToIntFunction;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.List;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/**
 * clusterjunctioning
 * TODO abstract into general purpose "Cluster of Bags" class
 */
public class BagClustering<X> {
    /**
     * how to interpret the bag items as vector space data
     */
    abstract public static class Dimensionalize<X> {

        final int dims;

        protected Dimensionalize(int dims) {
            this.dims = dims;
        }

        abstract public void coord(X t, double[] d);

        /**
         * default impl, feel free to override
         */
        public double distanceSq(double[] a, double[] b) {
            return Centroid.distanceCartesianSq(a, b);
        }

    }


    public final Bag<X, VLink<X>> bag;
    public final NeuralGasNet net;
    final Dimensionalize<X> model;
    /**
     * TODO allow dynamic change
     */
    protected /*Flip<*/ FasterList<VLink<X>> sorted = new FasterList<>();


    public BagClustering(Dimensionalize<X> model, int centroids, int initialCap) {


        this.model = model;

        this.net = new NeuralGasNet(model.dims, centroids, model::distanceSq);

        PriMerge merge = PriMerge.max;
        ArrayBag<X, VLink<X>> b = new ArrayBag<X, VLink<X>>(merge, initialCap) {

            @Override
            public X key(VLink<X> xvLink) {
                return xvLink.get();
            }
        };

        //this.bag = b;
        this.bag = new BufferedBag.SimpleBufferedBag<>(b, new PriBuffer<VLink<X>>(merge));
//        this.bag = new FastPutProxyBag<>(b, 1024);
//        this.bag = new PriLinkHijackBag<X,VLink<X>>(PriMerge.max, initialCap, 4) {
//
//        };

    }

    public void print() {
        print(System.out);
    }

    public void print(PrintStream out) {
        forEachCluster(c -> {
            out.println(c);
            stream(c._id).forEach(i -> {
                out.print("\t");
                out.println(i);
            });
            out.println();
        });
        out.println(net.edges);
    }


    public void forEachCluster(Consumer<Centroid> c) {
        for (Centroid b : net.centroids) {
            c.accept(b);
        }
    }

    public int size() {
        return bag.size();
    }

    public <A> void forEachCentroid(A arg, @Nullable Random rng, BiPredicate<Stream<VLink<X>>, A> each) {

        List<VLink<X>> sorted = itemsSortedByCentroid(rng);

        int current = -1;
        int n = sorted.size();
        int bs = -1;
        for (int i = 0; i < n; i++) {
            VLink<X> x = sorted.get(i);

            if (current != x.centroid || (i == n - 1)) {
                current = x.centroid;
                if (bs != -1 && i - bs > 1) {
                    if (!each.test(IntStream.range(bs, i + 1).mapToObj(sorted::get), arg))
                        break;
                }
                bs = i;
            }
        }


    }

    public void learn(float forgetRate, int learningIterations) {


        bag.commit(t -> {
            X tt = t.get();
            if ((tt instanceof Prioritized) && ((Prioritized) tt).isDeleted())
                t.delete();
        });

        int s = bag.size();
        if (s > 0) {
            bag.commit(bag.forget(forgetRate));

//            net.alpha.setAt(0.8f / s);
            float lambdaFactor = 1f;
            net.setLambdaPeriod((int) Math.ceil(s * lambdaFactor));
            for (int i = 0; i < learningIterations; i++)
                bag.forEach(this::learn);
        }
    }

    private List<VLink<X>> itemsSortedByCentroid(@Nullable Random rng) {

        int s = bag.size();
        if (s == 0)
            return List.of();

        FasterList<VLink<X>> x = new FasterList<>(s);
        bag.forEach(x::add);


        s = x.size();
        if (s > 0) {
            IntToIntFunction shuffler;
            if (rng != null) {
                int shuffle = rng.nextInt();
                shuffler = (c) -> c ^ shuffle;
            } else {
                shuffler = (c) -> c;
            }

            if (s > 2) {
                ArrayUtils.quickSort(0, s,
                        (a, b) -> a == b ? 0 : Integer.compare(shuffler.applyAsInt(x.get(a).centroid), shuffler.applyAsInt(x.get(b).centroid)),
                        x::swap);
            }
        }


        return x;
    }


    private void learn(VLink<X> x) {
        double x0 = x.coord[0];
        if (x0 != x0) {
            model.coord(x.get(), x.coord);
        }

        x.centroid = net.put(x.coord).id;
    }

    public void clear() {
        synchronized (bag) {
            bag.clear();
            net.clear();
        }
    }

    public final void put(X x, float pri) {
        assert(pri==pri);
        bag.putAsync(new VLink<>(x, pri, model.dims));
    }

    public final void remove(X x) {
        bag.remove(x);
    }

    /**
     * returns NaN if either or both of the items are not present
     */
    public double distance(X x, X y) {
        //assert (!x.equals(y));
        assert(x!=y);

        @Nullable VLink<X> xx = bag.get(x);
        if (xx != null && xx.centroid >= 0) {
            @Nullable VLink<X> yy = bag.get(y);
            if (yy != null && yy.centroid >= 0) {
                return Math.sqrt(net.distanceSq.distance(xx.coord, yy.coord));
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    /**
     * TODO this is O(N) not great
     */
    public Stream<VLink<X>> stream(int internalID) {
        return bag.stream().filter(y -> y.centroid == internalID);
    }

    public Stream<VLink<X>> neighbors(X x) {
        @Nullable VLink<X> link = bag.get(x);
        if (link != null) {
            int centroid = link.centroid;
            if (centroid >= 0) {
                Centroid[] nodes = net.centroids;
                if (centroid < nodes.length)
                    return stream(centroid)
                            .filter(y -> !y.equals(x))
                            ;
            }
        }
        return Stream.empty();
    }

}
