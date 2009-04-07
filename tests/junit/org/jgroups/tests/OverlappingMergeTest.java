package org.jgroups.tests;

import org.jgroups.*;
import org.jgroups.protocols.FD;
import org.jgroups.protocols.FD_ALL;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.stack.ProtocolStack;
import org.jgroups.util.Util;

import java.util.*;

/**
 * Tests overlapping merges, e.g. A: {A,B}, B: {A,B} and C: {A,B,C}. Tests unicast as well as multicast seqno tables.<br/>
 * Related JIRA: https://jira.jboss.org/jira/browse/JGRP-940
 * @author Bela Ban
 * @version $Id: OverlappingMergeTest.java,v 1.1.2.9 2009/04/07 08:01:45 belaban Exp $
 */
public class OverlappingMergeTest extends ChannelTestBase {
    private JChannel a, b, c;
    private MyReceiver ra, rb, rc;

    protected void setUp() throws Exception {
        super.setUp();
        ra=new MyReceiver("A"); rb=new MyReceiver("B"); rc=new MyReceiver("C");
        a=createChannel(); a.setReceiver(ra);
        b=createChannel(); b.setReceiver(rb);
        c=createChannel(); c.setReceiver(rc);
        modifyConfigs(a, b, c);

        a.connect("testUnicastingAfterOverlappingMerge");
        b.connect("testUnicastingAfterOverlappingMerge");
        c.connect("testUnicastingAfterOverlappingMerge");
        View view=c.getView();
        assertEquals("view is " + view, 3, view.size());
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        Util.close(c,b,a);
        ra.clear(); rb.clear(); rc.clear();
    }

    /**
     * Verifies that unicasts are received correctly by all participants after an overlapping merge. The following steps
     * are executed:
     * <ol>
     * <li/>Group is {A,B,C}, disable shunning in all members. A is the coordinator
     * <li/>MERGE2 is removed from all members
     * <li/>VERIFY_SUSPECT is removed from all members
     * <li/>Everyone sends 5 unicast messages to everyone else
     * <li/>Everyone sends 5 multicasts
     * <li/>A SUSPECT(A) event is injected into B's stack (GMS). This causes a new view {B,C} to be multicast by B
     * <li/>B and C install {B,C}
     * <li/>B and C trash the connection table for A in UNICAST
     * <li/>A ignores the view, it still has view {A,B,C} and all connection tables intact in UNICAST
     * <li/>We now inject a MERGE(A,B) event into A. This should ause A and B as coords to create a new MergeView {A,B,C}
     * <li/>The merge already fails because the unicast between A and B fails due to the reason given below !
     *      Once this is fixed, the next step below should work, too !
     * <li/>A sends a unicast to B and C. This should fail until JGRP-940 has been fixed !
     * <li/>Reason: B and C trashed A's conntables in UNICAST, but A didn't trash its conn tables for B and C, so
     * we have non-matching seqnos !
     * </ol>
     */
    public void testOverlappingMergeWithBC() throws Exception {
        sendAndCheckMessages(5, a, b, c);

        // Inject view {B,C} into B and C:
        View new_view=Util.createView(b.getLocalAddress(), 10, b.getLocalAddress(), c.getLocalAddress());
        System.out.println("\n ==== Injecting view " + new_view + " into B and C ====");
        injectView(new_view, b, c);

        System.out.println("A's view: " + a.getView());
        System.out.println("B's view: " + b.getView());
        System.out.println("C's view: " + c.getView());
        assertEquals("A's view is " + a.getView(), 3, a.getView().size());
        assertEquals("B's view is " + b.getView(), 2, b.getView().size());
        assertEquals("C's view is " + c.getView(), 2, c.getView().size());

        System.out.println("\n==== Sending messages while the cluster is partitioned ====");
        sendAndCheckMessages(5, a, b, c);

        // start merging
        Vector<Address> coords=new Vector<Address>(2);
        coords.add(a.getLocalAddress()); coords.add(b.getLocalAddress());
        Event merge_evt=new Event(Event.MERGE, coords);
        JChannel merge_leader=determineMergeLeader(a, b);
        System.out.println("\n==== Starting the merge (leader=" + merge_leader.getLocalAddress() + ") ====");
        injectMergeEvent(merge_evt, merge_leader);

        System.out.println("\n==== checking views after merge ====:");
        for(int i=0; i < 20; i++) {
            if(a.getView().size() == 3 && b.getView().size() == 3 && c.getView().size() == 3) {
                System.out.println("views are correct: all views have a size of 3");
                break;
            }
            System.out.print(".");
            Util.sleep(500);
        }

        View va=a.getView(), vb=b.getView(), vc=c.getView();

        System.out.println("\nA's view: " + va);
        System.out.println("B's view: " + vb);
        System.out.println("C's view: " + vc);
        assertEquals("A's view is " + va, 3, va.size());
        assertEquals("B's view is " + vb, 3, vb.size());
        assertEquals("C's view is " + vc, 3, vc.size());

        ra.clear(); rb.clear(); rc.clear();
        System.out.println("Sending messages after merge");
        sendAndCheckMessages(5, a, b, c);
        Util.sleep(1000); // sleep a little to make sure async msgs have been received
        checkReceivedMessages(5, ra, rb, rc);
    }

    private static JChannel determineMergeLeader(JChannel ... coords) {
        Membership tmp=new Membership();
        for(JChannel ch: coords) {
            tmp.add(ch.getLocalAddress());
        }
        tmp.sort();
        Address  merge_leader=tmp.elementAt(0);
        for(JChannel ch: coords) {
            if(ch.getLocalAddress().equals(merge_leader))
                return ch;
        }
        return null;
    }

    private static void injectView(View view, JChannel ... channels) {
        for(JChannel ch: channels) {
            ch.down(new Event(Event.VIEW_CHANGE, view));
            ch.up(new Event(Event.VIEW_CHANGE, view));
        }
        for(JChannel ch: channels) {
            MyReceiver receiver=(MyReceiver)ch.getReceiver();
            System.out.println("[" + receiver.name + "] view=" + ch.getView());
        }
    }

    private static void injectSuspectEvent(JChannel ch, Address suspected_mbr) {
        GMS gms=(GMS)ch.getProtocolStack().findProtocol(GMS.class);
        gms.up(new Event(Event.SUSPECT, suspected_mbr));
    }

    private static void injectMergeEvent(Event evt, JChannel ... channels) {
        for(JChannel ch: channels) {
            GMS gms=(GMS)ch.getProtocolStack().findProtocol(GMS.class);
            gms.up(evt);
        }
    }



    private void sendAndCheckMessages(int num_msgs, JChannel ... channels) throws Exception {
        ra.clear(); rb.clear(); rc.clear();

        Set<Address> mbrs=new HashSet<Address>(channels.length);
        for(JChannel ch: channels)
            mbrs.add(ch.getLocalAddress());

        // 1. send multicast messages
        for(JChannel ch: channels) {
            Address addr=ch.getLocalAddress();
            for(int i=1; i <= 5; i++)
                ch.send(null, null, "multicast msg #" + i + " from " + addr);
        }

        // 2. send unicast messages
        for(JChannel ch: channels) {
            Address addr=ch.getLocalAddress();
            for(Address dest: mbrs) {
                for(int i=1; i <= num_msgs; i++) {
                    ch.send(dest, null, "unicast msg #" + i + " from " + addr);
                }
            }
        }
        Util.sleep(2000);
        MyReceiver[] receivers=new MyReceiver[channels.length];
        for(int i=0; i < channels.length; i++)
            receivers[i]=(MyReceiver)channels[i].getReceiver();
        checkReceivedMessages(num_msgs, receivers);
    }
    

    private static void checkReceivedMessages(int num_msgs, MyReceiver ... receivers) {
        for(MyReceiver receiver: receivers) {
            List<Message> mcasts=receiver.getMulticasts();
            List<Message> ucasts=receiver.getUnicasts();
            int mcasts_received=mcasts.size();
            int ucasts_received=ucasts.size();
            System.out.println("receiver " + receiver + ": mcasts=" + mcasts_received + ", ucasts=" + ucasts_received);
        }
        int total_unicasts=receivers.length * num_msgs;
        for(MyReceiver receiver: receivers) {
            List<Message> mcasts=receiver.getMulticasts();
            List<Message> ucasts=receiver.getUnicasts();
            int mcasts_received=mcasts.size();
            int ucasts_received=ucasts.size();
            int total_mcasts=receiver.view.size() * num_msgs;
            assertEquals("ucasts: " + print(ucasts), total_unicasts, ucasts_received);
            assertEquals("num_mcasts=" + print(mcasts), total_mcasts, mcasts_received);
        }
    }

    private static String print(List<Message> msgs) {
        StringBuilder sb=new StringBuilder();
        for(Message msg: msgs) {
            sb.append(msg.getSrc()).append(": ").append(msg.getObject()).append(" ");
        }
        return sb.toString();
    }


    private static void modifyConfigs(JChannel ... channels) throws Exception {
        for(JChannel ch: channels) {
            ProtocolStack stack=ch.getProtocolStack();

            FD fd=(FD)stack.findProtocol(FD.class);
            if(fd != null)
                fd.setShun(false);

            FD_ALL fd_all=(FD_ALL)stack.findProtocol(FD_ALL.class);
            if(fd_all != null)
                fd_all.setShun(false);

            stack.removeProtocol("MERGE2");
            stack.removeProtocol("FC");
            stack.removeProtocol("VERIFY_SUSPECT");
        }
    }



    private static class MyReceiver extends ReceiverAdapter {
        final String name;
        View view=null;
        final List<Message> mcasts=new ArrayList<Message>(20);
        final List<Message> ucasts=new ArrayList<Message>(20);

        public MyReceiver(String name) {
            this.name=name;
        }

        public void receive(Message msg) {
            Address dest=msg.getDest();
            boolean mcast=dest == null;
            if(mcast)
                mcasts.add(msg);
            else
                ucasts.add(msg);
            // System.out.println("received " + (mcast? "mcast" : "ucast") + " msg from " + msg.getSrc());
        }

        public void viewAccepted(View new_view) {
            // System.out.println("[" + name + "] " + new_view);
            view=new_view;
        }

        public List<Message> getMulticasts() { return mcasts; }
        public List<Message> getUnicasts() { return ucasts; }
        public void clear() {mcasts.clear(); ucasts.clear();}

        public String toString() {
            return name;
        }
    }



}