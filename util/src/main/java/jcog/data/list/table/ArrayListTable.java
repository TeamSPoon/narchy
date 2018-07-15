package jcog.data.list.table;

import jcog.data.map.CollectorMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Items are available by an integer index
 */
abstract public class ArrayListTable<K, V> extends CollectorMap<K, V> implements Table<K, V> {


    protected int capacity;

    public ArrayListTable(@NotNull Map<K, V> map) {
        super(map);
    }

    abstract public V get(int i);

    @Override
    abstract public int size();

    @Override
    public void forEachKey(Consumer<? super K> each) {
        forEach(t -> each.accept(key(t)));
    }

    @NotNull
    @Override
    abstract public Iterator<V> iterator();


    @Override
    public void clear() {
        super.clear(); 
        listClear();
    }

    abstract protected void listClear();

    /**
     * Check if an item is in the bag
     *
     * @param k An item
     * @return Whether the Item is in the Bag
     */
    public final boolean contains(/*@NotNull*/ K k) {
        return this.containsKey(k);
    }


    @Nullable
    @Override
    protected final V removeItem(/*@NotNull*/ V removed) {
        return listRemove(removed) ? removed : null;
    }

    protected abstract boolean listRemove(V removed);






































    @Override
    public final int capacity() {
        return capacity;
    }

    /**
     * returns whether the capacity has changed
     */
    @Override
    public void setCapacity(int newCapacity) {
        this.capacity = newCapacity;
        
    }



























    /**
     * default implementation; more optimal implementations will avoid instancing an iterator
     */
    public void forEach(int max, @NotNull Consumer<? super V> action) {
        int n = Math.min(size(), max);
        
        for (int i = 0; i < n; i++) {
            action.accept(get(i));
        }
    }










}