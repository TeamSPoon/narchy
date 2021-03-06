package spacegraph.space2d.widget.slider;

import com.jogamp.opengl.GL2;
import jcog.Util;
import jcog.exe.Exe;
import jcog.math.FloatRange;
import jcog.math.v2;
import jcog.pri.ScalarValue;
import jcog.tree.rtree.rect.RectFloat;
import org.eclipse.collections.api.block.procedure.primitive.FloatFloatProcedure;
import spacegraph.input.finger.Finger;
import spacegraph.input.finger.FingerDragging;
import spacegraph.space2d.Surface;
import spacegraph.space2d.SurfaceRender;
import spacegraph.space2d.container.Bordering;
import spacegraph.space2d.hud.HudHover;
import spacegraph.space2d.widget.port.FloatPort;
import spacegraph.space2d.widget.text.VectorLabel;
import spacegraph.video.Draw;

import static jcog.Texts.n4;
import static spacegraph.space2d.container.Bordering.E;
import static spacegraph.space2d.container.Bordering.S;

/**
 * Created by me on 6/26/16.
 */
public class XYSlider extends Surface implements HudHover  {

    public static final int BUTTON = 0;
    private final v2 knob = new v2(0.5f, 0.5f);

    private FloatFloatProcedure change = null;
    private final float[] knobColor = new float[] { 0.75f, 0.75f, 0.75f };


    private static final float _low = 0.2f;
    private static final float _HIH = 0.8f;
    private static final float[] lefAlphaCorners = new float[] {_low, _HIH, _HIH, _low};
    private static final float[] rihAlphaCorners = new float[] {_HIH, _low, _low, _HIH};
    private static final float[] topAlphaCorners = new float[] {_HIH, _HIH, _low, _low};
    private static final float[] botAlphaCorners = new float[] {_low, _low, _HIH, _HIH};
    private boolean pressing;


    public XYSlider() {
        super();
        updated();
    }

    public XYSlider(FloatRange x, FloatRange y) {
        this();
        set(x.floatValue(), y.floatValue());
        on((xx,yy)->{
           x.setProportionally(xx); y.setProportionally(yy);
        });
    }

    @Override
    public Surface hover(RectFloat screenBounds, Finger f) {
        return caption().pos(screenBounds.scale(0.75f));
    }

    /** creates a live-updating label */
    public Surface caption() {
        return new VectorLabel() {
            @Override
            protected boolean prePaint(SurfaceRender r) {
                text(summary());
                return super.prePaint(r);
            }
        };
    }

    /** TODO optional labels for x and y axes */
    public String summary() {
        return summaryX(knob.x) + ", " + summaryY(knob.y);
    }

    public String summaryX(float x) {
        return n4(x);
    }

    public String summaryY(float y) {
        return n4(y);
    }

    public XYSlider on(FloatFloatProcedure change) {
        this.change = change;
        return this;
    }

    final FingerDragging drag = new FingerDragging(BUTTON) {

        @Override
        protected boolean startDrag(Finger f) {
            pressing = true;
            return super.startDrag(f);
        }

        @Override
        public void stop(Finger finger) {
            super.stop(finger);
            pressing = false;
        }

        @Override protected boolean drag(Finger f) {
            setPoint(f);
            return true;
        }
    };

    private void setPoint(Finger f) {
        v2 hitPoint = f.relativePos(XYSlider.this);
        if (hitPoint.inUnit()) {
            pressing = true;
            if (!Util.equals(knob.x, hitPoint.x) || !Util.equals(knob.y, hitPoint.y)) {
                knob.set(hitPoint);
                updated();
            }
        }
    }

    @Override
    public Surface finger(Finger f) {
        if (f.tryFingering(drag)) {
        } else if (f.pressing(drag.button)) {
            setPoint(f);
        }
        return this;
    }




    private void updated() {
        FloatFloatProcedure c = change;
        if (c!=null) {
            Exe.invokeLater(()->{
                c.value(knob.x, knob.y);
            });

        }
    }


    @Override
    protected void paint(GL2 gl, SurfaceRender surfaceRender) {

        Draw.rectRGBA(bounds, 0f, 0f, 0f, 0.8f, gl);

        float px = knob.x;
        float py = knob.y;

        float knobThick = pressing ? 0.08f : 0.04f;


        float bw = bounds.w;
        float bh = bounds.h;
        float KT = Math.min(bw, bh) * knobThick;
        float kw = bounds.x+(px* bw);
        float kh = bounds.y+(py* bh);
        float KTH = KT / 2;
        Draw.rectAlphaCorners(bounds.x, kh - KTH, kw - KTH, kh + KTH, knobColor, lefAlphaCorners, gl
        );
        Draw.rectAlphaCorners(kw + KTH, kh - KTH, bounds.x + bw, kh + KTH, knobColor, rihAlphaCorners, gl
        );

        Draw.rectAlphaCorners(kw - KTH, bounds.y, kw + KTH, kh- KTH, knobColor, botAlphaCorners, gl
        );
        Draw.rectAlphaCorners(kw - KTH, kh + KTH, kw + KTH, bounds.y + bh, knobColor, topAlphaCorners, gl
        );

        




    }

    public XYSlider set(float x, float y) {
        if (knob.setIfChanged(x, y, ScalarValue.EPSILON))
            updated();
        return this;
    }

    public Surface chip() {
        FloatPort px = new FloatPort();
        FloatPort py = new FloatPort();
        on((x,y)->{
            px.out(x);
            py.out(y);
        });

        Bordering b = new Bordering();
        b.center(this);
        b.set(S, px, 0.1f);
        b.set(E, py, 0.1f);
        return b;
    }
}
