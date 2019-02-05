package jcog.net;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import jcog.Texts;
import jcog.Util;
import jcog.data.NumberX;
import jcog.data.byt.DynBytes;
import jcog.event.ListTopic;
import jcog.event.Topic;
import jcog.exe.Every;
import jcog.io.BinTxt;
import jcog.math.FloatRange;
import jcog.math.RecycledSummaryStatistics;
import jcog.net.attn.HashMapTagSet;
import jcog.pri.UnitPrioritizable;
import jcog.pri.bag.Bag;
import jcog.pri.bag.Sampler;
import jcog.pri.bag.impl.HijackBag;
import jcog.pri.bag.impl.hijack.PriHijackBag;
import jcog.random.XoRoShiRo128PlusRandom;
import jcog.util.ArrayUtils;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import static jcog.net.UDPeer.Command.*;
import static jcog.net.UDPeer.Msg.ADDRESS_BYTES;

/**
 * UDP peer - self-contained generic p2p/mesh network node
 *
 * see:
 * Gossip Protocols
 * Gnutella
 * GNU WASTE
 *
 * TODO https://github.com/komamitsu/phi-accural-failure-detector/blob/master/src/main/java/org/komamitsu/failuredetector/PhiAccuralFailureDetector.java
 *
 */
public class UDPeer extends UDP {

    protected final Logger logger;

//    public final HashMapTagSet can = new HashMapTagSet("C");
//    public final HashMapTagSet need = new HashMapTagSet("N");

    public final Bag<Integer, UDProfile> them;
    public final PriHijackBag<Msg, Msg> seen;

    public final UDiscover<Discoverability> discover;
    public Every discoverEvery = Every.Never;

    /**
     * TODO use a variable size identifier, 32+ bit. ethereumj uses 512bits.
     * smaller id's will work better for closed networks with a known population
     */
    public final int me;
    private static final int UNKNOWN_ID = Integer.MIN_VALUE;

    /**
     * rate of sharing peer needs
     */
    private static final FloatRange empathy = new FloatRange(0.5f, 0f, 1f);

    private static final byte DEFAULT_PING_TTL = 2;
    private static final byte DEFAULT_ATTN_TTL = DEFAULT_PING_TTL;

    /**
     * active routing table capacity
     * TODO make this IntParam mutable
     */
    final static int PEERS_CAPACITY = 64;


    public final Topic<MsgReceived> receive = new ListTopic<>();

    /**
     * message memory
     */
    final static int SEEN_CAPACITY = 32 * 1024;
    private final Random rng;


    private final AtomicBoolean
            needChanged = new AtomicBoolean(false);
    private final AtomicBoolean canChanged = new AtomicBoolean(false);


    public UDPeer() throws IOException {
        this(true);
    }
    /**
     * assigned a random port
     */
    public UDPeer(boolean discovery) throws IOException {
        this(InetAddress.getLocalHost(), 0, discovery);
    }

    public UDPeer(int port) throws IOException {
        this(InetAddress.getLocalHost(), port, true);
    }

    public UDPeer(InetAddress address, int port) throws IOException {
        this(address, port, false);
    }

    public UDPeer(int port, boolean discovery) throws IOException {
        this(InetAddress.getLocalHost(), port, discovery);
    }

    public UDPeer(InetAddress address, int port, boolean discovery) throws IOException {
        super(address, port);

        

        
        /*this.me = new InetSocketAddress(
                InetAddress.getByName("[0:0:0:0:0:0:0:0]"),
                port);*/
        

        this.rng = new XoRoShiRo128PlusRandom(System.nanoTime());

        int me;
        while ((me = (int)(UUID.randomUUID().getLeastSignificantBits() & 0xffff)) == UNKNOWN_ID) ;
        this.me = me;

        this.logger = LoggerFactory.getLogger(getClass().getSimpleName() + ':' + name());

        them = new HijackBag<Integer, UDProfile>(3) {

            @Override
            public void onAdd(UDProfile p) {
                logger.debug("connect {}", p);
                onAddRemove(p, true);
            }

            @Override
            public void onRemove(UDPeer.UDProfile p) {
                logger.debug("disconnect {}", p);
                onAddRemove(p, false);
            }

            @Override
            protected UDProfile merge(@Nullable UDPeer.UDProfile existing, UDProfile incoming, NumberX overflowing) {
                return (existing != null ? existing : incoming);
            }

            @Override
            public Consumer<UDProfile> forget(float rate) {
                return null;
            }

            @Override
            public float pri(UDPeer.UDProfile key) {
                long latency = key.latency();
                return 1f / (1f + Util.sqr(latency / 100f));
            }

            @Override
            public void priAdd(UDProfile entry, float amount) {
                
            }


            @NotNull
            @Override
            public Integer key(UDProfile value) {
                return value.id;
            }

        };

        them.setCapacity(PEERS_CAPACITY);

        seen = new PriHijackBag<Msg,UDPeer.Msg>(SEEN_CAPACITY, 3) {

            @NotNull
            @Override
            public UDPeer.Msg key(Msg x) {
                return x;
            }

            @Override
            public Consumer<Msg> forget(float rate) {
                return null;
            }
        };

        discover = discovery ? new UDiscover<Discoverability>(new Discoverability(me, addr)) {
            @Override
            protected void found(Discoverability who, InetAddress addr, int port) {
                //TODO hard exclude the UDPeer itself (ie. if addr and port equal)
                if (!who.addr.equals(UDPeer.this.addr) && !them.contains(who.id)) {
                    logger.debug("discovered {} at {}:{}", who.id, who.addr);
                    ping(who.addr);
                }
            }
        } : null;

    }

    static class Discoverability implements Serializable {
        public int id;
        public InetSocketAddress addr;

        public Discoverability() { }
        
        public Discoverability(int id, InetSocketAddress addr) {
            this.id = id;
            this.addr = addr;
        }
    }




    protected void onAddRemove(UDProfile p, boolean addedOrRemoved) {

    }


    public final boolean connected() {
        return !them.isEmpty();
    }

    /**
     * broadcast
     * TODO handle oversized message
     *
     * @return how many sent
     */
    public int tellSome(Msg o, float pri, boolean onlyIfNotSeen) {

        if (!connected() || pri <= 0) {
            
            return 0;
        } else {

            if (onlyIfNotSeen && seen(o, pri))
                return 0;

            byte[] bytes = o.arrayCompactDirect();

            final int[] remain = {Math.round(them.size() * pri)};
            them.sample(rng, (Function<UDProfile, Sampler.SampleReaction>)((to) -> {
                if (o.id() != to.id /*&& (pri >= 1 || rng.nextFloat() <= pri)*/ ) {
                    outBytes(bytes, to.addr);
                }
                return ((remain[0]--) > 0) ? Sampler.SampleReaction.Next : Sampler.SampleReaction.Stop;
            }));
            return remain[0];

        }
    }

    @Override
    protected void starting() {
        synchronized (this) {
            super.starting();
            if (discover != null) {
                discover.start();
                discoverEvery = new Every(discover::update, 250);
            } else {
                discoverEvery = Every.Never;
            }
        }
    }

    @Override
    protected void stopping() {
        synchronized (this) {
            if (discover != null) {
                discover.stop();
                discoverEvery = Every.Never;
            }
            them.clear();
            super.stopping();
        }
    }


    public boolean seen(Msg o, float pri) {
        o.priAdd(pri);
        return seen.put(o) != o;
    }

    public void tellSome(byte[] msg, int ttl) {
        tellSome(msg, ttl, false);
    }

    public int tellSome(byte[] msg, int ttl, boolean onlyIfNotSeen) {
        Msg x = new Msg(TELL.id, (byte) ttl, me, null, msg);
        int y = tellSome(x, 1f, onlyIfNotSeen);
        seen(x, 1f);
        return y;
    }

    public int tellSome(Object msg, int ttl) throws JsonProcessingException {
        return tellSome(msg, ttl, false);
    }

    public int tellSome(Object msg, int ttl, boolean onlyIfNotSeen) throws JsonProcessingException {
        byte[] b = Util.toBytes(msg, Object.class);
        return tellSome(b, ttl, onlyIfNotSeen);
    }
    public int tellSome(JsonNode msg, int ttl, boolean onlyIfNotSeen) throws JsonProcessingException {
        byte[] b = Util.toBytes(msg, JsonNode.class);
        return tellSome(b, ttl, onlyIfNotSeen);
    }

    /**
     * send to a specific known recipient
     */
    public void send(Msg o, InetSocketAddress to) {
        outBytes(o.arrayDirect(), 0, o.len, to);
    }

    @Override
    public boolean next() {

        discoverEvery.next();

        if (!super.next())
            return false;

        seen.commit();

//        boolean updateNeed, updateCan;
//        if (needChanged.compareAndSet(true, false)) {
//            updateNeed = true;
//            tellSome(need);
//            onUpdateNeed();
//        }
//
//        if (canChanged.compareAndSet(true, false)) {
//            updateCan = true;
//            tellSome(can);
//        }

        return true;
    }

//    protected void onUpdateNeed() {
//
//    }

//    protected void tellSome(HashMapTagSet setAt) {
//        tellSome(new Msg(ATTN.id, DEFAULT_ATTN_TTL, me, null,
//                setAt.toBytes()), 1f, false);
//    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + name() + ')';
    }

    public String name() {
        return BinTxt.toString(me);
    }

    @Override
    protected void in(InetSocketAddress p, byte[] data, int len) {

        final byte[] inputArray = data;


        

        Msg m = new Msg(data, len);
        if (/*m == null || */m.id() == me)
            return;

        Command cmd = Command.get(m.cmd());
        if (cmd == null)
            return; 


        if (m.port() == 0) {
            
            byte[] msgOriginBytes = bytes(p);
            if (!m.originEquals(msgOriginBytes)) {
                m = m.clone(cmd.id, msgOriginBytes);
            }
        }

        m.compact(inputArray, false);

        boolean seen = seen(m, 1f);
        if (seen)
            return;

        boolean survives = m.live();

        @Nullable UDProfile you = them.get(m.id());

        long now = System.currentTimeMillis();

        switch (cmd) {
            case PONG:
                you = onPong(p, m, you, now);
                break;
            case PING:
                sendPong(p, m); 
                break;



            case TELL:
                receive(you, m);
                break;
//            case ATTN:
//                if (you != null) {
//                    HashMapTagSet h = HashMapTagSet.fromBytes(m.data());
//                    if (h != null) {
//
//                        switch (h.id()) {
//                            case "C":
//                                you.can = h;
//
//                                break;
//                            case "N":
//                                you.need = h;
//                                need(h, empathy.floatValue());
//                                break;
//                            default:
//                                return;
//                        }
//                        if (logger.isDebugEnabled())
//                            logger.debug("{} attn {}", you.name(), h);
//                    }
//                }
//                break;
            default:
                return;
        }


        if (you == null) {
            if (them.size() < them.capacity()) {
                
                ping(p);
            }
        } else {
            you.lastMessage = now;
        }


        if (survives) {
            tellSome(m, 1f, false /* did a test locally already */);
        }
    }

    protected void receive(@Nullable UDProfile from, Msg m) {
        if (!receive.isEmpty())
            receive.emit(new MsgReceived(m, from));
    }


    public RecycledSummaryStatistics latencyAvg() {
        RecycledSummaryStatistics r = new RecycledSummaryStatistics();
        them.forEach(x -> r.accept(x.latency()));
        return r;
    }

    public String summary() {
        return them.size() + " peers, (avg latency=" + latencyAvg() + ')';
    }

    /**
     * ping same host, different port
     */
    public void ping(int port) {
        ping(new InetSocketAddress(port));
    }

    public void ping(String host, int port) {
        ping(new InetSocketAddress(host, port));
    }

    public void ping(InetAddress host, int port) {
        ping(new InetSocketAddress(host, port));
    }

    public void ping(@Nullable UDPeer x) {
        assert(this!=x);
        ping(x.addr);
    }

    public void ping(@Nullable InetSocketAddress to) {
        if (to.equals(UDPeer.this.addr))
            return;
            //throw new UnsupportedOperationException("dont ping self");

        if (logger.isDebugEnabled())
            logger.debug("{} {} send ping to {}", name(), addr, to);
        send(ping(), to);
    }


    private Msg ping() {
        return new Msg(PING.id, DEFAULT_PING_TTL, me, null, System.currentTimeMillis());
    }


    protected @Nullable UDProfile onPong(InetSocketAddress p, Msg m, @Nullable UDProfile connected, long now) {

        long sent = m.dataLong(0);
        long latencyMS = now - sent;

        if (logger.isDebugEnabled())
            logger.debug("{} recv pong {} from {} ({})", name(), m, connected, Texts.timeStr(1E6 * latencyMS));

        if (connected != null) {
            connected.onPing(latencyMS);
        } else {
            int pinger = m.dataInt(8, UNKNOWN_ID);
            if (pinger == me) {
                connected = them.put(new UDProfile(m.id(), p, latencyMS));
            }
        }
        return connected;
    }

    protected void sendPong(InetSocketAddress from, Msg ping) {
        Msg m =
                new Msg(PONG.id, (byte) 1, me, from,
                        ArrayUtils.addAll(
                                Longs.toByteArray(ping.dataLong(0)), 
                                Ints.toByteArray(ping.id()) 
                        ));

        if (logger.isDebugEnabled())
            logger.debug("send pong {} {} to {}", addr, m, from);

        seen(m, 1f);
        send(m, from);
    }


//    public void can(String tag, float pri) {
//        if (can.addAt(tag, pri))
//            canChanged.setAt(true);
//    }
//
//    public void need(String tag, float pri) {
//        if (need.addAt(tag, pri))
//            needChanged.setAt(true);
//    }
//
//    public void need(HashMapTagSet tag, float pri) {
//        if (need.addAt(tag, pri))
//            needChanged.setAt(true);
//    }

    public enum Command {

        /**
         * measure connectivity
         */
        PING('P'),

        /**
         * answer a ping
         */
        PONG('p'),

        /**
         * ping / report known peers?
         */
        WHO('w'),

//        /**
//         * share my attention
//         */
//        ATTN('a'),

        /**
         * share a belief claim
         */
        TELL('t'),;

        public final byte id;


        Command(char id) {
            this.id = (byte) id;
        }

        @Nullable
        public static Command get(byte cmdByte) {
            switch (cmdByte) { 
                case 'P':
                    return PING;
                case 'p':
                    return PONG;


                case 't':
                    return TELL;
//                case 'a':
//                    return ATTN;
            }
            return null;
        }
    }

    /** Msg extended with a UDProfile instance */
    public static class MsgReceived extends Msg {

        @Nullable
        public final UDProfile from;

        public MsgReceived(Msg m, @Nullable UDProfile from) {
            super(m.arrayCompactDirect());
            this.from = from;
        }
    }

    public static class Msg extends DynBytes implements UnitPrioritizable {

        final static int TTL_BYTE = 0;
        final static int CMD_BYTE = 1;
        final static int ID_BYTE = 2;
        final static int PORT_BYTE = 6;
        final static int ORIGIN_BYTE = 8;
        final static int DATA_START_BYTE = 24;

        final static int HEADER_SIZE = DATA_START_BYTE;
        final static int ADDRESS_BYTES = 16 /* ipv6 */ + 2 /* port */;

        final int hash;
        private float pri;

        public Msg(byte[] data, int len) {
            super(data, len);
            hash = hash();
        }

        public Msg(byte... data) {
            this(data, data.length);
        }

        @Override
        public final float pri() {
            return pri;
        }

        private void init(byte cmd, byte ttl, int id, @Nullable InetSocketAddress origin) {
            writeByte(ttl);
            writeByte(cmd);
            writeInt(id);

            if (origin != null) {
                write(bytes(origin));
            } else {
                fillBytes((byte) 0, ADDRESS_BYTES);
            }


        }

        public Msg(byte cmd, byte ttl, int id, InetSocketAddress origin, byte[] payload) {
            super(HEADER_SIZE + payload.length);
            init(cmd, ttl, id, origin);

            if (payload.length > 0)
                write(payload);

            hash = hash();
        }


        public Msg(byte cmd, byte ttl, int id, InetSocketAddress origin, long payload) {
            super(HEADER_SIZE + 8);
            init(cmd, ttl, id, origin);

            writeLong(payload);

            hash = hash();
        }


        private int hash() {
            compact();
            return hash(1  /* skip TTL byte */, len);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            Msg m = (Msg) obj;
            if (hash == m.hash) {
                int len = this.len;
                return m.len == len && Arrays.equals(m.bytes, 1, len, bytes, 1, len);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        public byte cmd() {
            return bytes[CMD_BYTE];
        }

        public byte ttl() {
            return bytes[TTL_BYTE];
        }

        public boolean live() {
            int ttl = ttl();
            if (ttl <= 0)
                return false;
            return (--bytes[TTL_BYTE]) >= 0;
        }

        @Override
        public String toString() {

            return BinTxt.toString(id()) + ' ' +
                    ((char) cmd()) + '+' + ttl() +
                    '[' + dataLength() + ']';

            
        }


        /**
         * clones a new copy with different command
         */
        public Msg clone(byte newCmd) {
            byte[] b = Arrays.copyOf(bytes, len);
            b[CMD_BYTE] = newCmd;
            return new Msg(b);
        }

        public Msg clone(byte newCmd, @Nullable byte[] newOrigin) {
            byte[] b = Arrays.copyOf(bytes, len);
            b[CMD_BYTE] = newCmd;

            if (newOrigin != null) {
                System.arraycopy(newOrigin, 0, b, PORT_BYTE, ADDRESS_BYTES);
            } else {
                Arrays.fill(b, PORT_BYTE, ADDRESS_BYTES, (byte) 0);
            }
            return new Msg(b);
        }

        public Msg clone(byte newCmd, int id, @Nullable byte[] newOrigin) {
            byte[] b = Arrays.copyOf(bytes, len);
            b[CMD_BYTE] = newCmd;

            System.arraycopy(Ints.toByteArray(id), 0, b, ID_BYTE, 4);

            if (newOrigin != null) {
                System.arraycopy(newOrigin, 0, b, PORT_BYTE, ADDRESS_BYTES);
            } else {
                Arrays.fill(b, PORT_BYTE, ADDRESS_BYTES, (byte) 0);
            }
            return new Msg(b);
        }

        public int dataLength() {
            return length() - DATA_START_BYTE;
        }

        public byte[] data() {
            return data(0, dataLength());
        }

        public byte[] data(int start, int end) {
            return Arrays.copyOfRange(bytes, DATA_START_BYTE + start, DATA_START_BYTE + end);
        }

        public int id() {
            byte[] b = bytes;
            return Ints.fromBytes(
                    b[ID_BYTE], b[ID_BYTE + 1], b[ID_BYTE + 2], b[ID_BYTE + 3]
            );
        }

        /**
         * the payload as a long
         */
        public long dataLong(int offset) {
            byte[] b = this.bytes;
            offset += DATA_START_BYTE;
            if (b.length < offset + 8)
                throw new RuntimeException("missing 64-bit payload");

            return Longs.fromBytes(
                    b[offset++], b[offset++], b[offset++], b[offset++],
                    b[offset++], b[offset++], b[offset++], b[offset]
            );
        }

        public int dataInt(int offset, int ifMissing) {
            byte[] b = this.bytes;
            offset += DATA_START_BYTE;
            if (b.length < offset + 4)
                return ifMissing;

            return Ints.fromBytes(
                    b[offset++], b[offset++], b[offset++], b[offset++]
            );
        }


        public boolean originEquals(byte[] addrBytes) {
            int addrLen = addrBytes.length;
            return Arrays.equals(bytes, PORT_BYTE, PORT_BYTE + addrLen, addrBytes, 0, addrLen);
        }























        @Nullable
        public InetSocketAddress origin() {
            int port = this.port();
            InetAddress aa = null;
            try {
                aa = InetAddress.getByAddress(Arrays.copyOfRange(bytes, ORIGIN_BYTE, ORIGIN_BYTE + 16));
                return new InetSocketAddress(aa, port);
            } catch (UnknownHostException e) {
                return null;
            }

        }

        public int port() {
            int firstByte = (0x000000FF & ((int) bytes[PORT_BYTE]));
            int secondByte = (0x000000FF & ((int) bytes[PORT_BYTE + 1]));
            return (char) (firstByte << 8 | secondByte);
            
        }


        @Override
        public float pri(float p) {
            return this.pri = Util.unitize(p);
        }

        @Override
        public boolean delete() {
            if (pri == pri) {
                this.pri = Float.NaN;
                return true;
            }
            return false;
        }

        @Override
        public boolean isDeleted() {
            return pri != pri;
        }
    }

    /**
     * profile of another peer
     */
    public static class UDProfile {
        public final InetSocketAddress addr;

        public final int id;

        long lastMessage = Long.MIN_VALUE;
        public byte[] addrBytes;

        /**
         * ping time, in ms
         * TODO find a lock-free sort of statistics class
         */
        final Histogram pingTime = new ConcurrentHistogram(1, 16*1024, 0);

        /** caches the value of the mean pingtime */
        final AtomicLong latency = new AtomicLong(Long.MAX_VALUE);

        HashMapTagSet
                can = HashMapTagSet.EMPTY,
                need = HashMapTagSet.EMPTY;

        public UDProfile(int id, InetSocketAddress addr, long initialPingTime) {
            this.id = id;
            this.addr = addr;
            this.addrBytes = bytes(addr);
            onPing(initialPingTime);
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public boolean equals(Object obj) {
            return id == ((UDProfile) obj).id;
            
        }

        public void onPing(long timeMS) {
            pingTime.recordValue(Math.max(1,timeMS));
            latency.set(Math.round(pingTime.getMean()));
        }

        /**
         * average ping time in ms
         */
        public long latency() {
            return latency.get();
        }

        @Override
        public String toString() {
            return name() + '{' +
                    "addr=" + addr +
                    ", ping=" + latency() +
                    ", can=" + can +
                    ", need=" + need +
                    '}';
        }


        public String name() {
            return BinTxt.toString(id);
        }
    }


    public static byte[] bytes(InetSocketAddress addr) {
        byte[] x = new byte[ADDRESS_BYTES];
        int port = addr.getPort();
        x[0] = (byte) ((port >> 8) & 0xff); 
        x[1] = (byte) (port & 0xff);
        ipv6(addr.getAddress().getAddress(), x, 2);
        return x;
    }




}
