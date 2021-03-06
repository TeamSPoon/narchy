package spacegraph.video;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLContext;
import jcog.data.list.FasterList;
import jcog.math.v2;
import jcog.tree.rtree.HyperRegion;
import jcog.tree.rtree.rect.HyperRectFloat;
import jcog.tree.rtree.rect.RectFloat;
import spacegraph.input.finger.Finger;
import spacegraph.input.finger.FingerMove;
import spacegraph.space2d.Surface;
import spacegraph.space2d.SurfaceRender;
import spacegraph.space2d.container.Stacking;
import spacegraph.space2d.widget.text.VectorLabel;
import spacegraph.util.geo.IRL;
import spacegraph.util.geo.osm.Osm;
import spacegraph.util.geo.osm.OsmElement;
import spacegraph.util.geo.osm.OsmWay;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static jcog.Texts.n4;

public class OsmSurface extends Surface {



    private final IRL index;

    private OsmSpace.LonLatProjection projection =
            new OsmSpace.RawProjection();
            //new OsmSpace.ECEFProjection();

    public final AtomicBoolean debugIndexBounds = new AtomicBoolean(false);


    final v2 translate = new v2();

    private transient GL2 gl;
    private List<OsmElement> hilight = new FasterList(128);

    public OsmSurface(IRL i) {
        this.index = i;
    }



    @Deprecated transient protected Osm o = null;

    @Override
    protected void paint(GL2 gl, SurfaceRender surfaceRender) {
        gl.glPushMatrix();

        gl.glTranslatef(
                translate.x + bounds.x + bounds.w/2,
                translate.y + bounds.y + bounds.h/2, 0); //center in view


        projection.transform(gl, bounds);

        {
            renderMap(gl);

            if (debugIndexBounds.get()) {
//                renderIndexBounds(gl);
                renderTouchedIndexBounds(gl);
            }

            if (gl != null) {
                hilight.forEach(each -> {
                    renderBounds(gl, each);
                });
            }
        }

        projection.untransform(gl, bounds);

        gl.glPopMatrix();

    }

    private void renderTouchedIndexBounds(GL2 gl) {
        index.index.root().intersectingNodes(HyperRectFloat.cube(touch, 0), n->{
            renderBounds(gl, n.bounds());
            return true;
        }, index.index.model);
    }

    private void renderIndexBounds(GL2 gl) {

        gl.glLineWidth(2);

        index.index.root().streamNodesRecursively().forEach(n -> {
            renderBounds(gl, n.bounds());
        });
    }

    private void renderBounds(GL2 gl, HyperRegion b) {
        if (b instanceof OsmWay)
            b = ((OsmWay)b).bounds();
        if (b instanceof HyperRectFloat) {
            HyperRectFloat r = (HyperRectFloat)b;
            rect(gl, r);
        }
    }

    private void rect(GL2 gl, HyperRectFloat r) {
        float x1 = r.min.coord(0), y1 = r.min.coord(1);
        float x2 = r.max.coord(0), y2 = r.max.coord(1);

        float[] ff = new float[3];
        projection.project(x1, y1, 0, ff, 0);
        x1 = ff[0];
        y1 = ff[1];
        projection.project(x2, y2, 0, ff, 0);
        x2 = ff[0];
        y2 = ff[1];

        Draw.colorHash(gl, r.hashCode(), 0.5f);
        //Draw.rect(
        Draw.rectStroke(
                x1, y1, x2-x1, y2-y1,
                gl
        );
    }

    private void renderMap(GL2 gl) {
        this.gl = gl;

        if (o !=null) {

            RectFloat b = o.geoBounds;

            if (b != null) {



                Consumer<GL2> renderProc;

                if (!o.ready)
                    renderProc = loading;
                else {
                    GLContext ctx = gl.getContext();
                    Object c = ctx.getAttachedObject(o.id);
                    if (projection.changed() && c!=null) {
                        //detach and create new
                        ctx.detachObject(o.id);
                        c = null;
                    }
                    if (c == null) {
                        c = new OsmSpace.OsmRenderer(gl, projection);
                        OsmSpace.OsmRenderer r = ((OsmSpace.OsmRenderer) c);
//                        HyperRectFloat viewBounds = new HyperRectFloat(
//                                new float[] { },
//                                new float[] { }
//                        );
                        o.ways.forEach(w -> r.addWay(w));
                        o.nodes.values().forEach(n -> r.addNode(n));
//                        //index.index.forEach(e -> {//whileEachIntersecting(viewBounds,e->{
//                            if (e instanceof OsmWay)
//                                r.addWay((OsmWay)e);
//                            else if (e instanceof OsmNode)
//                                r.addNode((OsmNode)e);
//                            //return true;
//                        });
                        ctx.attachObject(o.id, c);
                        projection.changeNoticed();
                    }
                    renderProc = (Consumer<GL2>) c;
                }

                renderProc.accept(gl);

                /* debug */ {
                    gl.glColor4f(0.5f, 0.5f, 0.5f, 0.5f);
                    Draw.rectFrame(gl, b.cx(), b.cy(),
                            b.w, b.h, 0.0001f);

                }

            }
        }
    }


    private static final Consumer<GL2> loading = (gl)->{
        gl.glColor3f(1, 0, 0);
        Draw.rectFrame(gl, 0, 0, 1, 1, 0.1f);
    };

    public OsmSurface go(Osm o) {
        this.o = o;
        projection.center(o.geoBounds.cx(), o.geoBounds.cy());
        return this;
    }

    public OsmSurface go(float lon, float lat, float lonRange, float latRange) {
        this.o = index.request(lon, lat, lonRange, latRange);
        projection.center(lon, lat);
        return this;
    }

    private class AnimLabel extends VectorLabel {
        final Supplier<String> text;

        public AnimLabel(Supplier<String> text) {
            this.text = text;
        }

        @Override
        protected boolean prePaint(SurfaceRender r) {
            text(text.get());
            return super.prePaint(r);
        }
    }

    final FingerMove pan = new FingerMove(0) {



        v2 prev = new v2();
        @Override
        protected boolean startDrag(Finger f) {
            prev.set(0,0);
            return super.startDrag(f);
        }

        @Override
        public void move(float tx, float ty) {
            projection.pan(tx - prev.x, ty - prev.y, bounds);
            prev.set(tx, ty);
        }
    };

    float touch[] = new float[3];


    @Override
    public Surface finger(Finger finger) {

        hilight.clear();


        float wheel;
        if ((wheel = finger.rotationY(true)) != 0) {
            projection.zoom(wheel);
            return this;
        }

        if (finger.tryFingering(pan)) {
            return this;
        } else {
            v2 pos = finger.posPixel;
            float wx = -bounds.w/2 + pos.x;
            float wy = -bounds.h/2 + pos.y;
            float wz = 0;

            //TODO unproject screen to world

            projection.unproject(wx, wy, wz, touch);
            System.out.println(n4(wx,wy,wz) + " -> " + n4(touch));

//            float[] untouch = new float[3];
//            projection.project(touch[0], touch[1], touch[2], untouch, 0);
//            System.out.println("  " + n4(untouch[0] - wx) + " " + n4(untouch[1] - wy));

            float rad = 0.0004f;
            HyperRectFloat cursor = HyperRectFloat.cube(touch, rad).scale(1,1,0);
            index.index.intersectsWhile(cursor, (each)->{
                if (each.tags!=null) {
                    //System.out.println(each.tags);
                    hilight.add(each);
                    each.contains(cursor);
                }
                return true;
            });

        }

        return null;
    }

    public Surface view() {
        return new Stacking(
                this
//                ,
//                new Bordering().south(
//                    Gridding.col(
//                        new AnimLabel(()->"translation: " + translate.toString()),
//                        new AnimLabel(()->"scale: " + scale.toString())
//                    )
//                )
        );
    }


}
