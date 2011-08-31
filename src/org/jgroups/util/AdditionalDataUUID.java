package org.jgroups.util;

import org.jgroups.Global;

import java.io.*;
import java.security.SecureRandom;

/**
 * Subclass of {@link org.jgroups.util.UUID} which adds a string as payload. An instance of this can be fed to
 * {@link org.jgroups.JChannel#setAddressGenerator(org.jgroups.stack.AddressGenerator)}, with the address generator
 * creating PayloadUUIDs.
 * @author Bela Ban
 */
public class AdditionalDataUUID extends UUID {
    protected byte[] payload;

    public AdditionalDataUUID() {
    }

    protected AdditionalDataUUID(byte[] data, byte[] payload) {
        super(data);
        this.payload=payload;
    }

    public static AdditionalDataUUID randomUUID(byte[] payload) {
        return new AdditionalDataUUID(generateRandomBytes(), payload);
    }

    public static AdditionalDataUUID randomUUID(String logical_name, byte[] payload) {
        AdditionalDataUUID retval=new AdditionalDataUUID(generateRandomBytes(), payload);
        UUID.add(retval, logical_name);
        return retval;
    }


    protected static byte[] generateRandomBytes() {
        SecureRandom ng=numberGenerator;
        if(ng == null)
            numberGenerator=ng=new SecureRandom();

        byte[] randomBytes=new byte[16];
        ng.nextBytes(randomBytes);
        return randomBytes;
    }

    public int size() {
        int retval=super.size() + Global.BYTE_SIZE;
        if(payload != null)
            retval+=payload.length;
        return retval;
    }

    public void writeTo(DataOutput out) throws IOException {
        super.writeTo(out);
        Util.writeByteBuffer(payload, out);
    }

    public void readFrom(DataInput in) throws IOException, IllegalAccessException, InstantiationException {
        super.readFrom(in);
        payload=Util.readByteBuffer(in);
    }


    public String toString() {
        if(print_uuids)
            return toStringLong() + (payload == null? "" : " (" + payload.length + " bytes)");
        return super.toString() + (payload == null? "" : " (" + payload.length + " bytes)");
    }

}
