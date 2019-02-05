package spacegraph.space2d;

import com.jogamp.opengl.GL2;
import spacegraph.input.finger.Finger;
import spacegraph.input.finger.impl.NewtKeyboard;
import spacegraph.input.finger.impl.NewtMouseFinger;
import spacegraph.space2d.container.collection.MutableListContainer;
import spacegraph.space2d.hud.Ortho;
import spacegraph.space2d.hud.ZoomOrtho;
import spacegraph.video.JoglSpace;

public class SpaceGraphFlat extends JoglSpace {


    private ZoomOrtho zoom;
//    private final Ortho<MutableListContainer> hud;

    private final Finger finger;
    private final NewtKeyboard keyboard;

    public SpaceGraphFlat(Surface content) {
        super();

        finger = new NewtMouseFinger(this);

        keyboard = new NewtKeyboard(/*TODO this */);

        onReady(() -> {

            zoom = new ZoomOrtho(this, content, finger, keyboard) {
                @Override
                protected void starting() {
                    super.starting();
                    io.window.setPointerVisible(false); //HACK
                }
            };
            add(zoom);
            add(finger.zoomBoundsSurface(zoom.cam));
            add(finger.cursorSurface());
            //addOverlay(this.keyboard.keyFocusSurface(cam));

        Ortho<MutableListContainer> hud = new Ortho<MutableListContainer>(this, new MutableListContainer(),
                keyboard) {
            @Override
            protected boolean autosize() {
                return true;
            }

            @Override
            protected void paintIt(GL2 gl, SurfaceRender r) {
                gl.glPushMatrix();

                    gl.glLoadIdentity();
                    r.render(gl);

                gl.glPopMatrix();
            }
        };
        add(hud);
//        hud.content().addAt(new PushButton("x").pos(0, 0, 100f, 100f));


        });

    }

}
