package nars.gui.graph;

import spacegraph.Spatial;
import spacegraph.layout.ForceDirected;
import spacegraph.phys.Collidable;
import spacegraph.phys.collision.broad.Broadphase;

import java.util.List;

public class EdgeDirected extends ForceDirected {

    @Override
    public void solve(Broadphase b, List<Collidable> objects, float timeStep) {

        float a = attraction.floatValue();

        for (int i = 0, objectsSize = objects.size(); i < objectsSize; i++) {
            Collidable c = objects.get(i);

            Spatial A = ((Spatial) c.data());
            if (A instanceof ConceptWidget) {
                ((ConceptWidget) A).edges.forEachKey(e -> {

                    float attraction = e.attraction;
                    if (attraction > 0) {
                        ConceptWidget B = e.target;

                        if ((B.body != null)) {

                            attract(c, B.body, a * attraction, e.attractionDist);
                        }
                    }

                });
            }


        }

        super.solve(b, objects, timeStep);
    }
}