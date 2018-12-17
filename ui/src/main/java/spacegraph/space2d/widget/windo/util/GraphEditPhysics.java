package spacegraph.space2d.widget.windo.util;

import com.jogamp.opengl.GL2;
import spacegraph.space2d.Surface;
import spacegraph.space2d.SurfaceRender;
import spacegraph.space2d.container.EmptySurface;
import spacegraph.space2d.widget.port.util.Wire;
import spacegraph.space2d.widget.windo.GraphEdit;
import spacegraph.space2d.widget.windo.Link;
import spacegraph.space2d.widget.windo.Windo;

public abstract class GraphEditPhysics {

    protected GraphEdit<?> graph = null;

    transient public Surface surface = new EmptySurface();

    abstract public void add(Windo w);

    abstract public void remove(Windo w);

    public final Surface start(GraphEdit parent) {
        this.graph = parent;
        return starting(graph);
    }
    abstract protected Surface starting(GraphEdit<?> graph);

    abstract public void stop();

    public abstract Link link(Wire w);


}
