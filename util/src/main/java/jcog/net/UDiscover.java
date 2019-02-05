package jcog.net;

import jcog.Util;
import jcog.net.UDPeer.Discoverability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.Arrays;

/**
 * https:
 */
abstract public class UDiscover<P>  {

    static protected final Logger logger = LoggerFactory.getLogger(UDiscover.class);

    final static String address = "228.5.6.7";
    final static int port = 6576;
    static final int MAX_PAYLOAD_ID = 256;

    /** SO_TIMEOUT value */
    private static final int TIMEOUT_MS = 250;

    private final P id;

    MulticastSocket ms;
    private DatagramPacket p, q;
    private InetAddress ia;
    private byte[] myID;
    private byte[] theirID;

//    final AtomicBoolean busy = new AtomicBoolean(true);


    public UDiscover(P payloadID) {
        this.id = payloadID;
    }


    abstract protected void found(P theirs, InetAddress who, int port);


    public void start() {
        synchronized (this) {


            try {
                ia = InetAddress.getByName(address);

                ms = new MulticastSocket(port);

                ms.setBroadcast(true);

                ms.setReuseAddress(true);
                ms.setTimeToLive(3);


                
                ms.setSoTimeout(TIMEOUT_MS);
                ms.joinGroup(ia);
                //System.out.println("ttl=" + ms.getTimeToLive());

                //HACK
//                NetworkInterface bestNic = NetworkInterface.networkInterfaces().max(Comparator.comparingInt(n -> n.getInterfaceAddresses().size())).get();
//                ms.setNetworkInterface(bestNic);
                //System.out.println("nic=" + ms.getNetworkInterface());

                theirID = new byte[MAX_PAYLOAD_ID];
                myID = Util.toBytes(id);
                p = new DatagramPacket(myID, myID.length, ia, port);
                q = new DatagramPacket(theirID, theirID.length);

                //busy.setAt(false);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }
    }


    public boolean update() {



        try {

//            if (!busy.compareAndSet(false, true))
//                return false;

            final MulticastSocket ms = this.ms;
            if (ms == null)
                return false;

            try {
                ms.send(p);
            } catch (IOException e) {
                logger.warn("{}", e);
            }
            

            try {
                ms.receive(q);
                P theirPayload;
                try {

                    int len = q.getLength();
                    byte[] qd = q.getData();
                    if (!Arrays.equals(p.getData(),qd) && !Arrays.equals(myID, 0, myID.length, qd, 0, len)) {
                        theirPayload = (P) Util.fromBytes(qd, len, id.getClass());
                        found(theirPayload, q.getAddress(), q.getPort());
                        
                    }
                    Arrays.fill(qd, (byte) 0);
                } catch (Exception e) {
                    logger.error("deserializing {}", e);
                }
            } catch (SocketTimeoutException ignored) {

            }

        } catch (Exception e) {
            logger.error("{} {}", this, e);
        }/* finally {
            busy.setAt(false);
        }*/

        return true;
    }

    public void stop() {
        synchronized (this) {
            //busy.setAt(true);
            if (ms != null) {
                ms.close();
                ms = null;
            }
        }
    }



    

































}
































































































































































































































































































































































































































