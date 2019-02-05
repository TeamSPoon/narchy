package spacegraph.space2d.container.layout;

import jcog.data.list.FasterList;
import jcog.data.pool.MetalPool;
import spacegraph.space2d.container.graph.Graph2D;
import spacegraph.util.MutableFloatRect;

public abstract class DynamicLayout2D<X, M extends MutableFloatRect<X>> implements Graph2D.Graph2DUpdater<X> {

    protected final FasterList<MutableFloatRect<X>> nodes = new FasterList<>();

    private final MetalPool<MutableFloatRect<X>> nodesPool = new MetalPool<MutableFloatRect<X>>() {

        @Override
        public MutableFloatRect<X> create() {
            return newContainer();
        }

        @Override
        public void put(MutableFloatRect<X> i) {
            i.clear();
            super.put(i);
        }
    };

    protected MutableFloatRect<X> newContainer() {
        return new MutableFloatRect<X>();
    }

    @Override
    public void update(Graph2D<X> g, int dtMS) {
        if (!get(g))
            return;

        layout(g);

        put();
    }

    protected abstract void layout(Graph2D<X> g);

    private boolean get(Graph2D<X> g) {
        g.forEachValue(v -> {
            if (v.visible() && !v.pinned()) {
                MutableFloatRect<X> m = nodesPool.get();
                m.set(v);
                nodes.add(m);
            }
        });

        return !nodes.isEmpty();
    }

    private void put() {
        nodes.forEach(m -> put(m, m.node));

        nodesPool.steal(nodes);
    }

    /** apply to node after layout
     * default impl: copy directly
     * */
    protected void put(MutableFloatRect<X> mover, Graph2D.NodeVis node) {
        node.posXYWH(mover.x, mover.y, mover.w, mover.h);
    }

}
