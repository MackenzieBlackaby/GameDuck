package com.blackaby.Backend.Emulation.CPU.Instructions;

/**
 * This enum represents the different types of instructions that the CPU can
 * execute.
 * Each instruction has an opcode and a description.
 * The opcode is the byte that represents the instruction in memory.
 * The description is a human-readable string that describes the instruction.
 */
public enum InstructionType {

    // Debug instructions
    DEBUG_DISPLAY(0xFF, "Debug Display Test", 0),
    DEBUG_CONSOLE(0xFE, "Debug Console Test", 0),;

    private final int opcode;
    private final String description;
    private final int operandCount;

    /**
     * Constructor for InstructionType.
     * 
     * @param opcode      The opcode of the instruction.
     * @param description The description of the instruction.
     */
    InstructionType(int opcode, String description, int operandCount) {
        this.opcode = opcode;
        this.description = description;
        this.operandCount = operandCount;
    }

    /**
     * This method returns the opcode of the instruction.
     * 
     * @return The opcode of the instruction.
     */
    public int getOpcode() {
        return opcode;
    }

    /**
     * This method returns the description of the instruction.
     * 
     * @return The description of the instruction.
     */
    public String getDescription() {
        return description;
    }

    /**
     * This method returns the number of operands that the instruction takes.
     * 
     * @return The number of operands that the instruction takes.
     */
    public int getOperandCount() {
        return operandCount;
    }

    /**
     * This method returns the InstructionType with the given opcode.
     * 
     * @param opcode The opcode of the instruction.
     * @return The InstructionType with the given opcode.
     */
    public static InstructionType fromOpcode(int opcode) {
        for (InstructionType type : values()) {
            if (type.opcode == opcode) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown opcode: " + opcode);
    }
}