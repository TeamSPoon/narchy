package spacegraph.widget.windo;

import com.jogamp.opengl.GL2;
import jcog.Util;
import jcog.data.graph.ImmutableDirectedEdge;
import jcog.data.graph.MapNodeGraph;
import jcog.data.graph.NodeGraph;
import jcog.event.On;
import jcog.math.random.XoRoShiRo128PlusRandom;
import jcog.tree.rtree.rect.RectFloat2D;
import org.eclipse.collections.api.tuple.Pair;
import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.collision.shapes.Shape;
import org.jbox2d.common.Transform;
import org.jbox2d.dynamics.*;
import org.jbox2d.dynamics.joints.Joint;
import org.jbox2d.dynamics.joints.RopeJoint;
import org.jbox2d.dynamics.joints.RopeJointDef;
import org.jbox2d.fracture.PolygonFixture;
import spacegraph.Ortho;
import spacegraph.Surface;
import spacegraph.SurfaceBase;
import spacegraph.ZoomOrtho;
import spacegraph.math.Tuple2f;
import spacegraph.math.v2;
import spacegraph.phys.util.Animated;
import spacegraph.render.Draw;
import spacegraph.render.SpaceGraphFlat;
import spacegraph.widget.button.CheckBox;

import java.util.Random;
import java.util.function.Supplier;

import static org.eclipse.collections.impl.tuple.Tuples.pair;

/**
 * wall which organizes its sub-surfaces according to 2D phys dynamics
 */
public class PhyWall extends Wall implements Animated {

    static final float SHAPE_SIZE_EPSILON = 0.0001f;

    final Dynamics2D W;
    private On on;

    /** TODO use more efficient graph representation */
    final MapNodeGraph<PhyWindow,RopeJoint> undirectedLinks = new MapNodeGraph();

    private PhyWall() {
        super();

        W = new Dynamics2D(new v2(0, 0));
        W.setParticleRadius(0.2f);
        W.setParticleDensity(1.0f);

        W.setAllowSleep(true);
        W.setContinuousPhysics(true);
        //W.setSubStepping(true);
    }

    /** HACK */
    public static PhyWall window(int width, int height) {
        PhyWall s = new PhyWall();
        s.pos(-1,-1,1,1);

        new SpaceGraphFlat(
                new ZoomOrtho(s) {

                    @Override
                    public boolean autoresize() {
                        zoom(s);
                        return false;
                    }

                }
        ).show(width, height);

        return s;
    }

    @Override
    public void start(SurfaceBase parent) {
        synchronized (this) {
            super.start(parent);
            on = ((Ortho) root()).onUpdate(this);
        }
    }


    @Override
    public boolean animate(float dt) {

        W.step(dt, 4, 4);

        return true;
    }

    @Override
    protected void paintBelow(GL2 gl) {
        super.paintBelow(gl);

        Dynamics2D w = this.W;

        for (Joint j = w.joints(); j != null; j = j.next)
            drawJoint(j, gl);

        for (Body2D b = w.bodies(); b != null; b = b.next())
            drawBody(b, gl);

    }

    private void drawJoint(Joint joint, GL2 g) {
        g.glColor4f(0.9f, 0.8f, 0.1f, 0.75f);
        g.glLineWidth(10f);
        Tuple2f v1 = new v2(), v2 = new v2();
        switch (joint.getType()) {
            default:
                joint.getAnchorA(v1);
                joint.getAnchorB(v2);
                break;
        }
        Draw.line(g, v1.x, v1.y, v2.x, v2.y);

    }

    private void drawBody(Body2D body, GL2 gl) {
//                    if (body.getType() == BodyType.DYNAMIC) {
//                        g.setColor(Color.LIGHT_GRAY);
//                    } else {
//                        g.setColor(Color.GRAY);
//                    }

        if (body.data() instanceof PhyWindow.WallBody) {
            return; //its rendered already via its Surface
        }

        //boolean active = body.isActive();
        boolean awake = body.isAwake();
        gl.glColor4f(0.5f, 0.5f, 0.5f, awake ? 0.5f : 0.3f);

        //Tuple2f v = new v2();
        //List<PolygonFixture> generalPolygons = new FasterList<>();
        for (Fixture f = body.fixtures; f != null; f = f.next) {
            PolygonFixture pg = f.polygon;
            if (pg != null) {
                //generalPolygons.add(pg);
            } else {
                Shape shape = f.shape();
                switch (shape.m_type) {
                    case POLYGON:

                        PolygonShape poly = (PolygonShape) shape;

                        gl.glBegin(GL2.GL_TRIANGLE_FAN);
                        int n = poly.vertices;
                        Tuple2f[] pv = poly.vertex;
                        float preScale = 1.1f;

                        for (int i = 0; i < n; ++i)
                            body.getWorldPointToGL(pv[i], preScale, gl);
                        body.getWorldPointToGL(pv[0], preScale, gl); //close

                        gl.glEnd();
                        break;
                    case CIRCLE:
//                                    CircleShape circle = (CircleShape) shape;
//                                    float r = circle.m_radius;
//                                    body.getWorldPointToOut(circle.m_p, v);
//                                    Point p = getPoint(v);
//                                    int wr = (int) (r * zoom);
//                                    g.fillOval(p.x - wr, p.y - wr, wr * 2, wr * 2);
                        break;
                    case EDGE:
//                                    EdgeShape edge = (EdgeShape) shape;
//                                    Tuple2f v1 = edge.m_vertex1;
//                                    Tuple2f v2 = edge.m_vertex2;
//                                    Point p1 = getPoint(v1);
//                                    Point p2 = getPoint(v2);
//                                    g.drawLine(p1.x, p1.y, p2.x, p2.y);
                        break;
                }
            }
        }

//                    if (generalPolygons.size() != 0) {
//                        PolygonFixture[] polygonArray = generalPolygons.toArray(new PolygonFixture[generalPolygons.size()]);
//                        for (PolygonFixture poly : polygonArray) {
//                            int n = poly.size();
//                            int x[] = new int[n];
//                            int y[] = new int[n];
//                            for (int i = 0; i < n; ++i) {
//                                body.getWorldPointToOut(poly.get(i), v);
//                                Point p = getPoint(v);
//                                x[i] = p.x;
//                                y[i] = p.y;
//                            }
//                            g.fillPolygon(x, y, n);
//                        }
    }


    @Override
    public void stop() {
        synchronized (this) {
            on.off();
            on = null;
            super.stop();
        }
    }



    final Random rng = new XoRoShiRo128PlusRandom(1);
    float rngPolar(float scale) {
        return //2f*(rng.nextFloat()*scale-0.5f);
                (float) rng.nextGaussian() * scale;
    }
    float rngNormal(float scale) {
        return rng.nextFloat() * scale;
    }

    public PhyWindow newWindow(Surface content, RectFloat2D initialBounds) {
        PhyWindow s = new PhyWindow(initialBounds);
        //s.children(new Scale(content, 1f - Windo.resizeBorder));
        add(s);
        s.add(content);

        return s;
    }




    class PhyWindow extends Windo {
        private final Body2D body;
        private final PolygonShape shape;
//        public final SimpleSpatial<String> spatial;

        PhyWindow(RectFloat2D initialBounds) {
            super();
            pos(initialBounds);

            this.shape = PolygonShape.box(initialBounds.w / 2, initialBounds.h / 2);

            FixtureDef fd = new FixtureDef(shape, 0.1f, 0f);
            fd.setRestitution(0.1f);
            W.addBody(this.body = new WallBody(), fd);

        }

//        @Override
//        protected Fingering fingering(DragEdit mode) {
//            Fingering f = super.fingering(mode);
//            if (f != null) {
////                spatial.body.clearForces();
////                spatial.body.angularVelocity.zero();
////                spatial.body.linearVelocity.zero();
//            }
//            return f;
//        }


        public Pair<PhyWindow, RopeJoint> sprout(Surface target, float scale) {
            return sprout(target, scale, 1f);
        }

        /**
         * convenience method for essentially growing a separate window
         * of a proportional size with some content (ex: a port),
         * and linking it to this window via a constraint.
         */
        public Pair<PhyWindow, RopeJoint> sprout(Surface target, float scale, float targetAspect) {
            float W = w();
            float H = h();
            float l = new v2(W,H).length();
            float w = W *scale;
            float h = H *scale;

            //TODO apply targetAspect in initial size of the target

            //min rope distance
            float minRadius = l/2 + l*scale/2;

            float a = rng.nextFloat()*2*(float)Math.PI;
            float dx = (float) (minRadius * Math.cos(a));
            float dy = (float) (minRadius * Math.sin(a));

            PhyWindow sprouted = newWindow(target,
                    RectFloat2D.XYWH(cx() + dx, cy() + dy, w, h));

            return link(sprouted);
        }


        /** convenience method for creating a basic undirected link joint.
         *  no endpoint is necessarily an owner of the other so
         *  it should not matter who is the callee.
         *
         *  duplicate links are prevented.
         */
        public Pair<PhyWindow, RopeJoint> link(PhyWindow target) {

            synchronized (undirectedLinks) {
                NodeGraph.MutableNode<PhyWindow, RopeJoint> me = undirectedLinks.addNode(this);

                Iterable<ImmutableDirectedEdge<PhyWindow, RopeJoint>> edges = me.edges(true, true);
                if (edges!=null) {
                    for (ImmutableDirectedEdge<PhyWindow, RopeJoint> e : edges) {
                        if (e.from.id == target || e.to.id == target)
                            return pair(target, e.id);
                    }
                }

                RopeJointDef jd = new RopeJointDef(target.body, this.body);
                jd.collideConnected = true;
                jd.maxLength = 1f; //should be effectively ignored by the implementation below

                RopeJoint link = new RopeJoint(PhyWall.this.W.pool, jd) {

                    float lengthScale = 1.3f;

                    @Override
                    public float targetLength() {
                        //maxLength will be proportional to the radii of the two bodies
                        //so that if either changes, the rope's behavior changes also
                        return ( (PhyWall.this.radius()/2f + target.radius()/2f) * lengthScale );
                    }
                };
                PhyWall.this.W.addJoint(link);

                undirectedLinks.addNode(target);
                undirectedLinks.addEdge(this, link, target);

                return pair(target, link);
            }
        }

        public void sproutBranch(String label, Supplier<Surface[]> children) {
            CheckBox toggle = new CheckBox(label);
            Pair<PhyWindow, RopeJoint> toggleWindo = sprout(toggle, 0.25f);
            toggle.on((cb, enabled)->{
                synchronized (toggle) {
                    if (enabled) {
                        Surface[] cc = children.get();
                        for (Surface x : cc) {
                            toggleWindo.getOne().sprout(x, +2f);
                        }
                    } else {
                        //TODO remove what is shown
                    }
                }
            });
        }


        private class WallBody extends Body2D {

            RectFloat2D physBounds = null;

            public WallBody() {
                super(new BodyDef(BodyType.DYNAMIC), PhyWall.this.W);

                setData(this);

                setFixedRotation(true);
                this.physBounds = bounds;
            }


            @Override
            public void preUpdate() {


                RectFloat2D r = bounds;
                if (physBounds == null || r!=physBounds) {

                        if (!Util.equals(r.w, physBounds.w, SHAPE_SIZE_EPSILON) ||
                            !Util.equals(r.h, physBounds.h, SHAPE_SIZE_EPSILON)) {
                            updateFixtures((f) -> {
                                //HACK assumes the first is the only one
                                //if (f.m_shape == shape) {
                                    f.setShape(shape.setAsBox(r.w / 2, r.h / 2));
                                //}


                            });
                        }


                    v2 target = new v2(r.cx(), r.cy());

                    if (setTransform(target, 0, EPSILON))
                       setAwake(true);
                }


            }

            @Override
            public void postUpdate() {


                Transform t = getXform();
                Tuple2f p = t.pos;
                //float rot = t.q.getAngle();

                float w = w(), h = h(); //HACK re-use the known width/height assumes that the physics engine cant change the shape's size

                RectFloat2D r = RectFloat2D.XYWH(p.x, p.y, w, h);
                if (!r.equals(physBounds, EPSILON)) {
                    pos(physBounds = r);
                }

            }
        }
    }

}
//    private volatile MouseJointDef mjdef;
//    private volatile MouseJoint mj;
//    private volatile boolean destroyMj = false;
//private volatile Tuple2f mousePosition = new Vec2();

//    private void initMouse() {
//                    addMouseWheelListener((MouseWheelEvent e) -> {
//                        if (e.getWheelRotation() < 0) {
//                            zoom *= 1.25f * -e.getWheelRotation();
//                        } else {
//                            zoom /= 1.25f * e.getWheelRotation();
//                        }
//
//                        zoom = Math.min(zoom, 100);
//                        zoom = Math.max(zoom, 0.1f);
//                        repaint();
//                    });

//                    addMouseMotionListener(new MouseMotionListener() {
//                        @Override
//                        public void mouseDragged(MouseEvent e) {
//                            Point p = e.getPoint();
//                            mousePosition = getPoint(p);
//                            if (clickedPoint != null) {
//                                p.x -= clickedPoint.x;
//                                p.y -= clickedPoint.y;
//                                center.x = startCenter.x - p.x / zoom;
//                                center.y = startCenter.y + p.y / zoom;
//                            } else {
//                                if (mj != null) {
//                                    mj.setTarget(mousePosition);
//                                }
//                            }
//                            if (!running) {
//                                repaint();
//                            }
//                        }
//
//                        @Override
//                        public void mouseMoved(MouseEvent e) {
//                            Point p = e.getPoint();
//                            mousePosition = getPoint(p);
//                            if (!running) {
//                                repaint();
//                            }
//                        }
//                    });

//                    addMouseListener(new MouseListener() {
//                        @Override
//                        public void mouseClicked(MouseEvent e) {
//                        }
//
//                        @Override
//                        public void mousePressed(MouseEvent e) {
//                            int x = e.getX();
//                            int y = e.getY();
//                            Point p = new Point(x, y);
//                            switch (e.getButton()) {
//                                case 3:
//                                    startCenter.set(center);
//                                    clickedPoint = p;
//                                    break;
//                                case 1:
//                                    Tuple2f v = getPoint(p);
//                                    /*synchronized(Tests.this)*/
//                                {
//                                    bodyFor:
//                                    for (Body2D b = w.bodies(); b != null; b = b.next) {
//                                        for (Fixture f = b.getFixtureList(); f != null; f = f.next) {
//                                            if (f.testPoint(v)) {
//                                                MouseJointDef def = new MouseJointDef();
//
//                                                def.bodyA = ground;
//                                                def.bodyB = b;
//                                                def.collideConnected = true;
//
//                                                def.target.set(v);
//
//                                                def.maxForce = 500f * b.getMass();
//                                                def.dampingRatio = 0;
//
//                                                mjdef = def;
//                                                break bodyFor;
//                                            }
//                                        }
//                                    }
//                                }
//
//                                break;
//                            }
//                        }
//
//                        @Override
//                        public void mouseReleased(MouseEvent e) {
//                            //synchronized (Tests.this) {
//                            switch (e.getButton()) {
//                                case 3:
//                                    clickedPoint = null;
//                                    break;
//                                case 1:
//                                    if (mj != null) {
//                                        destroyMj = true;
//                                    }
//                                    break;
//                            }
//                            //}
//                        }
//                    });
//        mj = null;
//        destroyMj = false;
//        mjdef = null;
//    }

