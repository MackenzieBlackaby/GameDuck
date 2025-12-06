package com.blackaby.OldBackEnd.Emulation.Misc;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.File;

/**
 * Represents a Game Boy ROM file.
 * <p>
 * Loads the contents of the ROM from disk and provides access to the ROM's
 * binary data and metadata (filename, display name).
 * </p>
 */
public class ROM {
    private String filename;
    private int data[];

    /**
     * Constructs a ROM object with the given file path.
     * Automatically loads the ROM data into memory.
     *
     * @param filename The path to the ROM file.
     */
    public ROM(String filename) {
        this.filename = filename;
        if (!filename.isEmpty()) {
            try {
                byte[] fileBytes = Files.readAllBytes(Paths.get(filename));
                data = new int[fileBytes.length];
                for (int i = 0; i < fileBytes.length; i++) {
                    data[i] = fileBytes[i] & 0xFF;
                }
            } catch (IOException e) {
                e.printStackTrace();
                data = new int[0];
            }
        }
    }

    /**
     * Returns the raw ROM data as an array of unsigned 8-bit values.
     *
     * @return An array containing the ROM contents.
     */
    public int[] getData() {
        return data;
    }

    /**
     * Returns the display name of the ROM file (without path or extension).
     *
     * @return The ROM's base name.
     */
    public String getName() {
        File file = new File(filename);
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex != -1) {
            name = name.substring(0, dotIndex);
        }
        return name;
    }
}
