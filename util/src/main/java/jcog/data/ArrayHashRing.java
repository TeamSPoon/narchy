package jcog.data;

/** fixed capacity ArrayHashSet. new items remove oldest item. combination of Set and Deque
 * TODO add ability to add/remove from both ends, like Deque<>
 * TODO use actual ring-buffer instead of List<> for faster removal from the start */
public class ArrayHashRing<X> extends ArrayHashSet<X> {

    int capacity;

    public ArrayHashRing(int capacity) {
        this.capacity = capacity;
    }

    public ArrayHashRing<X> capacity(int capacity) {
        if (this.capacity!=capacity && size() > capacity) {
            int toRemove = size() - capacity;
            pop(toRemove);
        }
        this.capacity = capacity;
        return this;
    }

    @Override
    protected void addUnique(X element) {
        if (size() >= capacity)
            pop(1);
        super.addUnique(element);
    }

    public void pop(int n) {
        for (int i = 0; i < n; i++) {
            boolean removed = remove(list.remove(0));
            assert(removed);
        }
    }


}