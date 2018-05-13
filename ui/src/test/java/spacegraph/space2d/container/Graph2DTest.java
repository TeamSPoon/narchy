package spacegraph.space2d.container;

import jcog.data.graph.MapNodeGraph;
import jcog.data.graph.NodeGraph;
import org.ujmp.core.Matrix;
import org.ujmp.core.util.matrices.SystemEnvironmentMatrix;
import spacegraph.SpaceGraph;
import spacegraph.space2d.widget.Graph2D;

import java.io.IOException;

public class Graph2DTest {


    static class Graph2DTest1 {
        public static void main(String[] args) {


            MapNodeGraph<Object,Object> h = new MapNodeGraph();
            h.addNode(("x"));
            h.addNode(("y"));
            h.addNode(("z"));
            h.addNode(("w"));
//            for (int i = 0; i < 100; i++)
//                h.addNode("_" + i);
            h.addEdge(("x"), ("xy"), ("y"));
            h.addEdge(("x"), ("xz"), ("z"));
            h.addEdge(("y"), ("yz"), ("z"));
            h.addEdge(("w"), ("wy"), ("y"));

            Graph2D<NodeGraph.Node<Object, Object>> sg = new Graph2D<NodeGraph.Node<Object, Object>>()

                .layout(new ForceDirected2D())

                .layer(new Graph2D.NodeGraphLayer())

                .set(h.nodes());


            SpaceGraph.window(sg, 800, 800);
        }

    }

    static class Ujmp1 {
        public static void main(String[] args) throws IOException {


            MapNodeGraph<Object,Object> h = new MapNodeGraph();

            SystemEnvironmentMatrix env = Matrix.Factory.systemEnvironment();
            h.addNode("env");
            env.forEach((k,v)->{
                h.addNode(v);
                h.addEdge("env",k,v);
            });


            Graph2D<NodeGraph.Node<Object, Object>> sg = new Graph2D<NodeGraph.Node<Object, Object>>()

                    .layout(new ForceDirected2D())

                    .layer(new Graph2D.NodeGraphLayer())

                    .set(h.nodes());


            SpaceGraph.window(sg, 800, 800);
        }

    }


}
