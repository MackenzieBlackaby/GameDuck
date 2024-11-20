package com.blackaby.Backend.Emulation.CPU;

import java.util.LinkedList;
import java.util.Queue;

import com.blackaby.Backend.Emulation.DuckEmulation;
import com.blackaby.Backend.Emulation.CPU.Instructions.*;

/**
 * This class represents the CPU of the GameBoy.
 * It has methods for executing instructions and managing registers.
 * It also has methods for reading and writing to memory.
 * It is the main class for the CPU emulation.
 * 
 * The instructions are implemented as methods in this class.
 * The CPU has a reference to the memory and the display.
 */
public class DuckCPU {

    private Queue<Duckstruction> instructionQueue;
    private DuckEmulation boundEmulator;

    public DuckCPU(DuckEmulation boundEmulator) {
        this.boundEmulator = boundEmulator;
        instructionQueue = new LinkedList<>();
    }

    /**
     * This method queues an instruction
     */
    public void queueInstruction(int opcode, int... values) {
        InstructionType type = InstructionType.fromOpcode(opcode);
        switch (type) {
            case DEBUG_DISPLAY:
                instructionQueue.add(new DebugDisplay(boundEmulator.getDisplay()));
                break;
            default:
                throw new IllegalArgumentException("Unknown opcode: " + opcode);
        }
    }

    /**
     * This method executes the next instruction in the queue
     */
    public void executeNextInstruction() {
        Duckstruction instruction = instructionQueue.poll();
        if (instruction != null) {
            instruction.execute();
        }
    }
}
