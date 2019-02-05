package spacegraph.space2d.container.graph;

import com.jogamp.opengl.GL2;
import jcog.Util;
import jcog.data.graph.Node;
import jcog.data.list.FasterList;
import jcog.data.map.CellMap;
import jcog.data.map.ConcurrentFastIteratingHashMap;
import jcog.data.map.MRUMap;
import jcog.data.pool.MetalPool;
import jcog.data.pool.Pool;
import jcog.data.set.ArrayHashSet;
import org.jetbrains.annotations.Nullable;
import spacegraph.space2d.Surface;
import spacegraph.space2d.SurfaceRender;
import spacegraph.space2d.container.Container;
import spacegraph.space2d.container.Splitting;
import spacegraph.space2d.container.collection.MutableMapContainer;
import spacegraph.space2d.container.grid.Gridding;
import spacegraph.space2d.container.unit.Clipped;
import spacegraph.space2d.container.unit.Scale;
import spacegraph.space2d.widget.button.PushButton;
import spacegraph.space2d.widget.meta.ObjectSurface;
import spacegraph.space2d.widget.windo.Windo;
import spacegraph.util.MutableFloatRect;
import spacegraph.video.Draw;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 2D directed/undirected graph widget
 * designed for high-performance realtime animated visualization of many graph nodes and edges
 * that can appear, disappear, and re-appear between frames
 *
 * TODO generify for use in Dynamics3D
 */
public class Graph2D<X> extends MutableMapContainer<X, Graph2D.NodeVis<X>> {


    private final AtomicBoolean busy = new AtomicBoolean(false);


    //public AtomicFloat scale = new AtomicFloat(1);


    private List<Graph2DRenderer<X>> renderers = new FasterList<>();

//    private final DequePool<NodeVis<X>> nodePool = new DequePool<>() {
//        @Override
//        public NodeVis<X> create() {
//            NodeVis<X> v = new NodeVis<>();
//            v.start(Graph2D.this);
//            return v;
//        }
//    };
    private final MetalPool<EdgeVis<X>> edgePool = new MetalPool<EdgeVis<X>>() {
        @Override
        public EdgeVis<X> create() {
            return new EdgeVis<>();
        }

        @Override
        public void put(EdgeVis<X> i) {
            i.clear();
            super.put(i);
        }
    };

    final MRUMap<X, NodeVis<X>> nodeCache = new MRUMap<X,NodeVis<X>>(8 * 1024) {
        @Override
        protected void onEvict(Map.Entry<X, NodeVis<X>> entry) {

            NodeVis<X> s = entry.getValue();
            if (s.id==null)
                s.stop();
        }
    }; //TODO set capacity good


    @Override
    protected void hide(NodeVis<X> key, Surface s) {
        s.hide();
    }

    private volatile Graph2DUpdater<X> updater = NullUpdater;

    final static Graph2DUpdater NullUpdater = (c, d) -> {
    };

    private final transient Set<X> wontRemain = new ArrayHashSet();

    private final Consumer<NodeVis<X>> DEFAULT_NODE_BUILDER = x -> x.set(
            new Scale(new PushButton(x.id.toString()), 0.75f)
    );
    private transient Consumer<NodeVis<X>> builder = DEFAULT_NODE_BUILDER;

    public Graph2D() {
        this(NullUpdater);
    }

    public Graph2D(Graph2DUpdater<X> updater) {
        super();
        update(updater);
    }
    public Surface widget() {
        return widget(null);
    }

    /** TODO impl using MetaFrame menu */
    public Surface widget(Object controls) {
        Gridding cfg = configWidget();

        if (controls!=null) {
            cfg.add(new ObjectSurface(controls));
        }

        addControls(cfg);

        return new Splitting(new Clipped(
                this
        ) {
            @Override
            protected void paintIt(GL2 gl, SurfaceRender r) {

                gl.glColor4f(0,0,0, 0.9f);
                Draw.rect(bounds, gl);

                super.paintIt(gl, r);
            }
        }, cfg, 0.1f);
    }

//    @Override
//    public void renderContents(GL2 gl, SurfaceRender r) {
//        SurfaceRender rr = r.clone(scale.floatValue(), new v2()); //HACK
//        super.renderContents(gl, rr);
//    }

    protected void addControls(Gridding cfg) {

    }

    /**
     * adds a rendering stage.  these are applied successively at each visible node
     */
    public Graph2D<X> render(Graph2DRenderer<X>... renderStages) {
        renderers = new FasterList<>(renderStages);
        //Collections.addAll(renderers, stages);
        return this;
    }

    public Graph2D<X> build(Consumer<NodeVis<X>> builder) {
        this.builder = builder;
        return this;
    }

    /**
     * TODO to support dynamically changing updater, apply the updater's init procdure to all existing nodes.  do this in between frames so there is no interruption if rendering current frame.  this means saving a 'nextUpdater' reference to delay application
     */
    public Graph2D<X> update(Graph2DUpdater<X> u) {
        this.updater = u;
        layout();
        return this;
    }

    public Gridding configWidget() {
        Gridding g = new Gridding();
        g.add(new ObjectSurface(updater));
        for (Graph2DRenderer l : renderers) {
            g.add(new ObjectSurface(l));
        }
        return g;
    }

    public int nodes() {
        return cells.size();
    }

    @Override
    protected boolean prePaint(SurfaceRender r) {
        if (super.prePaint(r)) {
            if (busy.compareAndSet(false, true)) {
                try {
                    updater.update(this, r.dtMS);
                } finally {
                    busy.set(false);
                }
            }
            return true;
        }
        return false;
    }


    @Override
    protected void paintIt(GL2 gl, SurfaceRender r) {
        cells.forEachValue(n -> {
            if (n.visible())
                n.paintEdges(gl);
        });
    }

    public final Graph2D<X> add(Stream<X> nodes) {
        return add(nodes::iterator);
    }

    public final Graph2D<X> set(Stream<X> nodes) {
        return set(nodes::iterator);
    }

    public final Graph2D<X> add(Iterable<X> nodes) {
        return update(nodes, true);
    }

    public final Graph2D<X> set(Iterable<X> nodes) {
        return update(nodes, false);
    }

    @Override
    public void clear() {

        set(List.of());
    }

    private Graph2D<X> update(Iterable<X> nodes, boolean addOrReplace) {

        if (!busy.compareAndSet(false, true)) {
            return this;
        }
        try {

            updateNodes(nodes, addOrReplace);

            render();

        } finally {
            busy.set(false);
        }

        return this;
    }

    @Override
    protected void stopping() {
        super.stopping();
        //synchronized (this) {
            nodeCache.values().forEach(Container::stop);
            nodeCache.clear();
            edgePool.delete();
        //}
    }

    private void updateNodes(Iterable<X> nodes, boolean addOrReplace) {
        if (!addOrReplace) {
            cells.map.forEach((k, v) -> wontRemain.add(k));
        }


        nodes.forEach(x -> {
            CellMap.CacheCell<X, NodeVis<X>> xxx =
                    compute(x, xx -> xx == null ? materialize(x) : rematerialize(xx));

            NodeVis<X> cv = xxx.value;
            if (cv.parent == null)
                cv.start(this);

            cv.show();
        });

        if (!wontRemain.isEmpty()) {
            cells.removeAll(wontRemain);
            wontRemain.clear();
        }
    }


    private Graph2D.NodeVis<X> materialize(X x) {
        NodeVis<X> yy = nodeCache.computeIfAbsent(x, x0 -> {
            NodeVis<X> y = new NodeVis();
            y.start(x0);
            builder.accept(y);
            return y;
        });
        yy.id = x; //in case x different instance but equal
        updater.init(this, yy);
//        yy.show();
        return yy;
//        xx = nodePool.get();
//        xx.start(x);
//        builder.accept(xx);
//        updater.init(this, xx);
//        //xx.show();
//        return xx;
    }

    /**
     * node continues being materialized
     */
    private NodeVis<X> rematerialize(NodeVis<X> xx) {
        xx.update();
        wontRemain.remove(xx.id);
        return xx;
    }

    @Override
    protected void unmaterialize(NodeVis<X> v) {
        v.end(edgePool);
//        nodePool.put(v);
    }

    private void render() {
        renderers.forEach(layer -> layer.render(cells,edit));
    }


    /**
     * wraps all graph construction procedure in this interface for which layers construct graph with
     */
    public static final class GraphEditing<X> {

        public final Graph2D<X> graph;

        GraphEditing(Graph2D<X> g) {
            this.graph = g;
        }

        /**
         * adds a visible edge between two nodes, if they exist and are visible
         */
        public @Nullable EdgeVis<X> edge(Object from, Object to) {
            @Nullable NodeVis<X> fromNode = graph.cells.get(from);
            return fromNode != null ? edge(fromNode, to) : null;
        }

        /**
         * adds a visible edge between two nodes, if they exist and are visible
         */
        public @Nullable EdgeVis<X> edge(NodeVis<X> from, Object to) {

            @Nullable NodeVis<X> t = graph.cells.getValue(to);
            if (t == null)
                return null;

            if (from == t) {
//                if (Param.DEBUG)
//                    throw new TODO("self referencing edges not supported yet");
//                else
                    return null; //ignored
            }

            return from.out(t, graph.edgePool);
        }

    }

    private final GraphEditing<X> edit = new GraphEditing<>(this);


    /**
     * iterative animated geometric update; processes the visual representation of the content
     */
    @FunctionalInterface public interface Graph2DUpdater<X> {

        void update(Graph2D<X> g, int dtMS);

        /**
         * set an initial location (and/or size) for a newly created NodeVis
         */
        default void init(Graph2D<X> g, NodeVis<X> newNode) {
//            float gw = g.w();
//            float gh = g.h();
//            int count = g.cellMap.cache.size();
//            float defaultSize = (float) (Math.min(gw, gh) / Math.sqrt(count + 1));
//
//            n.pos(RectFloat2D.XYWH(
//                    g.x() + (gw / 2 - gw / 4) + (float) Math.random() * gw / 2f,
//                    g.y() + (gh / 2 - gh / 4) + (float) Math.random() * gh / 2f,
//                    defaultSize, defaultSize
//            ));
        }
    }


    /**
     * one of zero or more sequentially-applied "layers" of the representation of the graph,
     * responsible for materializing/decorating/rendering each individual node it is called for,
     * and the graph that holds it (including edges, etc) via the supplied GraphEditing interface.
     */
    @FunctionalInterface
    public interface Graph2DRenderer<X> {

        /**
         * called for each node being processed.  can edit the NodeVis
         * and generate new links from it to target nodes.
         */
        void node(NodeVis<X> node, GraphEditing<X> graph);

        default void render(CellMap<X, NodeVis<X>> cells, GraphEditing<X> edit) {
            cells.forEachValue(nv -> {
                if (nv.visible())
                    node(nv, edit);
//            else
//                throw new WTF();
            });
        }

    }

    public static class NodeVis<X> extends Windo {

        public transient volatile X id; //TODO WeakReference

        /**
         * optional priority component
         */
        public float pri;

        /**
         * current layout movement instance
         */
        public /*volatile*/ transient MutableFloatRect mover = null;

        /**
         * outgoing edges
         */
        public final ConcurrentFastIteratingHashMap<X, EdgeVis<X>> outs = new ConcurrentFastIteratingHashMap(new EdgeVis[0]);

        private float r, g, b, a;

        void start(X id) {
            this.id = id;
            pri = 0.5f;
            r = g = b = 0.5f;
        }

        void end(Pool<EdgeVis<X>> edgePool) {
            hide();
            removeOuts(edgePool);
            this.mover = null;
//            clear();
            this.id = null;
        }



        //        @Override
//        public boolean stop() {
//            if (super.stop()) {
//                return true;
//            }
//            return false;
//        }

        void paintEdges(GL2 gl) {
            outs.forEachValue(x -> x.draw(this, gl));
        }

        @Override
        protected void paintIt(GL2 gl, SurfaceRender r) {
            gl.glColor4f(this.r, g, b, a);
            Draw.rect(bounds, gl);
        }

        public void color(float r, float g, float b) {
            color(r,g,b,1);
        }

        public boolean pinned() {

            return false;
        }

        private void removeOuts(Pool<EdgeVis<X>> pool) {
            outs.clear(pool::put);
        }

//        private boolean removeOut(EdgeVis<X> x, Pool<EdgeVis<X>> pool) {
//            EdgeVis<X> y = outs.remove(x.to.id);
//            if (y != null) {
//                pool.put(y);
//                return true;
//            }
//            return false;
//        }

        /**
         * adds or gets existing edge
         */
        private EdgeVis<X> out(NodeVis<X> target, Pool<EdgeVis<X>> pool) {

            X tid = target.id;
            if (tid == null)
                return null;

            EdgeVis<X> y = outs.compute(tid, (tt, yy) -> {
                if (yy == null) {
                    yy = pool.get();
                    yy.to = target;
                }
                return yy;
            });
            y.invalid = false;
            return y;
        }

        public void update() {
            //remove dead edges, or edges to NodeVis's which have been recycled after removal
            outs.removeIf((x, e) -> {
                if (e.invalid) return true;
                NodeVis<X> ee = e.to;
                return ee == null || !x.equals(ee.id);
            });
        }

        protected void invalidateEdges() {
            outs.forEachValue(e -> {
                e.invalid = true;
            });
        }

        public void color(float r, float g, float b, float a) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }
    }

    public static class EdgeVis<X> {
        volatile public NodeVis<X> to;

        public volatile boolean invalid;

        float r, g, b, a;
        public float weight;
        volatile public EdgeVisRenderer renderer;

        public EdgeVis() {
            clear();
        }

        public void clear() {
            invalid = true;
            r = g = b = 0f;
            a = 0.75f;
            to = null;
            weight = 1f;
            renderer = EdgeVisRenderer.Triangle;
        }



//        protected void merge(EdgeVis<X> x) {
//            weight += x.weight;
//            r = Util.or(r, x.r);
//            g = Util.or(g, x.g);
//            b = Util.or(b, x.b);
//            a = Util.or(a, x.a);
//        }

        enum EdgeVisRenderer {
            Line {
                @Override
                public void render(EdgeVis e, NodeVis from, GL2 gl) {
                    float x = from.cx(), y = from.cy();
                    gl.glLineWidth(1f + e.weight * 4f);
                    e.color(gl);
                    NodeVis to = e.to;
                    Draw.line(x, y, to.cx(), to.cy(), gl);
                }
            },
            Triangle {
                @Override
                public void render(EdgeVis e, NodeVis from, GL2 gl) {

                    NodeVis to = e.to;
                    if (to == null)
                        return;


                    float scale = Math.min(from.w(), from.h());
                    float base = Util.lerp(e.weight, scale / 2, scale);

                    e.color(gl);
                    float fx = from.cx(), fy = from.cy();
                    float tx = to.cx(), ty = to.cy();
                    Draw.halfTriEdge2D(fx, fy, tx, ty, base, gl);

                }
            };

            abstract public void render(EdgeVis e, NodeVis from, GL2 gl);
        }

        private void color(GL2 gl) {
            gl.glColor4f(r, g, b, a);
        }


        public EdgeVis<X> weight(float w) {
            weight = w;
            return this;
        }

        public EdgeVis<X> weightAddLerp(float w, float rate) {
            this.weight = Util.lerp(rate, this.weight, this.weight + w);
            return this;
        }
        public EdgeVis<X> weightLerp(float w, float rate) {
            this.weight = Util.lerp(rate, this.weight, w);
            return this;
        }

        public EdgeVis<X> color(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
            return this;
        }

        public EdgeVis<X> colorLerp(float r, float g, float b /* TODO type */, float rate) {
            if (r==r) this.r = Util.lerp(rate, this.r, r);
            if (g==g) this.g = Util.lerp(rate, this.g, g);
            if (b==b) this.b = Util.lerp(rate, this.b, b);
            return this;
        }
        public EdgeVis<X> colorAdd(float r, float g, float b /* TODO type */, float rate) {
            if (r==r) this.r = Util.lerp(rate, this.r, r + this.r);
            if (g==g) this.g = Util.lerp(rate, this.g, g + this.g);
            if (b==b) this.b = Util.lerp(rate, this.b, b + this.b);
            return this;
        }

        final void draw(NodeVis<X> from, GL2 gl) {

            NodeVis<X> t = this.to;
            if (t == null || !t.visible())
                return;

            renderer.render(this, from, gl);
        }
    }

    /**
     * layer which renders NodeGraph nodes and edges
     */
    public static class NodeGraphRenderer<N, E> implements Graph2DRenderer<N> {
        @Override
        public void node(NodeVis<N> node, GraphEditing<N> graph) {
            if (node.id instanceof Node) {
                node.color(0.5f, 0.5f, 0.5f);
                node.move((float) Math.random() * 100, (float) Math.random() * 100);
                node.size(20f, 10f);

                Node<N, E> nn = (Node<N, E>) node.id;
                nn.edges(false, true).forEach((e) -> {
                    Graph2D.EdgeVis<N> ee = graph.edge(node, e.other(nn));
                    ee.weight= 0.1f;
                    ee.a = 1;
                    ee.r = ee.g = ee.b = 0.5f;
                });

            }
        }
    }

    /**
     * invalidates all edges by setting their dirty flag
     */
    protected static Graph2DRenderer InvalidateEdges = (n, g) -> {
        n.invalidateEdges();
    };

}
