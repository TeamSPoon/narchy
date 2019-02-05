package spacegraph.space2d.widget;

import com.jogamp.opengl.GL2;
import spacegraph.SpaceGraph;
import spacegraph.space2d.SurfaceRender;
import spacegraph.space2d.container.graph.Timeline2D;
import spacegraph.space2d.container.graph.Timeline2D.SimpleEvent;
import spacegraph.space2d.container.unit.Scale;
import spacegraph.space2d.widget.button.PushButton;
import spacegraph.video.Draw;

public class Timeline2DTest {

    public static void main(String[] args) {

        Timeline2D.SimpleTimelineModel dummyModel = new Timeline2D.SimpleTimelineModel();
        int events = 30;
        int range = 50;
        for (int i = 0; i < events; i++) {
            long start = (long) (Math.random()* range);
            long length = (long) (Math.random()*10)+1;
            dummyModel.add(new Timeline2D.SimpleEvent("x" + i, start, start+length));
        }



        SpaceGraph.window(new Timeline2D<SimpleEvent>(dummyModel, e -> e.set(new Scale(new PushButton(e.id.name), 0.8f))) {
            @Override
            protected void paintIt(GL2 gl, SurfaceRender r) {
                gl.glColor3f(0.1f, 0, 0.1f);
                Draw.rect(bounds, gl);
            }
        }.view(0, range+1).withControls(), 800, 600);
    }
}