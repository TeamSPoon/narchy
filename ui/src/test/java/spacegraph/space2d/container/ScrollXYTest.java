package spacegraph.space2d.container;

import com.jogamp.opengl.GL2;
import jcog.tree.rtree.rect.RectFloat;
import spacegraph.SpaceGraph;
import spacegraph.space2d.Surface;
import spacegraph.space2d.container.grid.DynGrid;
import spacegraph.space2d.container.grid.GridModel;
import spacegraph.space2d.container.grid.GridRenderer;
import spacegraph.space2d.container.grid.KeyValueGrid;
import spacegraph.space2d.container.unit.Clipped;
import spacegraph.space2d.widget.Widget;
import spacegraph.space2d.widget.button.CheckBox;
import spacegraph.space2d.widget.button.PushButton;
import spacegraph.space2d.widget.text.VectorLabel;
import spacegraph.space2d.widget.textedit.TextEdit;
import spacegraph.video.Draw;

import java.util.Map;

import static spacegraph.SpaceGraph.window;

class ScrollXYTest {


    static class ScrollGridTest1 {
        public static void main(String[] args) {

            GridModel<String> model = new GridModel<String>() {

                @Override
                public String get(int x, int y) {
                    return x + "," + y;
                }

                @Override
                public int cellsX() {
                    return 64;
                }

                @Override
                public int cellsY() {
                    return 64;
                }
            };

            ScrollXY<DynGrid<String>> grid = new ScrollXY<>(new DynGrid<>(model,
                    (x, y, s) -> {
                        if (Math.random() < 0.5f) {
                            Surface p = new PushButton(s) {
                                @Override
                                protected void paintWidget(RectFloat bounds, GL2 gl) {
                                    Draw.colorHash(gl, x ^ y, 0.2f, 0.3f, 0.85f);
                                    Draw.rect(bounds, gl);
                                }
                            };
                            return new Widget(p);
                        } else {
                            return new VectorLabel(s);
                        }
                    })).view(0,0,8,4);


//            grid.setScrollBar(true, true, false);
//            grid.setScrollBar(false, false, true);

            window(grid, 1024, 800);
        }
    }
    static class ListTest1 {
        public static void main(String[] args) {

            String[] list = {"a", "b", "c", "d", "e", "f"};

            GridRenderer<String> builder = (x, y, n) -> new CheckBox(n);

            SpaceGraph.window( ScrollXY.array(builder, list) , 800, 800);
        }

    }

    static class MapTest1 {
        public static void main(String[] args) {
            SpaceGraph.window(
                debugScroll(new DynGrid<>(
                        new KeyValueGrid(
                            Map.of("wtf", "ok", "sdfj", "xcv", "sdf", "fdfs")
                        ),
                        (x, y, n)->
                            x == 0 ? new VectorLabel(n.toString()) : new CheckBox(n.toString())))
                , 800, 800);
        }


    }

    private static Object debugScroll(ScrollXY.ScrolledXY x) {

        ScrollXY<ScrollXY.ScrolledXY> s = new ScrollXY<>(x);
        Surface debug = new TextEdit("x");
        return Splitting.row(new Clipped(s), 0.75f, debug);
    }
}