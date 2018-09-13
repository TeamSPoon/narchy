package toxi.geom;

import java.util.function.Consumer;

public interface SpatialIndex<T> {

    void clear();

    boolean index(T p);

    boolean isIndexed(T item);

//    public List<T> itemsWithinRadius(T p, float radius, List<T> results);

    void itemsWithinRadius(Vec2D p, float radius, Consumer<T> results);

    boolean reindex(T p, Consumer<T> update);

    int size();

    boolean unindex(T p);

}