package com.blackaby.Backend.Emulation.Misc;

/**
 * This class represents a binary instruction
 * It has an opcode, length, and operands
 * The opcode is the byte that represents the instruction in memory
 * The length is the number of bytes the instruction takes up in memory
 * The operands are the arguments of the instruction
 * The operands are stored as an array of integers
 */
public class BinaryInstruction {
    private int opcode;
    private int length;
    private int operands[];

    /**
     * Constructor for BinaryInstruction
     * 
     * @param opcode   The opcode of the instruction
     * @param operands The operands of the instruction
     */
    public BinaryInstruction(int opcode, int... operands) {
        this.opcode = opcode;
        this.length = operands.length + 1;
        this.operands = operands;
    }

    /**
     * This method returns the opcode of the instruction
     * 
     * @return The opcode of the instruction
     */
    public int getOpcode() {
        return opcode;
    }

    /**
     * This method returns the length of the instruction
     * 
     * @return The length of the instruction
     */
    public int getLength() {
        return length;
    }

    /**
     * This method returns the operands of the instruction
     * 
     * @return The operands of the instruction
     */
    public int[] getOperands() {
        return operands;
    }

    /**
     * This method returns the operand at the given index
     * 
     * @param index The index of the operand
     * @return The operand at the given index
     */
    public int getOperand(int index) {
        return operands[index];
    }
}
