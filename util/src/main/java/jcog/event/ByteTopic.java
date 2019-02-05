package jcog.event;

import jcog.TODO;
import jcog.data.bit.MetalBitSet;

import java.util.function.Consumer;

/** topic whose transmissions are keyed by a 'byte' selector.  receivers can register for one or more of the channels */
public class ByteTopic<X> {

    /** last channel is reserved for general catch 'all' sent once in all cases */
    final static byte ANY = Byte.MAX_VALUE-1;

    final boolean allowDynamic = false;

    final Topic<X>[] chan = new Topic[Byte.MAX_VALUE /* signed max */];

    /** TODO write atomic variant of LongMetalBitset */
    final MetalBitSet active = MetalBitSet.bits(chan.length);

    public ByteTopic(byte... preDefined) {
        validate(false, preDefined);
        for (byte c : preDefined)
            chan[c] = newTopic(c);

        assert(chan[ANY] == null);
        chan[ANY] = newTopic(ANY);

    }

    protected Topic<X> newTopic(byte c) {
        return new ListTopic<X>() {
            @Override
            public void enable(Consumer<X> o) {
                super.enable(o);
                synchronized (ByteTopic.this.active) { //HACK TODO use atomic
                    active.set(c, true);
                }
            }

            @Override
            public void disable(Consumer<X> o) {
                super.disable(o);
                synchronized (ByteTopic.this.active) { //HACK TODO use atomic
                    active.set(c, false);
                }
            }
        };
    }

    public final Off on(Consumer<X> each, byte... channelsRegistered) {

        if (channelsRegistered.length == 0)
            return _on(each, ANY);

        validate(true, channelsRegistered);

        if (allowDynamic) {
            throw new TODO();//TODO synch-free version of this using atomic ops
        } else {
            Offs o = new Offs();
            for (byte c : channelsRegistered) {
                Off co = _on(each, c);
                o.add(co);
            }
            return o;
        }
    }

    private Off _on(Consumer<X> each, byte c) {
        active.set(c, true);
        return chan[c].on(each);
    }

    public final void emit(X x, byte... chans) {
        for (byte c : chans)
            _emit(x, c);
        _emit(x, ANY);
    }

    private void _emit(X x, byte c) {
        if (active.get(c))
            chan[c].emit(x);
    }

    private void validate(boolean external, byte[] chans) {
        if (chans.length == 0)
            throw new UnsupportedOperationException();
        for (byte c : chans) {
            if (c < 0 || c >= ANY)
                throw new ArrayIndexOutOfBoundsException();
            if (external && (!allowDynamic && chan[c] == null))
                throw new NullPointerException();
        }
    }

}
