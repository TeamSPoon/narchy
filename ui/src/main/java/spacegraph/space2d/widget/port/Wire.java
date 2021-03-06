package spacegraph.space2d.widget.port;

import jcog.Util;
import jcog.event.Offs;
import spacegraph.space2d.Surface;

import java.lang.reflect.Array;

/** undirected edge
 * see: https://github.com/apache/nifi/blob/master/nifi-api/src/main/java/org/apache/nifi/processor/ProcessContext.java
 * */
public class Wire {

    private final int hash;

    private volatile long aLastActive = Long.MIN_VALUE, bLastActive = Long.MIN_VALUE;
    private volatile int aTypeHash = 0, bTypeHash = 0;

    public final Surface a, b;

    public final Offs offs = new Offs();

    protected Wire(Wire copy) {
        this.a = copy.a;
        this.aTypeHash = copy.aTypeHash;
        this.b = copy.b;
        this.bTypeHash = copy.bTypeHash;
        this.hash = copy.hash;
    }

    public Wire(Surface a, Surface b) {
        assert(a!=b);
        if (a.id > b.id) {
            
            Surface x = b;
            b = a;
            a = x;
        }

        this.a = a;
        this.b = b;
        this.hash = Util.hashCombine(a, b);
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) return true;

        Wire w = ((Wire)obj);
        return w.hash == hash && (w.a.equals(a) && w.b.equals(b));
    }

    @Override
    public final int hashCode() {
        return hash;
    }

    /** sends to target */
    public final boolean send(Surface sender, Port receiver, Object s) {
        if (receiver.recv(this, transfer(sender, s))) {
            long now = System.nanoTime();

            Class<?> cl = s.getClass();
            int th = cl.hashCode();
            if (cl.isArray()) {
                
                th = Util.hashCombine(th, Array.getLength(s));
            }

            if (sender == a) {
                this.aLastActive = now;
                this.aTypeHash = th;
            } else if (sender == b) {
                this.bLastActive = now;
                this.bTypeHash = th;
            } else
                throw new UnsupportedOperationException();

            return true;
        }
        return false;
    }

    /** allows subclasses to impl inline filters or transforms */
    protected Object transfer(Surface sender, Object x) {
        return x;
    }

    public Surface other(Surface x) {
        if (x == a) {
            return b;
        } else if (x == b) {
            return a;
        } else {
            throw new RuntimeException();
        }
    }

    /** provides a value between 0 and 1 indicating amount of 'recent' activity.
     * this is entirely relative to itself and not other wires.
     * used for display purposes.
     * time is in nanosconds
     */
    public float activity(boolean aOrB, long now, long window) {
        long l = aOrB ? aLastActive : bLastActive;
        if (l == Long.MIN_VALUE)
            return 0;
        else {
            return (float) (1.0/(1.0+(Math.abs(now - l))/((double)window)));
        }
    }
    /** combined activity level */
    public final float activity(long now, long window) {
        return activity(true, now, window) + activity(false, now, window);
    }

    public final boolean connectable() {
        if (a instanceof Port && b instanceof Port) { //HACK
            //synchronized (this) {
                return ((Port) a).connectable((Port) b) && ((Port) b).connectable((Port) a);
            //}
        }

        return true;
    }

    public int typeHash(boolean aOrB) {
        int x = aOrB ? aTypeHash : bTypeHash;
        if (x == 0 && (aOrB ? aLastActive : bLastActive)==Long.MIN_VALUE)
            return (aOrB ? bTypeHash : aTypeHash ); 
        else
            return x;
    }

    public final void remove() {
        offs.off();
    }

    /** override in subclasses to implement behavior to be executed after wire connection has been established in the graph. */
    public void connected() {
        if (a instanceof Port && b instanceof Port) { //HACK
            ((Port) a).connected((Port) b);
            ((Port) b).connected((Port) a);
        }
    }
}
