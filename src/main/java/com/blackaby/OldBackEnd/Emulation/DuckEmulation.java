package com.blackaby.OldBackEnd.Emulation;

import com.blackaby.Frontend.DebugLogger;
import com.blackaby.Frontend.DuckDisplay;
import com.blackaby.Frontend.MainWindow;
import com.blackaby.OldBackEnd.Emulation.CPU.*;
import com.blackaby.OldBackEnd.Emulation.CPU.DuckDecoder.InstructionType;
import com.blackaby.OldBackEnd.Emulation.Memory.DuckMemory;
import com.blackaby.OldBackEnd.Emulation.Misc.ROM;
import com.blackaby.OldBackEnd.Emulation.Misc.Specifics;
import com.blackaby.OldBackEnd.Emulation.Peripherals.DuckTimer;

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
    private MainWindow mainWindow;

    // Threading Variables
    private Thread emulationThread;
    private volatile boolean running = false;
    private volatile boolean paused = false;
    private Instruction instruction = null;
    private int frames = 0;
    private int prevLY = 0;
    private String romName = null;

    /**
     * This constructor creates a new DuckEmulation
     * * @param display The display to be used in the emulation
     */
    public DuckEmulation(MainWindow window, DuckDisplay display) {
        this.display = display;
        this.mainWindow = window;
    }

    /**
     * This method starts the emulation with the given ROM file
     * * @param romfile The ROM file to be loaded
     */
    public void startEmulation(String romfile) {
        running = true;
        paused = false;
        // Initialise hardware
        rom = new ROM(romfile);
        romName = rom.getName();
        memory = new DuckMemory();
        cpu = new DuckCPU(memory, this);
        DuckDecoder.initialiseMap(cpu, memory, this);
        ppu = new DuckPPU(cpu, memory, display);
        timerSet = new DuckTimer(cpu, memory);
        memory.setTimerSet(timerSet);
        // Set hardware references
        // Start emulation thread
        mainWindow.subtitle(romName, "[" + frames + " FPS]");
        emulationThread = new Thread(this);
        emulationThread.start();
    }

    /**
     * This method pauses or resumes the emulation
     */
    public void pauseEmulation() {
        paused = !paused;
        if (paused) {
            mainWindow.subtitle("Paused");
        } else {
            mainWindow.subtitle(romName, "[" + frames + " FPS]");
        }
    }

    /**
     * This method stops the emulation
     */
    public void stopEmulation() {
        running = false;
        paused = false;
        mainWindow.subtitle();
    }

    /**
     * This method is the main loop of the emulation
     */
    public void run() {
        // Load rom
        memory.loadROM(rom);
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
        memory.write(DuckMemory.LCDC, 0x91);
        startFrameCounter();
        // Main loop for emulation
        long prevTime = System.nanoTime();
        double leftOvers = 0;
        // DebugLogger.logFile(cpu.toString(), DebugLogger.LOG_FILE);
        while (running) {
            try {
                long rn = System.nanoTime();
                double delta = rn - prevTime + leftOvers;
                int ticks = (int) (delta / Specifics.US_PER_CYCLE);
                leftOvers = delta - (ticks * Specifics.US_PER_CYCLE);
                prevTime = rn;
                while (ticks > 0) {
                    // InstructionTick now returns T-Cycles (4.19MHz units)
                    // This matches the 'ticks' calculated from US_PER_CYCLE
                    ticks -= InstructionTick(false);

                    // Frame counting logic inside the loop
                    int newLY = memory.read(DuckMemory.LY);
                    if (newLY < prevLY)
                        countFrame();
                    prevLY = newLY;
                }
                if (paused) {
                    while (paused)
                        Thread.sleep(100);
                    startFrameCounter();
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

    /**
     * Executes one CPU instruction (or HALT state) and steps the hardware
     * components
     * proportionally.
     * * @param skipStops If true, ignore STOP state logic.
     * 
     * @return The number of T-Cycles (dots) consumed.
     * @throws InterruptedException
     */
    private int InstructionTick(boolean skipStops) throws InterruptedException {
        boolean interruptPending = (memory.getIE() & memory.getIF() & 0x1F) != 0;

        // --- STOP handling ---
        if (cpu.isStopped() && !skipStops) {
            // STOP exits only via Joypad interrupt (bit 4)
            if ((memory.getIE() & memory.getIF() & 0x10) != 0) {
                cpu.setStopped(false);
            }
            // Even in STOP mode, some hardware might need ticking (though usually stopped)
            // Ideally, we just consume time to prevent tight loops in the main thread
            return 4;
        }

        int mCycles = 0;

        // --- HALT handling ---
        boolean lcdEnabled = (memory.read(DuckMemory.LCDC) & 0x80) != 0;

        if (cpu.isHalted()) {
            if (!lcdEnabled) {
                // LCD disabled -> HALT behaves like NOP
                cpu.setHalted(false);
                // 1 Machine Cycle
                mCycles = 1;
            } else if (interruptPending) {
                cpu.setHalted(false);
                if (!cpu.isInterruptMasterEnable()) {
                    cpu.setHaltBug(true);
                }
                // Wake up consumes 1 Machine Cycle
                mCycles = 1;
            } else {
                // True HALT waiting for interrupt
                // Consumes 1 Machine Cycle while waiting
                mCycles = 1;
            }
        } else {
            instruction = ReadNextInstruction();
            // cpu.execute returns M-Cycles (e.g., 1, 2, 3...)
            mCycles = cpu.execute(instruction);
        }

        // --- CRITICAL TIMING FIX ---
        // Convert Machine Cycles to Clock Cycles (T-Cycles)
        // 1 M-Cycle = 4 T-Cycles
        int tCycles = mCycles * 4;

        // Step hardware components for every T-Cycle
        for (int i = 0; i < tCycles; i++) {
            memory.tickDMA();
            timerSet.tick();
            ppu.step();
            handleSerial();
        }

        return tCycles;
    }

    private void handleSerial() {
        int serialControl = memory.read(DuckMemory.SERIAL_CONTROL);
        int serialData = memory.read(DuckMemory.SERIAL_DATA);

        if ((serialControl & 0x81) == 0x81) {

            DebugLogger.serialOutput(serialData);

            memory.write(DuckMemory.SERIAL_DATA, 0xFF);

            memory.write(DuckMemory.SERIAL_CONTROL, serialControl & ~0x80);

            // cpu.requestInterrupt(DuckCPU.Interrupt.SERIAL);
        }
    }

    /**
     * This method reads the next instruction from the ROM, incrementing the PC and
     * returning the instruction
     * as an array of integers
     * * @return The next instruction as an array of integers
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
        InstructionType type = DuckDecoder.getType(opcode, isCB);
        // Check for unknown opcode
        if (type == null) {
            // Optional: Handle unknown opcodes gracefully instead of crashing
            System.err.println("Unknown Opcode: " + Integer.toHexString(opcode));
            System.exit(1);
        }
        // Get operand count
        int operandCount = type.getOperandCount();

        // Read in next bytes
        int[] operands = {};
        if (operandCount > 0) {
            operands = memory.readBytes(pc, operandCount);
            pc += operandCount;
        }

        // Update PC
        if (!cpu.getHaltBug())
            cpu.setPC(pc);
        else
            cpu.setHaltBug(false);

        // Construct instruction
        return DuckDecoder.constructInstruction(type, opcode,
                operands);
    }

    private void countFrame() {
        frames++;
    }

    private void startFrameCounter() {
        // Reset frame counter
        frames = 0;
        // Start frame counter thread
        Thread frameCounterThread = new Thread(() -> {
            while (running && !paused) {
                try {
                    Thread.sleep(1000);
                    if (paused)
                        continue;
                    mainWindow.updateFrameCounter(frames);
                    mainWindow.subtitle(romName, "[" + frames + " FPS]");
                    frames = 0;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        frameCounterThread.start();
    }
}