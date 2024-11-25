package com.blackaby.Backend.Emulation.Misc;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 * This class represents a ROM file.
 * It has a filename and the data of the ROM.
 * The data is stored as a byte array and can be read with the given function
 */
public class ROM {
    private String filename;
    private BinaryInstruction[] data;

    public ROM(String filename) {
        this.filename = filename;
        data = new BinaryInstruction[0];
    }

    private void LoadRom() {
        // Get size of the file
        int size = 0;
        try (FileReader reader = new FileReader(filename)) {
            while (reader.read() != -1) {
                size++;
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        // Read in raw data
        char buffer[] = new char[size];
        try (FileReader reader = new FileReader(filename)) {
            reader.read(buffer);
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // Convert raw data to instructions
        data = new BinaryInstruction[size];
    }

    /**
     * This method returns the filename of the ROM
     * 
     * @return The filename of the ROM
     */
    public String getFilename() {
        return filename;
    }

    /**
     * This method loads a preset list of instructions into the rom data
     * This is used for debugging
     */
    public void debugRomLoad() {
        data = new BinaryInstruction[2];
        data[0] = new BinaryInstruction(0xFF, 0x00);
        data[1] = new BinaryInstruction(0xFE, 0x00);
    }

    /**
     * This method reads an instruction from the ROM
     * 
     * @param PC The program counter
     * @return The instruction at the given program counter
     */
    public int getOpcode(int PC) {
        return data[PC].getOpcode();
    }

    /**
     * This method reads the operands of an instruction from the ROM
     * 
     * @param PC The program counter
     * @return The operands of the instruction at the given program counter
     */
    public int[] getOperands(int PC) {
        return data[PC].getOperands();
    }
}
