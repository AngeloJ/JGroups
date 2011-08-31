
package org.jgroups.protocols.pbcast;


import org.jgroups.Global;
import org.jgroups.View;
import org.jgroups.util.Digest;
import org.jgroups.util.Streamable;
import org.jgroups.util.Util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;


/**
 * Result of a JOIN request (sent by the GMS client). Instances of this class are immutable.
 */
public class JoinRsp implements Streamable {
    private View view=null;
    private Digest digest=null;
    /** only set if JOIN failed, e.g. in AUTH */
    private String fail_reason=null;


    public JoinRsp() {

    }

    public JoinRsp(View v, Digest d) {
        view=v;
        digest=d;
    }

    public JoinRsp(String fail_reason) {
        this.fail_reason=fail_reason;
    }

    public View getView() {
        return view;
    }

    public Digest getDigest() {
        return digest;
    }

    public String getFailReason() {
        return fail_reason;
    }

    public void setFailReason(String r) {
        fail_reason=r;
    }


    public void writeTo(DataOutput out) throws IOException {
        Util.writeStreamable(view, out);
        Util.writeStreamable(digest, out);
        Util.writeString(fail_reason, out);
    }

    public void readFrom(DataInput in) throws IOException, IllegalAccessException, InstantiationException {
        view=(View)Util.readStreamable(View.class, in);
        digest=(Digest)Util.readStreamable(Digest.class, in);
        fail_reason=Util.readString(in);
    }

    public int serializedSize() {
        int retval=Global.BYTE_SIZE * 2; // presence for view and digest
        if(view != null)
            retval+=view.serializedSize();
        if(digest != null)
            retval+=digest.serializedSize();

        retval+=Global.BYTE_SIZE; // presence byte for fail_reason
        if(fail_reason != null)
            retval+=fail_reason.length() +2;
        return retval;
    }

    public String toString() {
        StringBuilder sb=new StringBuilder();
        sb.append("view: ");
        if(view == null)
            sb.append("<null>");
        else
            sb.append(view);
        sb.append(", digest: ");
        if(digest == null)
            sb.append("<null>");
        else
            sb.append(digest);
        if(fail_reason != null)
            sb.append(", fail reason: ").append(fail_reason);
        return sb.toString();
    }
}
