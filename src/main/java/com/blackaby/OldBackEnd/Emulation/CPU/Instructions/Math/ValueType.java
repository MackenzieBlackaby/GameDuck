package com.blackaby.OldBackEnd.Emulation.CPU.Instructions.Math;

/**
 * Represents the source type for instruction operands.
 * 
 * Used to determine whether a value comes from a register,
 * the memory address pointed to by HL, or an immediate operand.
 */
public enum ValueType {
    /**
     * Operand is a register.
     */
    REGISTER,

    /**
     * Operand is the byte at the memory location pointed to by HL.
     */
    HL_MEMORY,

    /**
     * Operand is an immediate 8-bit value.
     */
    IMMEDIATE
}
