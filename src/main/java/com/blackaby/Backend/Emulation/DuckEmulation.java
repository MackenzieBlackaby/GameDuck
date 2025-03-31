package com.blackaby.Backend.Emulation;

import com.blackaby.Frontend.DebugLogger;
import com.blackaby.Backend.Emulation.CPU.*;
import com.blackaby.Backend.Emulation.Misc.ROM;
import com.blackaby.Backend.Emulation.Peripherals.DuckTimer;
import com.blackaby.Frontend.DuckDisplay;
import com.blackaby.Backend.Emulation.CPU.InstructionTypeManager.InstructionType;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

/**
 * This class represents the emulation of the GameBoy
 * It has methods for starting the emulation and getting the CPU and display
 */
public class DuckEmulation implements Runnable {

    // Emulated Hardware Parts
    private DuckCPU cpu;
    private DuckMemory memory;
    private DuckDisplay display;
    private DuckPPU ppu;
    private ROM rom;
    private DuckTimer timerSet;

    // Threading Variables
    private Thread emulationThread;
    private int clockCounter = 0;
    private volatile boolean running = false;
    private volatile boolean paused = false;
    private Instruction instruction = null;

    /**
     * This constructor creates a new DuckEmulation
     * 
     * @param display The display to be used in the emulation
     */
    public DuckEmulation(DuckDisplay display) {
        this.display = display;
    }

    /**
     * This method starts the emulation with the given ROM file
     * 
     * @param romfile The ROM file to be loaded
     */
    public void startEmulation(String romfile) {
        running = true;
        paused = false;
        // Initialise hardware
        rom = new ROM(romfile);
        memory = new DuckMemory();
        cpu = new DuckCPU(memory, this);
        InstructionTypeManager.initialiseMap(cpu, memory, this);
        ppu = new DuckPPU(cpu, memory, display);
        timerSet = new DuckTimer(cpu, memory);
        memory.setTimerSet(timerSet);
        // Set hardware references
        // Start emulation thread
        emulationThread = new Thread(this);
        emulationThread.start();
    }

    /**
     * This method pauses or resumes the emulation
     */
    public void pauseEmulation() {
        paused = !paused;
    }

    /**
     * This method stops the emulation
     */
    public void stopEmulation() {
        running = false;
        paused = false;
    }

    public void run() {
        memory.write(DuckMemory.LCDC, 0x91);
        // Set initial values
        cpu.setPC(0x0100);
        cpu.setAF(0x01B0);
        cpu.setBC(0x0013);
        cpu.setDE(0x00D8);
        cpu.setHL(0x014D);
        cpu.setSP(0xFFFE);
        memory.write(0xFF47, 0xFC);
        memory.write(0xFF00, 0xFF);
        // Load rom
        memory.loadROM(rom);
        // Main loop for emulation
        while (running) {
            try {
                InstructionTick(false);
                if (paused) {
                    while (paused)
                        Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // Clean up emulation after stopping
        rom = null;
        cpu = null;
        memory = null;
        ppu = null;
        timerSet = null;
        display.clear();
        emulationThread = null;
    }

    private void InstructionTick(boolean skipStops) throws InterruptedException {
        // check if an interrupt is pending
        boolean interruptPending = (memory.getIE() & memory.getIF() & 0x1F) != 0;
        if (cpu.isStopped() && !skipStops) {
            // STOP only exits via Joypad interrupt
            if ((memory.getIE() & memory.getIF() & 0x10) != 0) {
                cpu.setStopped(false);
            }
            return;
        } else if (cpu.isHalted()) {
            if (interruptPending) {
                cpu.setHalted(false);
                // CPU resumes; if IME is enabled, interrupt will be serviced next cycle
            }
            clockCounter = 1; // idle cycle during HALT
        } else {
            instruction = ReadNextInstruction();
            clockCounter = 4 * instruction.getCycleCount();
            cpu.execute(instruction);
        }
        // Always tick hardware (PPU, timers, serial) normally
        for (; clockCounter > 0; clockCounter--) {
            timerSet.tick();
            ppu.step();
            handleSerial();
            memory.tickDMA();
        }
    }

    private void handleSerial() {
        int serialControl = memory.read(DuckMemory.SERIAL_CONTROL);
        int serialData = memory.read(DuckMemory.SERIAL_DATA);

        // If transfer start flag (bit 7) is set AND internal clock (bit 0) is used
        if ((serialControl & 0x81) == 0x81) {

            // Optionally log it or store it elsewhere
            DebugLogger.serialOutput(serialData);

            // Write received data back (0xFF is common for unconnected)
            memory.write(DuckMemory.SERIAL_DATA, 0xFF);

            // Clear transfer start flag (bit 7)
            memory.write(DuckMemory.SERIAL_CONTROL, serialControl & ~0x80);

            // Optionally request the Serial interrupt (optional in test ROMs)
            // cpu.requestInterrupt(DuckCPU.Interrupt.SERIAL);
        }
    }

    /**
     * This method reads the next instruction from the ROM, incrementing the PC and
     * returning the instruction
     * as an array of integers
     * 
     * @return The next instruction as an array of integers
     */
    private Instruction ReadNextInstruction() {
        // Get PC
        int pc = cpu.getPC();

        // Get next Byte
        int opcode = memory.read(pc);
        boolean isCB = false;
        pc++;

        // Check for CB prefix
        if (opcode == 0xCB) {
            opcode = memory.read(pc);
            isCB = true;
            pc++;
        }

        // Get operand type
        InstructionType type = InstructionTypeManager.getType(opcode, isCB);
        // Check for unknown opcode
        if (type == null) {
            DebugLogger.logn("Unknown opcode: 0x" + Integer.toHexString(opcode));
            System.exit(1);
        } else if (type == InstructionType.ENABLE_INTERRUPTS) {
            DebugLogger.logn("Enable interrupts recognised\n\n\n\n\n\n\n\n\n\n\n\n\n");
            DebugLogger.logn(cpu.toString());
        }

        // Get operand count
        int operandCount = type.getOperandCount();

        // Read in next bytes
        int[] operands = {};
        if (operandCount > 0) {
            operands = memory.readBytes(pc, operandCount);
            pc += operandCount;
        }

        // // * Debug Print
        // if (cpu.isInterruptMasterEnable() || cpu.getPC() < 0x100) {
        // DebugLogger.log("[DECODING INSTRUCTION] PC: 0x" +
        // Integer.toHexString(cpu.getPC()) + " ");
        // DebugLogger.log("Opcode: 0x" + Integer.toHexString(opcode) + " ");
        // DebugLogger.log("Operands: { ");
        // for (int i = 0; i < operandCount; i++) {
        // DebugLogger.log("0x" + Integer.toHexString(operands[i]) + " ");
        // }
        // DebugLogger.logn("} Type: " + type);
        // DebugLogger.logn(cpu.toString());
        // }
        // // *

        // Update PC
        if (!cpu.getHaltBug())
            cpu.setPC(pc);

        // Construct instruction
        return InstructionTypeManager.constructInstruction(type, opcode,
                operands);
    }
}
