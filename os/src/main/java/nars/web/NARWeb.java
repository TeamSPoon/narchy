package nars.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jcog.Texts;
import jcog.Util;
import jcog.data.map.CustomConcurrentHashMap;
import jcog.event.Off;
import jcog.event.Offs;
import jcog.exe.Exe;
import jcog.net.http.HttpServer;
import jcog.net.http.WebSocketConnection;
import jcog.pri.bag.impl.PriArrayBag;
import jcog.pri.op.PriMerge;
import nars.*;
import nars.exe.Exec;
import nars.exe.UniExec;
import nars.index.concept.MaplikeConceptIndex;
import nars.index.concept.ProxyConceptIndex;
import nars.time.clock.RealTime;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.Nullable;
import spacegraph.WebServer;
import spacegraph.web.util.ClientBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import static jcog.data.map.CustomConcurrentHashMap.*;
import static nars.web.TaskJsonCodec.Native.taskify;
import static spacegraph.web.util.ClientBuilder.rebuild;

abstract public class NARWeb extends WebServer {

    static final int DEFAULT_PORT = 60606;


    @Override
    public boolean wssConnect(WebSocketConnection conn) {
        NAR n = nar(conn, conn.url().getPath());
        if (n != null) {

            //logger.info("..
            //System.out.println("NAR " + System.identityHashCode(n) + " for " + url);

            conn.setAttachment(
                    new NARConnection(n,
                            n.onTask(new WebSocketLogger(conn, n))
                            //...
                    )
            );

            return true;
        } else {
            return false;
        }
    }

    @Nullable
    protected abstract NAR nar(WebSocketConnection conn, String url);


    @Override
    public void wssMessage(WebSocket ws, String message) {
        try {
            NAR n = ((NARWeb.NARConnection) ws.getAttachment()).nar;
            n.input(message);
//            System.out.println(n.loop + " " + n.loop.isRunning());
//            System.out.println(Iterables.toString(n.attn.active));
//            System.out.println(Iterators.toString(n.services().iterator()));
//            System.out.println(n.exe);

        } catch (Narsese.NarseseException e) {
            ws.send(e.toString()); //e.printStackTrace();
        }
    }

    static class NARConnection extends Offs {
        public final NAR nar;

        public NARConnection(NAR n, Off... ons) {
            this.nar = n;
            for (Off o : ons)
                add(o);
        }
    }

    protected void stopping(NAR n) {

    }

    protected void starting(NAR n) {

    }


    /**
     * Web Interface for 1 NAR
     */
    public static class Single extends NARWeb {

        private final NAR nar;

        public Single(NAR nar) {
            this.nar = nar;
        }

        public static void main(String[] args) throws IOException {

            ClientBuilder.rebuildAsync(NARWebClient.class, false);

            int port;
            if (args.length > 0) {
                port = Texts.i(args[0]);
            } else {
                port = DEFAULT_PORT;
            }

            NAR nar;
            jcog.net.http.HttpServer h = new HttpServer(port, new NARWeb.Single(nar = NARchy.core(1)));
            h.setFPS(10f);

            nar.startFPS(5f);
            nar.loop.throttle.set(0.1f);
        }

        @Override
        protected @Nullable NAR nar(WebSocketConnection conn, String url) {
            return url.equals("/") ? nar : null;
        }
    }

    /**
     * Shared Multi-NAR Server
     * TODO
     */
    public static class Multi extends NARWeb {
        private final NAR nar;
        /**
         * adapter
         */
        private final MaplikeConceptIndex sharedIndex;

        public Multi() {
            this.nar = NARchy.core();
            //this.nar.loop.setFPS(10);
            this.sharedIndex = new ProxyConceptIndex(nar.concepts);
        }

        @Override
        protected NAR nar(WebSocketConnection conn, String url) {
            if (url.equals("/")) {
                return null;
            }

            return reasoners.computeIfAbsent(url, (Function<String, NAR>) this::nar);
        }

        //TODO <URI,NAR>
        final CustomConcurrentHashMap<String, NAR> reasoners = new CustomConcurrentHashMap<String, NAR>(
                STRONG, EQUALS, WEAK, IDENTITY, 64) {
            @Override
            protected void reclaim(NAR n) {
                Multi.this.remove(n);
            }
        };

        private void remove(NAR n) {
            stopping(n);
            n.reset();
        }

        /**
         * create a NAR
         */
        private NAR nar(String path) {
            final Exec exe = nar.exe;
            final Exec sharedExec = new UniExec() {

                @Override
                public boolean concurrent() {
                    return false;
                }

                @Override
                public void start(NAR nar) {
                    super.start(nar);
                }

                @Override
                public void execute(Runnable async) {
                    exe.execute(async);
                }

                @Override
                public void input(Consumer<NAR> r) {
                    execute(() -> r.accept(this.nar));
                }
            };

            NAR n = new NARS().withNAL(1, 8).time(new RealTime.MS()).exe(sharedExec).index(sharedIndex).get();


            assert (path.charAt(0) == '/');
            path = path.substring(1);
            n.named(path);

            n.log(); //temporary

            int initialFPS = 5;
            n.startFPS(initialFPS);

            starting(n);

            return n;
        }

    }

//        Util.sleep(100);
//
//        WebClient c1 = new WebClient(URI.create("ws://localhost:60606/a"));
//        WebClient c2 = new WebClient(URI.create("ws://localhost:60606/b"));
//
//        Util.sleep(500);
//        c1.closeBlocking();
//        c2.closeBlocking();


    /**
     * client access for use in java
     */
    public static class WebClient extends WebSocketClient {

        public WebClient(URI serverUri) {
            super(serverUri);
            try {
                connectBlocking();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {

        }

        @Override
        public void onMessage(String message) {
            System.out.println(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {

        }

        @Override
        public void onError(Exception ex) {

        }
    }

    static class WebSocketLogger implements Consumer<Task> {

        volatile WebSocket w;
        final PriArrayBag<Task> out = new PriArrayBag<Task>(PriMerge.max, 64);
        final AtomicBoolean busy = new AtomicBoolean();
        public WebSocketLogger(WebSocket ws, NAR n) {
            this.w = ws;

        }

        @Override
        public void accept(Task t) {
            if (out.put(t)!=null) {
                if (busy.compareAndSet(false, true)) {
                    Exe.invoke(this::drain);
                }
            }
        }


        protected void drain() {
            if (w.isOpen()) {
                busy.set(false);

//                final StringBuilder buf = new StringBuilder(2*1024);
//
//                buf.append('[');
//                out.clear(t -> buf.append('\"').append(t.toString(true)).append("\",")); //tmp
//                if (buf.length() > 0) {
//                    buf.setLength(buf.length() - 1);
//                }
//                buf.append(']');
//
//                String s = buf.toString();
//                w.send(s);

                ArrayNode a = Util.msgPacker.createArrayNode();

                out.clear(t -> {
                    taskify(t, a.addArray());
                });

                if (a.size() > 0) {
                    try {
                        w.send(Util.msgPacker.writeValueAsBytes(a));
                    } catch (JsonProcessingException e) {
                        //logger.error("")
                        e.printStackTrace();
                    }
                }

            } else {
                //closed, dont un-busy
            }
        }



    }

    public static class WebClientJSBuilder {

        public static void main(String[] args) {
            rebuild(NARWebClient.class, false);
        }

    }
}
