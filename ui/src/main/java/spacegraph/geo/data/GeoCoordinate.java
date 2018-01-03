package spacegraph.geo.data;

/**
 * Created by unkei on 2017/04/25.
 */
public class GeoCoordinate {
    public final double latitude;
    public final double longitude;
    public final double altitude;

    public GeoCoordinate() {
        this(0, 0, 0);
    }

    public GeoCoordinate(double latitude, double longitude, double altitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
    }

    public GeoCoordinate(double latitude, double longitude) {
        this(latitude, longitude, 0);
    }
}
