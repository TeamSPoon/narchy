package spacegraph.space2d.container;

import com.jogamp.newt.event.KeyEvent;
import com.jogamp.opengl.GL2;
import spacegraph.input.finger.Finger;
import spacegraph.space2d.Surface;
import spacegraph.space2d.SurfaceRender;
import spacegraph.util.math.v2;

public class EmptySurface extends Surface {

    public EmptySurface() {
        visible = false;
    }


    @Override
    public Surface visible(boolean b) {
        return this; 
    }


    @Override
    protected void paint(GL2 gl, SurfaceRender surfaceRender) {

    }

}
