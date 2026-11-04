package net.dvmn2.soundmanagerplugin;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Writers for the Minecraft wire formats used by the packets to the mod. Stateless. */
final class Wire {

    private static final int SEGMENT_MASK = 0x7F;
    private static final int CONTINUE_BIT = 0x80;
    private static final int SEGMENT_BITS = 7;

    private Wire() {
    }

    /** Writes a VarInt as in the Minecraft protocol. */
    static void writeVarInt(DataOutput out, int value) throws IOException {
        int remaining = value;
        while ((remaining & ~SEGMENT_MASK) != 0) {
            out.writeByte((remaining & SEGMENT_MASK) | CONTINUE_BIT);
            remaining >>>= SEGMENT_BITS;
        }
        out.writeByte(remaining);
    }

    /**
     * Writes a string as a VarInt byte length followed by UTF-8 bytes.
     *
     * @throws IllegalArgumentException if the encoded string is longer than {@code maxBytes}
     */
    static void writeString(DataOutput out, String value, int maxBytes) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        if (data.length > maxBytes) {
            throw new IllegalArgumentException("String is longer than " + maxBytes + " bytes");
        }
        writeVarInt(out, data.length);
        out.write(data);
    }
}
