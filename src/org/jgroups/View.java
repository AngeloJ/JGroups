
package org.jgroups;


import org.jgroups.util.Streamable;
import org.jgroups.util.Util;

import java.io.*;
import java.util.*;


/**
 * A view is a local representation of the current membership of a group.
 * Only one view is installed in a channel at a time.
 * Views contain the address of its creator, an ID and a list of member addresses.
 * These addresses are ordered, and the first address is always the coordinator of the view.
 * This way, each member of the group knows who the new coordinator will be if the current one
 * crashes or leaves the group.
 * The views are sent between members using the VIEW_CHANGE event
 * @author Bela Ban
 */
public class View implements Cloneable, Streamable, Iterable<Address> {

    /* A view is uniquely identified by its ViewID
    * The view id contains the creator address and a Lamport time.
    * The Lamport time is the highest timestamp seen or sent from a view.
    * if a view change comes in with a lower Lamport time, the event is discarded.
    */
    protected ViewId vid=null;

    /**
     * A list containing all the members of the view
     * This list is always ordered, with the coordinator being the first member.
     * the second member will be the new coordinator if the current one disappears
     * or leaves the group.
     */
    protected List<Address> members=null;



    /**
     * creates an empty view, should not be used
     */
    public View() {
    }


    /**
     * Creates a new view
     *
     * @param vid     The view id of this view (can not be null)
     * @param members Contains a list of all the members in the view, can be empty but not null.
     */
    public View(ViewId vid, List<Address> members) {
        this.vid=vid;
        this.members=new ArrayList<Address>(members);
    }

    /**
     * Creates a new view
     *
     * @param creator The creator of this view (can not be null)
     * @param id      The lamport timestamp of this view
     * @param members Contains a list of all the members in the view, can be empty but not null.
     */
    public View(Address creator, long id, List<Address> members) {
        this(new ViewId(creator, id), members);
    }


    /**
     * returns the view ID of this view
     * if this view was created with the empty constructur, null will be returned
     *
     * @return the view ID of this view
     */
    public ViewId getVid() {
        return vid;
    }

    public ViewId getViewId() {return vid;}

    /**
     * returns the creator of this view
     * if this view was created with the empty constructur, null will be returned
     *
     * @return the creator of this view in form of an Address object
     */
    public Address getCreator() {
        return vid != null ? vid.getCoordAddress() : null;
    }

    /**
     * Returns a reference to the List of members (ordered)
     * Do NOT change this list, hence your will invalidate the view
     * Make a copy if you have to modify it.
     *
     * @return a reference to the ordered list of members in this view
     */
    public List<Address> getMembers() {
        return Collections.unmodifiableList(members);
    }

    /**
     * returns true, if this view contains a certain member
     *
     * @param mbr - the address of the member,
     * @return true if this view contains the member, false if it doesn't
     *         if the argument mbr is null, this operation returns false
     */
    public boolean containsMember(Address mbr) {
        return mbr != null && members.contains(mbr);
    }


    public boolean equals(Object obj) {
        if(!(obj instanceof View))
            return false;
        if(vid != null) {
            int rc=vid.compareTo(((View)obj).vid);
            if(rc != 0)
                return false;
            if(members != null && ((View)obj).members != null) {
                return members.equals(((View)obj).members);
            }
        }
        else {
            if(((View)obj).vid == null)
                return true;
        }
        return false;
    }


    public int hashCode() {
        return vid != null? vid.hashCode() : 0;
    }

    /**
     * returns the number of members in this view
     *
     * @return the number of members in this view 0..n
     */
    public int size() {
        return members.size();
    }


    public View copy() {
        ViewId vid2=vid != null ? (ViewId)vid.clone() : null;
        return new View(vid2, new ArrayList<Address>(members));
    }


    /**
     * creates a copy of this view
     * @return a copy of this view
     */
    public Object clone() {
        ViewId vid2=vid != null ? (ViewId)vid.clone() : null;
        return new View(vid2, new ArrayList<Address>(members));
    }


    public String toString() {
        StringBuilder ret=new StringBuilder(64);
        ret.append(vid).append(" ").append(members);
        return ret.toString();
    }



    public void writeTo(DataOutput out) throws IOException {
        // vid
        if(vid != null) {
            out.writeBoolean(true);
            vid.writeTo(out);
        }
        else
            out.writeBoolean(false);

        // members:
        Util.writeAddresses(members, out);
    }

    @SuppressWarnings("unchecked") 
    public void readFrom(DataInput in) throws IOException, IllegalAccessException, InstantiationException {
        boolean b;
        // vid:
        b=in.readBoolean();
        if(b) {
            vid=new ViewId();
            vid.readFrom(in);
        }

        // members:
        members=(List<Address>)Util.readAddresses(in, ArrayList.class);
    }

    public int serializedSize() {
        int retval=Global.BYTE_SIZE; // presence for vid
        if(vid != null)
            retval+=vid.serializedSize();
        retval+=Util.size(members);
        return retval;
    }


    public Iterator<Address> iterator() {
        return members.iterator();
    }
}
