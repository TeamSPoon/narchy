package spacegraph.util.geo.osm;

import java.util.Map;

/**
 * Created by unkei on 2017/04/25.
 */
public class OsmNode extends OsmElement {
    public final GeoCoordinate geoCoordinate;

    public OsmNode(long id, GeoCoordinate geoCoordinate, Map<String, String> tags) {
        super(id, null, tags);
        this.geoCoordinate = geoCoordinate;
    }

    


}
