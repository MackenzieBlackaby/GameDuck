package com.blackaby.Backend.GB.Misc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Applies IPS patches to ROM byte arrays.
 */
public final class GBIpsPatch {

    private static final byte[] patchHeader = { 'P', 'A', 'T', 'C', 'H' };
    private static final byte[] eofMarker = { 'E', 'O', 'F' };

    private GBIpsPatch() {
    }

    /**
     * Applies an IPS patch file to a base ROM image.
     *
     * @param romBytes  base ROM bytes
     * @param patchPath patch file path
     * @return patched ROM bytes
     * @throws IOException when the patch file cannot be read
     */
    public static byte[] Apply(byte[] romBytes, Path patchPath) throws IOException {
        return Apply(romBytes, Files.readAllBytes(patchPath));
    }

    /**
     * Applies an IPS patch to a base ROM image.
     *
     * @param romBytes   base ROM bytes
     * @param patchBytes IPS patch bytes
     * @return patched ROM bytes
     */
    public static byte[] Apply(byte[] romBytes, byte[] patchBytes) {
        if (romBytes == null) {
            throw new IllegalArgumentException("Base ROM bytes are required.");
        }
        if (patchBytes == null || patchBytes.length < patchHeader.length + eofMarker.length) {
            throw new IllegalArgumentException("IPS patch is too short.");
        }
        for (int index = 0; index < patchHeader.length; index++) {
            if (patchBytes[index] != patchHeader[index]) {
                throw new IllegalArgumentException("Invalid IPS patch header.");
            }
        }

        byte[] output = Arrays.copyOf(romBytes, romBytes.length);
        int cursor = patchHeader.length;

        while (cursor < patchBytes.length) {
            if (MatchesAt(patchBytes, cursor, eofMarker)) {
                cursor += eofMarker.length;
                if (patchBytes.length - cursor == 3) {
                    int truncatedSize = ReadUInt24(patchBytes, cursor);
                    output = Resize(output, truncatedSize);
                    cursor += 3;
                }
                if (cursor != patchBytes.length) {
                    throw new IllegalArgumentException("Unexpected trailing data in IPS patch.");
                }
                return output;
            }

            if (cursor + 5 > patchBytes.length) {
                throw new IllegalArgumentException("IPS patch ended in the middle of a record.");
            }

            int offset = ReadUInt24(patchBytes, cursor);
            cursor += 3;

            int size = ReadUInt16(patchBytes, cursor);
            cursor += 2;

            if (size == 0) {
                if (cursor + 3 > patchBytes.length) {
                    throw new IllegalArgumentException("IPS patch ended in the middle of an RLE record.");
                }

                int runLength = ReadUInt16(patchBytes, cursor);
                cursor += 2;
                int value = patchBytes[cursor] & 0xFF;
                cursor += 1;

                output = EnsureCapacity(output, offset + runLength);
                Arrays.fill(output, offset, offset + runLength, (byte) value);
                continue;
            }

            if (cursor + size > patchBytes.length) {
                throw new IllegalArgumentException("IPS patch ended in the middle of record data.");
            }

            output = EnsureCapacity(output, offset + size);
            System.arraycopy(patchBytes, cursor, output, offset, size);
            cursor += size;
        }

        throw new IllegalArgumentException("IPS patch did not contain an EOF marker.");
    }

    private static boolean MatchesAt(byte[] bytes, int offset, byte[] expected) {
        if (offset + expected.length > bytes.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (bytes[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static int ReadUInt16(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static int ReadUInt24(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 16)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | (bytes[offset + 2] & 0xFF);
    }

    private static byte[] EnsureCapacity(byte[] bytes, int requiredLength) {
        if (requiredLength <= bytes.length) {
            return bytes;
        }
        return Arrays.copyOf(bytes, requiredLength);
    }

    private static byte[] Resize(byte[] bytes, int requiredLength) {
        return Arrays.copyOf(bytes, Math.max(0, requiredLength));
    }
}
