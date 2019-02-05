package spacegraph.space2d;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jogamp.opengl.GL2;
import jcog.Util;
import jcog.io.Grok;
import jcog.net.UDPeer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spacegraph.SpaceGraph;
import spacegraph.space2d.container.Bordering;
import spacegraph.space2d.container.graph.Timeline2D;
import spacegraph.space2d.container.graph.Timeline2D.SimpleEvent;
import spacegraph.space2d.container.grid.Gridding;
import spacegraph.space2d.container.unit.Scale;
import spacegraph.space2d.widget.button.CheckBox;
import spacegraph.space2d.widget.button.PushButton;
import spacegraph.video.Draw;

import java.io.IOException;
import java.util.Date;

/** stand-alone local/remote log consumer and visualization
 *
 * see:
 *  https:
 * */
public class SpaceLog {

    /** aux logger, for console or another downstream target */
    final Logger logger;

    final UDPeer udp;

    final Grok grok = Grok.all();

    /** time buffer */
    final Timeline2D.FixedSizeTimelineModel time =
            new Timeline2D.FixedSizeTimelineModel(512);

    public SpaceLog() throws IOException {
        this(0);
    }

    public SpaceLog(int port) throws IOException {
        this.udp = new UDPeer(port);
        this.udp.receive.on(this::input);
        this.udp.setFPS(20f);

        logger = LoggerFactory.getLogger(SpaceLog.class.getSimpleName() + "@" + udp.name());


    }

    protected void input(UDPeer.MsgReceived m) {
        
        byte[] data = m.data();
        input(m.from, data);
    }

    public void input(Object origin, byte[] data) {
        
        try {
            JsonNode x = Util.fromBytes(data, JsonNode.class);
            if (input(origin, x))
                return;
        } catch (IOException j) {
        }

        
            try {
                Object x = Util.fromBytes(data, Object.class);
                input(origin, x);
            } catch (IOException e) {
                
                String s = new String(data);
                Grok.Match ms = grok.capture(s);
                if (!ms.isNull()) {
                    logger.info("recv: {}\n{}", origin, ms.toMap());
                }
            }

    }

    public void input(Object origin, Object x) {
        if (x instanceof JsonNode) {
            if (input(origin, ((JsonNode)x)))
                return;
        }
        long now = System.nanoTime();
        time.add(new Timeline2D.SimpleEvent(x.toString(), now, now+1_000_000_000));
        logger.info("recv: {}\n\t{}", origin, x);
    }
    public boolean input(Object origin, JsonNode x) {

        
        if (x instanceof ArrayNode) {
            x.forEach(e -> {
               input(origin, e);
            });
            return true;
        }

        JsonNode id = x.get("_");
        if (id!=null) {
            long s = x.get("t").get(0).asLong();
            long e = x.get("t").get(1).asLong();
            Timeline2D.SimpleEvent event = new Timeline2D.SimpleEvent(id.asText(), s, e);
            time.add(event);
            //logger.info("recv: {}\n\t{}", origin, event);
            return true;
        }

        return false;
    }

    protected void gui() {

//
//        IRL i = new IRL(User.the());
//        i.load(-80.65, 28.58, -80.60, 28.63);

        //Surface space = new OsmSpace(i.osm).surface();


        Surface timeline = new Timeline2D<SimpleEvent>(time,
                e -> e.set(new Scale(
                        new PushButton(e.id.name) {
                            final int eHash = e.id.name.hashCode();

                            @Override
                            protected void paintIt(GL2 gl, SurfaceRender r) {
                                Draw.colorHashRange(gl, eHash, 0.3f, 0.6f, 0.8f);
                                Draw.rect(bounds, gl);
                            }
                        }, 0.8f))) {

            boolean autoNow = true;

            @Override
            protected void paintIt(GL2 gl, SurfaceRender r) {
                gl.glColor3f(0, 0, 0.1f);
                Draw.rect(bounds, gl);
            }

            @Override
            public Bordering controls() {
                Bordering b = super.controls();
                b.west(new CheckBox("Auto").on(autoNow).on(x -> autoNow = x));
                return b;
            }

            @Override
            protected boolean prePaint(SurfaceRender r) {
                if (autoNow && !time.isEmpty()) {
                    double when = System.nanoTime();
                    double range = tEnd - tStart;
                    assert (range > 0);
                    SimpleEvent lastEvent = time.last();
                    double end = Math.min(lastEvent.end + lastEvent.range() / 2, when);
                    double start = end - range;
                    view(start, end /* TODO: false unless new data */);
                    
                }
                return super.prePaint(r);
            }
        }.view(0, 15_000_000_000L /* ns */).withControls();
        SpaceGraph.window(new Gridding(
                //new Clipped(space),
                timeline), 800, 600);

    }

    public static void main(String[] args) throws IOException {
        SpaceLog s = new SpaceLog();

//        Loop.of(new DummyLogGenerator(new UDPeer())).setFPS(0.75f);
//        Loop.of(new DummyLogGenerator(new UDPeer())).setFPS(0.2f);

//        new FSWatch("/tmp", (p)-> {
//            s.input("/tmp", p);
//        }).setFPS(1);

        s.gui();
    }



    private static class DummyLogGenerator implements Runnable {

        private final UDPeer out;

        public DummyLogGenerator(UDPeer udPeer) {
            this.out = udPeer;
            out.setFPS(10f);
        }

        @Override
        public void run() {
            

            try {
                out.tellSome("my time is " + new Date(), 3, false);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }
}
