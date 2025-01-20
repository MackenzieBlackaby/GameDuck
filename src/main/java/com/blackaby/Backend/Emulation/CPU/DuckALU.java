package com.blackaby.Backend.Emulation.CPU;

import com.blackaby.Backend.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Register;

/**
 * This class represents the Arithmetic Logic Unit of the CPU
 * It contains methods for performing arithmetic operations on the CPU
 */
public class DuckALU {

    private DuckCPU cpu;

    /**
     * Creates a new DuckALU
     */
    public DuckALU(DuckCPU boundCPU) {
        this.cpu = boundCPU;
    }

    /**
     * This method adds the value of the source register to the value of the
     * destination register and stores the result in the destination register
     * 
     * @param source      The register to add
     * @param destination The register to store the result
     */
    public void Add(Register source, Register destination) {
        byte valSource = cpu.regGet(source);
        Add(valSource, destination);
    }

    /**
     * This method adds the immediate value to the value of the destination register
     * and stores the result in the destination register
     * 
     * @param immediate   The immediate value to add
     * @param destination The register to store the result
     */
    public void Add(byte immediate, Register destination) {
        byte valDestination = cpu.regGet(destination);
        // Perform addition with proper 8-bit handling
        int result = (valDestination & 0xFF) + (immediate & 0xFF);
        byte output = (byte) (result & 0xFF);
        cpu.regSet(destination, output);

        // Update flags
        cpu.setFlag(Flag.Z, (output & 0xFF) == 0);
        cpu.setFlag(Flag.N, false);
        cpu.setFlag(Flag.H, ((valDestination & 0xF) + (immediate & 0xF)) > 0xF);
        cpu.setFlag(Flag.C, result > 0xFF);
    }

    /**
     * This method subtracts the value of the source register from the value of the
     * destination register and stores the result in the destination register
     * 
     * @param source
     * @param destination
     */
    public void Sub(Register source, Register destination) {
        byte valSource = cpu.regGet(source);
        Sub(valSource, destination);
    }

    /**
     * This method subtracts the immediate value from the value of the destination
     * register and stores the result in the destination register
     * 
     * @param immediate   The immediate value to subtract
     * @param destination The register to store the result
     */
    public void Sub(byte immediate, Register destination) {
        byte valDestination = cpu.regGet(destination);
        short result = (short) (valDestination - immediate);
        cpu.regSet(destination, (byte) result);

        updateSubFlags(result, valDestination, immediate);
    }

    /**
     * This function updates the flags for a subtraction operation
     * This is separated to avoid repeat code
     * 
     * @param result       the result of the subtraction
     * @param subtractFrom the original value of the subtraction
     * @param subtractWith the value to subtract
     */
    public void updateSubFlags(short result, byte subtractFrom, byte subtractWith) {
        cpu.setFlag(Flag.Z, result == 0);
        cpu.setFlag(Flag.N, true);
        cpu.setFlag(Flag.H, (subtractFrom & 0xF) - (subtractWith & 0xF) < 0);
        cpu.setFlag(Flag.C, result < 0);
    }
}
