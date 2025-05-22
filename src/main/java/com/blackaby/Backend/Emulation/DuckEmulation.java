package com.blackaby.Backend.Emulation;

import com.blackaby.Frontend.DebugLogger;
import com.blackaby.Backend.Emulation.CPU.*;
import com.blackaby.Backend.Emulation.Misc.ROM;
import com.blackaby.Backend.Emulation.Misc.Specifics;
import com.blackaby.Backend.Emulation.Peripherals.DuckTimer;
import com.blackaby.Frontend.DuckDisplay;
import com.blackaby.Frontend.MainWindow;
import com.blackaby.Backend.Emulation.CPU.DuckDecoder.InstructionType;
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
    private MainWindow mainWindow;

    // Threading Variables
    private Thread emulationThread;
    private int clockCounter = 0;
    private volatile boolean running = false;
    private volatile boolean paused = false;
    private Instruction instruction = null;
    private int frames = 0;
    private int prevLY = 0;
    private String romName = null;

    /**
     * This constructor creates a new DuckEmulation
     * 
     * @param display The display to be used in the emulation
     */
    public DuckEmulation(MainWindow window, DuckDisplay display) {
        this.display = display;
        this.mainWindow = window;
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
        startFrameCounter();
        // Main loop for emulation
        long prevTime = System.nanoTime();
        double leftOvers = 0;
        //DebugLogger.logFile(cpu.toString(), DebugLogger.LOG_FILE);
        while (running) {
            try {
                long rn = System.nanoTime();
                double delta = rn - prevTime + leftOvers;
                int ticks = (int) (delta / Specifics.US_PER_CYCLE);
                leftOvers = delta - (ticks * Specifics.US_PER_CYCLE);
                prevTime = rn;
                while (ticks > 0) {
                    ticks -= InstructionTick(false);
                    int newLY = memory.read(DuckMemory.LY);
                    // Check if the LY register has changed
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

    private int InstructionTick(boolean skipStops) throws InterruptedException {
        // check if an interrupt is pending
        boolean interruptPending = (memory.getIE() & memory.getIF() & 0x1F) != 0;
        if (cpu.isStopped() && !skipStops) {
            // STOP only exits via Joypad interrupt
            if ((memory.getIE() & memory.getIF() & 0x10) != 0) {
                cpu.setStopped(false);
            }
            return 0;
        } else if (cpu.isHalted()) {
            if (interruptPending) {
                cpu.setHalted(false);
                // CPU resumes; if IME is enabled, interrupt will be serviced next cycle
            }
            clockCounter = 1;
        } else {
            instruction = ReadNextInstruction();
            clockCounter = 4 * instruction.getCycleCount();
            cpu.execute(instruction);
        }
        int prevClockCounter = clockCounter;
        // Always tick hardware normally
        for (; clockCounter > 0; clockCounter--) {
            timerSet.tick();
            ppu.step();
            handleSerial();
            memory.tickDMA();
        }
        return prevClockCounter;
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
        InstructionType type = DuckDecoder.getType(opcode, isCB);
        // Check for unknown opcode
        if (type == null) {
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
