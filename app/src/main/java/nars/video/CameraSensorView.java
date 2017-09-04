package nars.video;

import jcog.Util;
import jcog.event.On;
import nars.NAR;
import nars.NAgent;
import nars.concept.BaseConcept;
import nars.truth.Truth;
import spacegraph.render.Draw;
import spacegraph.widget.meter.BitmapMatrixView;

import java.util.function.Consumer;

/**
 * displays a CameraSensor pixel data as perceived through its concepts (belief/goal state)
 * monochrome
 */
public class CameraSensorView extends BitmapMatrixView implements BitmapMatrixView.ViewFunction2D, Consumer<NAgent> {

    private final Sensor2D cam;
    private final NAR nar;
    private final On<NAgent> on;
    private float maxConceptPriority;
    private long now;
    int dur;

    public CameraSensorView(Sensor2D cam, NAgent a) {
        super(cam.width, cam.height);
        this.cam = cam;
        this.nar = a.nar;
        this.dur = nar.dur();
        on = a.onFrame(this);
    }

    @Override
    public void stop() {
        on.off();
        super.stop();
    }


    @Override
    public void accept(NAgent nn) {
        now = nar.time();
        dur = nar.dur();
        maxConceptPriority = 1;
        update();
//            nar instanceof Default ?
//                ((Default) nar).focus.active.priMax() :
//                1; //HACK TODO cache this
    }

    @Override
    public int update(int x, int y) {


        long now = this.now;

        BaseConcept s = cam.matrix[x][y];
        Truth b = s.beliefs().truth(now, now, nar);
        float bf = b != null ? b.freq() : 0.5f;

        Truth d = s.goals().truth(now, now+dur, nar);
        float R = bf, G = bf, B = bf;
        if (d!=null) {
            float f = d.freq();
            //float c = d.conf();
            if (f > 0.5f) {
//                float z = (f - 0.5f) * 2f;
//                B -= z/2;
                G = f;
                B = 0;
            } else {
                R = f;
                B = 0;
//                float z = (0.5f - f) * 2f;
//                B += z/2;
//                G -= z/2;
            }
        }

//        float p = 1f;//nar.pri(s);
//        if (p!=p) p = 0;

        //p /= maxConceptPriority;



        return Draw.rgbInt(
                Util.unitize(R), Util.unitize(G), Util.unitize(B)
                /*, 0.5f + 0.5f * p*/);
    }
}
