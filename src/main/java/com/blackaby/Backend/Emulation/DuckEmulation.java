package com.blackaby.Backend.Emulation;

import com.blackaby.Backend.Emulation.Graphics.*;
import com.blackaby.Backend.Emulation.Memory.*;
import com.blackaby.Backend.Emulation.CPU.*;
import com.blackaby.Backend.Emulation.Misc.*;
import com.blackaby.Backend.Emulation.Peripherals.*;
import com.blackaby.Frontend.*;

/**
 * This class represents the emulation of the GameBoy
 * It has methods for starting the emulation and getting the CPU and display
 */
public class DuckEmulation implements Runnable {

    // Emulated Hardware Parts
    private DuckCPU cpu;
    private DuckMemory memory;
    private DuckTimer timer;
    private DuckPPU ppu;

    // Other hardware parts
    private DuckDisplay display;
    private ROM rom;
    private MainWindow mainWindow;

    // State variables
    private Thread emulationThread;
    private volatile boolean running = false;
    private volatile boolean paused = false;

    // Utility Variables
    private int frames = 0;
    private int prevLY = 0;
    private String defaultRomName = "NO ROM LOADED";
    private String romName = defaultRomName;

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
        // Make sure we're not already running
        if (running)
            stopEmulation();

        // Set state variables
        running = true;
        paused = false;

        // Initialise ROM
        rom = new ROM(romfile);
        romName = rom.getName();

        // Init hardware
        memory = new DuckMemory();
        cpu = new DuckCPU(memory, this, rom);
        timer = new DuckTimer(cpu, memory);
        ppu = new DuckPPU(cpu, memory, display);

        // Link hardware
        memory.setTimerSet(timer);
        memory.setCPU(cpu);
        memory.loadROM(rom);

        InstructionLogic.init(cpu, memory);

        // Other initial setup
        mainWindow.subtitle(romName, "[0 FPS]");

        // Start main thread
        emulationThread = new Thread(this);
        emulationThread.start();

    }

    /**
     * This method pauses or resumes the emulation
     */
    public void pauseEmulation() {
        paused = !paused;
        if (paused)
            mainWindow.subtitle(romName, ": Paused");
        else
            mainWindow.subtitle(romName, "[" + frames + " FPS]");
    }

    /**
     * This method stops the emulation
     */
    public void stopEmulation() {
        // Update states
        running = false;
        paused = false;
        // Wait for thread to finish
        try {
            if (emulationThread != null)
                emulationThread.join(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Clean UI window
        romName = defaultRomName;
        mainWindow.subtitle(romName);
        display.clear();

        // Clean up hardware
        cpu = null;
        memory = null;
        timer = null;
        ppu = null;
        rom = null;
    }

    /**
     * This method is the main loop of the emulation
     */
    public void run() {
        // BOOT SETUP
        cpu.setPC(0x0100);
        cpu.setAF(0x01B0);
        cpu.setBC(0x0013);
        cpu.setDE(0x00D8);
        cpu.setHL(0x014D);
        cpu.setSP(0xFFFE);

        memory.write(DuckAddresses.LCDC, 0x91);
        memory.write(DuckAddresses.BGP, 0xFC);
        memory.write(DuckAddresses.OBP0, 0xFF);
        memory.write(DuckAddresses.OBP1, 0xFF);

        // Frame counting setup
        startFrameCounter();

        // Timekeeping for smoothness
        long prevTime = System.nanoTime();
        double timeAccumulator = 0;

        // Main loop
        while (running) {
            // Pause logic
            if (paused) {
                try {
                    // Sleep the thread for a short time
                    Thread.sleep(100);
                    prevTime = System.nanoTime();
                } catch (InterruptedException e) {
                    // Close the thread if interrupted
                    Thread.currentThread().interrupt();
                }
                continue;
            }

            // Calculate time difference since last cycle
            long currentTime = System.nanoTime();
            double delta = currentTime - prevTime;
            prevTime = currentTime;
            timeAccumulator += delta;

            // CPU and hardware stepping according to time
            while (timeAccumulator >= Specifics.NS_PER_CYCLE) {
                // Fetch and decode if not halted
                if (!cpu.isHalted()) {
                    cpu.fetch();
                    cpu.decode();
                }

                // Execute instruction and get instructions
                int tCycles = cpu.execute();

                // Step other hardware
                for (int i = 0; i < tCycles; i++) {
                    timer.tick();
                    memory.tickDMA();
                    ppu.step();
                }

                // Update timing tracker
                timeAccumulator -= (tCycles * Specifics.NS_PER_CYCLE);

                // FPS tracking
                int currentLY = memory.read(DuckAddresses.LY);
                if (currentLY != prevLY)
                    frames++;
                prevLY = currentLY;
            }

        }

    }

    private void handleSerial() {
        int serialControl = memory.read(DuckAddresses.SERIAL_CONTROL);
        int serialData = memory.read(DuckAddresses.SERIAL_DATA);

        if ((serialControl & 0x81) == 0x81) {
            DebugLogger.serialOutput(serialData);
            memory.write(DuckAddresses.SERIAL_DATA, 0xFF);
            memory.write(DuckAddresses.SERIAL_CONTROL, serialControl & ~0x80);
            // cpu.requestInterrupt(DuckCPU.Interrupt.SERIAL);
        }
    }

    private void startFrameCounter() {
        Thread frameCounterThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(1000);
                    if (paused || !running)
                        continue;
                    mainWindow.updateFrameCounter(frames);
                    mainWindow.subtitle(romName, "[" + frames + " FPS]");
                    frames = 0;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        frameCounterThread.setDaemon(true);
        frameCounterThread.start();
    }
}