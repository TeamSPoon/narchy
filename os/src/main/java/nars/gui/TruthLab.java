package nars.gui;

import com.google.common.collect.Lists;
import com.jogamp.opengl.GL2;
import nars.*;
import nars.concept.Concept;
import nars.table.BeliefTable;
import nars.term.Compound;
import nars.truth.Truth;
import spacegraph.SpaceGraph;
import spacegraph.space2d.Surface;
import spacegraph.space2d.SurfaceRender;
import spacegraph.space2d.container.grid.Gridding;
import spacegraph.video.Draw;
import spacegraph.video.font.HersheyFont;

import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;

import static java.util.stream.Collectors.toList;
import static nars.$.$;
import static spacegraph.video.Draw.pop;
import static spacegraph.video.Draw.push;

/**
 * Tool for analyzing and tuning temporal dynamics among a set of specified concepts
 */
public class TruthLab extends Gridding {

    private final List<ConceptTimeline> views;
    private final NAR nar;

    private final List<Compound> concepts;

    /**
     * samples per frame
     */
    int samplePeriod = 1;

    long start, end;
    boolean showBeliefs = true;
    private static final boolean truthOrProjectedTaskTruth = false;

    public TruthLab(NAR n, Compound... x) {
        super(VERTICAL);
        start = n.time();
        this.nar = n;
        this.concepts = Lists.newArrayList(x);
        this.views = concepts.stream().map(xx -> new ConceptTimeline(xx, showBeliefs)).collect(toList());

        n.onCycle(this::update);

        update(n);
    }

    protected void update(NAR n) {

        this.end = Math.max(this.end, n.time());

        List<Surface> cc = $.newArrayList();
        views.forEach(l -> cc.addAll(l.update(n, start, end, samplePeriod)));
        set(cc);
        layout();
    }

    static class TruthTimeline extends Surface {

        float[] labelColor = new float[4];

        float timeScale = 0.05f;

        public String label = "?";

        
        final float[] data;
        final int samples;

        public TruthTimeline(long start, long end, int samplePeriod, IntFunction<Truth> eval) {
            this.data = new float[(int) Math.ceil(((double) (end - start)) / samplePeriod) * 3];

            int i = 0;
            int samples = 0;
            for (float occ = start; occ < end; occ += samplePeriod) {

                data[i++] = occ;

                Truth t = eval.apply(Math.round(occ));
                float f;
                f = t != null ? t.freq() : 0.5f;

                data[i++] = f;

                float c;
                c = t != null ? t.conf() : -1;

                data[i++] = c;

                
                

                samples++;
            }
            this.samples = samples;
        }


        @Override
        protected void paint(GL2 gl, SurfaceRender surfaceRender) {
            float sw = 0.9f * timeScale;
            float sh = 0.9f;

            
            push(gl);
            gl.glScalef(0.1f, 2f, 1f);
            gl.glColor4fv(labelColor, 0);
            HersheyFont.hersheyText(gl, label, 0.05f, 0, 0.25f, 0, Draw.TextAlignment.Right);
            pop(gl);

            for (int i = 0; i < data.length; ) {
                float occ = data[i++];
                float f = data[i++];
                float c = data[i++];
                if (c < 0)
                    continue;

                float x = occ * timeScale;
                float y = (1f - sh) / 2f;

                gl.glColor4f(1 - f, f, 0, c);
                Draw.rect(x, y, sw, sh, gl);
                gl.glColor4f(0.5f, 0.5f, 0.5f, 0.5f);
                Draw.rectStroke(x, y, sw, sh, gl);
            }
        }

    }

    class TaskTimeline extends TruthTimeline {

        public TaskTimeline(Task task, long start, long end, int samplePeriod) {
            super(start, end, samplePeriod, (w) ->
            {if(false) throw new java.lang.Error("shoiuld pass this.dur as an arg? naw it was final");
             return task.truth(w, dur); });
            
            this.label = task.toString();
            
            Draw.colorHash(task.term().root(), labelColor);
        }
    }

    static private final int  dur = 1;

    class BeliefTableTimeline extends TruthTimeline {


        public BeliefTableTimeline(Compound t, BeliefTable b, long start, long end, int samplePeriod, boolean truthOrProjectedTaskTruth) {
            super(start, end, samplePeriod, (w) -> {
            	if(true) throw new java.lang.Error("shoiuld pass this.nar as an arg?");
                final NAR tnar = null; //= this.nar;
				if (truthOrProjectedTaskTruth) {
                    return b.truth(w, w, tnar );
                } else {
                    Task x = b.match(w, w, null, tnar);
                    return x != null ? x.truth(w, dur) : null;

                }
            });

            this.label = t.toString();
            Draw.colorHash(t, labelColor);
        }
    }

    public class ConceptTimeline extends Gridding {
        private final Compound term;
        private final boolean showBeliefs;

        public ConceptTimeline(Compound x, boolean showBeliefs) {
            super(VERTICAL);

            this.showBeliefs = showBeliefs;
            this.term = x;
        }


        public Collection<Surface> update(NAR n, long start, long end, int samplePeriod) {

            List<Surface> cc = $.newArrayList();

            Concept c = n.concept(term);
            if (c == null) {

            } else {


                cc.add(new BeliefTableTimeline(term, c.beliefs(), start, end, samplePeriod, truthOrProjectedTaskTruth));

                if (showBeliefs) {
                    c.beliefs().forEachTask(b -> {
                        cc.add(new TaskTimeline(b, start, end, samplePeriod));
                    });
                }
            }

            return cc;
        }

    }


    public static void main(String[] args) throws Narsese.NarseseException {
        
        NAR n = new NARS().get();

        SpaceGraph.window(
                new TruthLab(n, $("(x)"), $("(y)"),
                        $("((x) && (y))"),
                        $("((x) &&+0 (y))"),
                        $("((x) &&+5 (y))"),
                        $("((x) &&+10 (y))"),
                        
                        
                        $("(--(x) && --(y))"),
                        $("(--(x) &&+10 --(y))")
                ),
                1200, 900);


        n.log()
                .inputAt(10, "(x).   :|: %1.0;0.9%")
                .inputAt(20, "(y).   :|: %1.0;0.9%")
                .inputAt(30, "--(x). :|: %1.0;0.9%")
                .inputAt(40, "--(y). :|: %1.0;0.8%")
                .run(60);


        n.run(1);
    }

}
