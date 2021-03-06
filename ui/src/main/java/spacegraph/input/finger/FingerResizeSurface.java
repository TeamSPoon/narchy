package spacegraph.input.finger;

import jcog.math.v2;
import jcog.tree.rtree.rect.RectFloat;
import spacegraph.space2d.Surface;
import spacegraph.space2d.widget.windo.util.DragEdit;

/**
 * resizes a rectangular surface in one of the four cardinal or four diagonal directions
 */
public class FingerResizeSurface extends FingerResize {

    private final Surface resizing;

    public FingerResizeSurface(Surface target, DragEdit mode) {
        super(0, mode);
        this.resizing = target;
    }

    @Override
    protected v2 pos(Finger finger) {
        return finger.posOrtho.clone();
    }

    @Override
    protected RectFloat size() {
        return resizing.bounds;
    }

    @Override
    protected void resize(float x1, float y1, float x2, float y2) {
        resizing.pos(x1, y1, x2, y2);
    }

}
