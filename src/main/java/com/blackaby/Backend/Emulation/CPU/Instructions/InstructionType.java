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
    DEBUG_DISPLAY(0xFF, "Debug Display Test"),
    DEBUG_CONSOLE(0xFE, "Debug Console Test"),;

    private final int opcode;
    private final String description;

    /**
     * Constructor for InstructionType.
     * 
     * @param opcode      The opcode of the instruction.
     * @param description The description of the instruction.
     */
    InstructionType(int opcode, String description) {
        this.opcode = opcode;
        this.description = description;
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