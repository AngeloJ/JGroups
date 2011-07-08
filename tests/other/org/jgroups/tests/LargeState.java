

package org.jgroups.tests;


import org.jgroups.*;
import org.jgroups.jmx.JmxConfigurator;
import org.jgroups.util.Util;

import javax.management.MBeanServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Tests transfer of large states. Start first instance with -provider flag and -size flag (default = 1MB).
 * The start second instance without these flags: it should acquire the state from the first instance. Possibly
 * tracing should be turned on for FRAG to see the fragmentation taking place, e.g.:
 * <pre>
 * trace1=FRAG DEBUG STDOUT
 * </pre><br>
 * Note that because fragmentation might generate a lot of small fragments at basically the same time (e.g. size1MB,
 * FRAG.frag-size=4096 generates a lot of fragments), the send buffer of the unicast socket in UDP might be overloaded,
 * causing it to drop some packets (default size is 8096 bytes). Therefore the send (and receive) buffers for the unicast
 * socket have been increased (see ucast_send_buf_size and ucast_recv_buf_size below).<p>
 * If we didn't do this, we would have some retransmission, slowing the state transfer down.
 *
 * @author Bela Ban Dec 13 2001
 */
public class LargeState extends ReceiverAdapter {
    Channel  channel;
    byte[]   state=null;
    boolean  rc=false;
    String   props;
    long     start, stop;
    boolean  provider=true;
    int      size=100000;
    int      total_received=0;


    public void start(boolean provider, int size, String props) throws Exception {
        this.provider=provider;
        channel=new JChannel(props);
        channel.setReceiver(this);
        channel.connect("TestChannel");
        MBeanServer server=Util.getMBeanServer();
        if(server == null)
            throw new Exception("No MBeanServers found;" +
                                  "\nLargeState needs to be run with an MBeanServer present, or inside JDK 5");
        JmxConfigurator.registerChannel((JChannel)channel, server, "jgroups", channel.getClusterName(), true);
        System.out.println("-- connected to channel");

        if(provider) {
            this.size=size;
            System.out.println("Waiting for other members to join and fetch large state");
            for(;;) {
                Util.sleep(10000);
            }
        }
        System.out.println("Getting state");
        start=System.currentTimeMillis();
        try {
            rc=channel.getState(null, 0);
            System.out.println("getState(), rc=" + rc);
        }
        catch(Exception ex) {
            ex.printStackTrace();
        }
        finally {
            Util.close(channel);
        }
    }


    static byte[] createLargeState(int size) {
        return new byte[size];
    }

    public void receive(Message msg) {
        System.out.println("-- received msg " + msg.getObject() + " from " + msg.getSrc());
    }

    public void viewAccepted(View new_view) {
        if(provider)
            System.out.println("-- view: " + new_view);
    }

    public byte[] getState() {
        if(state == null) {
            System.out.println("creating state of " + size + " bytes");
            state=createLargeState(size);
        }
        System.out.println("--> returning state: " + state.length + " bytes");
        return state;
    }

    public void setState(byte[] state) {
        stop=System.currentTimeMillis();
        if(state != null) {
            this.state=state;
            System.out.println("<-- Received byte[] state, size=" + Util.printBytes(state.length) + " (took " + (stop-start) + "ms)");
        }
    }

    public void setState(InputStream istream) throws Exception {
        total_received=0;
        int received=0;
        while(true) {
            byte[] buf=new byte[10000];
            received=istream.read(buf);
            if(received < 0)
                break;
            total_received+=received;
        }

        stop=System.currentTimeMillis();
        System.out.println("<-- Received stream state, size=" + Util.printBytes(total_received) + " (took " + (stop-start) + "ms)");
    }

    public void getState(OutputStream ostream) throws Exception {
        int frag_size=size / 10;
        for(int i=0; i < 10; i++) {
            byte[] buf=new byte[frag_size];
            ostream.write(buf);
            // if(i > 5) throw new Exception("state provcider made BOOOOOM");
        }
        int remaining=size - (10 * frag_size);
        if(remaining > 0) {
            byte[] buf=new byte[remaining];
            ostream.write(buf);
        }
    }


    public static void main(String[] args) {
        boolean provider=false;
        int     size=1024 * 1024;
        String  props=null;



        for(int i=0; i < args.length; i++) {
            if("-help".equals(args[i])) {
                help();
                return;
            }
            if("-provider".equals(args[i])) {
            	provider=true;
                continue;
            }
            if("-size".equals(args[i])) {
                size=Integer.parseInt(args[++i]);
                continue;
            }
            if("-props".equals(args[i])) {
                props=args[++i];
                continue;
            }
            help();
            return;
        }


        try {
            new LargeState().start(provider, size, props);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    static void help() {
        System.out.println("LargeState [-help] [-size <size of state in bytes] [-provider] [-props <properties>]");
    }

}
