package spacegraph.widget.text;

import spacegraph.Surface;
import spacegraph.layout.Splitting;

public class LabeledPane extends Splitting {

    public LabeledPane(String title, Surface content) {
        super(new Label(title), content, 0.9f);
    }

}
