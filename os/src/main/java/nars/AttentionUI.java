package nars;

import nars.agent.NAgent;
import nars.attention.AttNode;
import nars.gui.DurSurface;
import spacegraph.space2d.Surface;
import spacegraph.space2d.container.graph.Graph2D;
import spacegraph.space2d.container.grid.Gridding;
import spacegraph.space2d.container.layout.ForceDirected2D;
import spacegraph.space2d.widget.meta.ObjectSurface;
import spacegraph.space2d.widget.text.VectorLabel;
import spacegraph.util.MutableFloatRect;

public class AttentionUI {

    static class NodeUI extends Gridding {
        public final AttNode node;

        NodeUI(AttNode node) {
            this.node = node;
            add(new VectorLabel(node.toString()));
            add(new ObjectSurface(node));
        }
    }

    public static Surface attentionGraph(NAR n, NAgent a) {
        AttNode aa = a.attn;
        Graph2D<AttNode> aaa = new Graph2D<AttNode>()
                .render(new Graph2D.Graph2DRenderer<AttNode>() {
                    @Override
                    public void node(Graph2D.NodeVis<AttNode> node, Graph2D.GraphEditing<AttNode> graph) {
                        node.id.children.forEach(c -> {
                            Graph2D.EdgeVis<AttNode> e = graph.edge(node, c);
                            e.weight(1f);
                            e.color(0.5f, 0.5f, 0.5f);
                        });
                        float s = node.id.pri.pri();
                        float d = 0.5f * node.id.boost.floatValue();
//                            float r = Math.min(1, s/d);
                        node.color(Math.min(d, 1), Math.min(s, 1), 0);
                        if (!(node.the() instanceof NodeUI)) {
                            node.set(new NodeUI(node.id));
                        }
                    }
                })
                .update(new ForceDirected2D<AttNode>() {
                    @Override
                    protected void size(MutableFloatRect<AttNode> m, float a) {
                        float q =
                                    m.node.id.pri.pri();

                        float s = (float)(Math.sqrt((Math.max(0, q))));
                        s = Math.max(Math.min(s, 2) * a, 32);
                        m.size(s, s);
                    }
                });
        return DurSurface.get(aaa.widget(), n, () -> aaa.set(aa.childrenStreamRecurse()));
    }
}
