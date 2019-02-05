package spacegraph.space2d.widget.meta;

import com.jogamp.opengl.GL2;
import spacegraph.space2d.Surface;
import spacegraph.space2d.SurfaceRender;
import spacegraph.space2d.widget.text.VectorLabel;
import spacegraph.video.Draw;

import java.util.function.Function;

/** decoration inference for dynamic representational surfaces of arbitrary objects */
abstract public class IconBuilder<X> implements Function<X,Surface> {

    public static final IconBuilder<Object> simpleBuilder = new IconBuilder<Object>() {

        @Override
        public Surface apply(Object o) {
            return new VectorLabel(o.toString()) {

                final int classHash = o.getClass().hashCode();

                @Override
                protected void paintIt(GL2 gl, SurfaceRender r) {
                    super.paintIt(gl, r);
                    Draw.colorHash(gl, classHash);
                    Draw.rect(bounds, gl);
                }
            };
        }
    };

}
