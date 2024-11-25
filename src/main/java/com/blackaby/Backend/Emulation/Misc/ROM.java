package com.blackaby.Backend.Emulation.Misc;

import com.blackaby.Backend.Emulation.CPU.Instructions.InstructionType;
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
        LoadRom();
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
        for (int i = 0; i < data.length; i++) {
            // Get the type of the instruction and the number of operands
            InstructionType currentType = InstructionType.fromOpcode(buffer[i]);
            int operandCount = currentType.getOperandCount();
            // Get the opcode and operands
            int opcode = currentType.getOpcode();
            int[] operands = new int[operandCount];
            for (int j = 0; j < operands.length; j++) {
                operands[j] = buffer[i + j + 1];
            }
            // Create the instruction
            data[i] = new BinaryInstruction(opcode, operands);
            // Increment the index by the number of operands
            i += operandCount;
        }
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
