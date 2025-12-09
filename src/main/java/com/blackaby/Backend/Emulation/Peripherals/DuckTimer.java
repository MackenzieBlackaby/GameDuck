package com.blackaby.Backend.Emulation.Peripherals;

import com.blackaby.Backend.Emulation.Memory.DuckMemory;
import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.Memory.DuckAddresses;

public class DuckTimer {

    // Constants for clarity
    private static final int TAC_ENABLE_BIT = 0x04;
    private static final int TAC_CLOCK_SELECT_MASK = 0x03;

    private int internalCounter = 0; // 16-bit internal counter
    private boolean previousTimerBit = false;

    // Overflow handling
    private int overflowCounter = 0;
    public boolean timaOverflowPending = false;

    private final DuckMemory memory;
    private final DuckCPU cpu;

    public DuckTimer(DuckCPU cpu, DuckMemory memory) {
        this.cpu = cpu;
        this.memory = memory;
    }

    public void tick() {
        // 1. Handle pending overflow delays (4 T-cycles)
        if (overflowCounter > 0) {
            overflowCounter--;
            if (overflowCounter == 0 && timaOverflowPending) {
                // Delay finished: Reload TMA and Request Interrupt
                memory.setTIMAFromTimer(memory.read(DuckAddresses.TMA));
                cpu.requestInterrupt(DuckCPU.Interrupt.TIMER);
                timaOverflowPending = false;
            }
        }

        // 2. Increment Internal Counter
        internalCounter = (internalCounter + 1) & 0xFFFF;

        // 3. Update DIV Register (Upper 8 bits of internal counter)
        // Bit 15 of internal = Bit 7 of DIV.
        memory.setDividerFromTimer((internalCounter >> 8) & 0xFF);

        // 4. Detect Falling Edge to increment TIMA
        updateTIMA();
    }

    private void updateTIMA() {
        int tac = memory.read(DuckAddresses.TAC);
        boolean timerEnabled = (tac & TAC_ENABLE_BIT) != 0;
        int monitoredBit = getMonitoredBit(tac);

        // Timer ticks on the FALLING EDGE of this specific bit
        boolean currentTimerBit = timerEnabled && ((internalCounter & (1 << monitoredBit)) != 0);

        if (previousTimerBit && !currentTimerBit) {
            incrementTIMA();
        }

        previousTimerBit = currentTimerBit;
    }

    /**
     * Shared logic for incrementing TIMA.
     * Handles the overflow check and the 4-cycle delay initialization.
     */
    private void incrementTIMA() {
        int tima = memory.read(DuckAddresses.TIMA) & 0xFF;

        if (tima == 0xFF) {
            // Overflow!
            memory.setTIMAFromTimer(0x00); // Wrap to 0 immediately
            timaOverflowPending = true; // Mark pending
            overflowCounter = 4; // Set delay (4 T-Cycles)
        } else {
            // Normal increment
            memory.setTIMAFromTimer((tima + 1) & 0xFF);
        }
    }

    /**
     * Called when writing to the DIV register (at address 0xFF04).
     * This resets the internal counter and can trigger the "DIV Glitch".
     */
    public void resetDIV() {
        int tac = memory.read(DuckAddresses.TAC);
        boolean timerEnabled = (tac & TAC_ENABLE_BIT) != 0;
        int monitoredBit = getMonitoredBit(tac);

        // Check if the monitored bit is currently High
        boolean wasOne = timerEnabled && ((internalCounter & (1 << monitoredBit)) != 0);

        // Reset Internal Counter
        internalCounter = 0;

        // Since internalCounter is now 0, the bit is now Low.
        // If it WAS High, we just created a Falling Edge -> Increment TIMA immediately.
        if (wasOne) {
            incrementTIMA();
        }

        // Update state to match new counter value
        previousTimerBit = false;

        // Update the Memory's view of DIV immediately
        memory.setDividerFromTimer(0);
    }

    public void syncTimerBit() {
        int tac = memory.read(DuckAddresses.TAC);
        boolean timerEnabled = (tac & TAC_ENABLE_BIT) != 0;
        int monitoredBit = getMonitoredBit(tac);
        previousTimerBit = timerEnabled && ((internalCounter & (1 << monitoredBit)) != 0);
    }

    public int getInternalCounter() {
        return internalCounter;
    }

    private int getMonitoredBit(int tac) {
        return switch (tac & TAC_CLOCK_SELECT_MASK) {
            case 0 -> 9; // 4096 Hz
            case 1 -> 3; // 262144 Hz
            case 2 -> 5; // 65536 Hz
            case 3 -> 7; // 16384 Hz
            default -> 9;
        };
    }

    public void cancelPendingOverflow() {
        timaOverflowPending = false;
        overflowCounter = 0;
    }
}