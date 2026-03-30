package com.blackaby.Backend.GB.Peripherals;

import com.blackaby.Backend.GB.CPU.GBProcessor;
import com.blackaby.Backend.GB.Memory.GBMemAddresses;
import com.blackaby.Backend.GB.Memory.GBMemory;

/**
 * Emulates the DMG divider and programmable timer unit.
 * <p>
 * The timer follows the Game Boy's falling-edge behaviour and preserves the
 * delayed TIMA overflow reload used by many test ROMs.
 */
public class GBTimerSet {

    public record TimerState(
            int internalCounter,
            boolean previousTimerBit,
            int overflowCounter,
            boolean timaOverflowPending) implements java.io.Serializable {
    }

    private static final int tacEnableBit = 0x04;
    private static final int tacClockSelectMask = 0x03;

    private int internalCounter;
    private boolean previousTimerBit;
    private int overflowCounter;

    public boolean timaOverflowPending;

    private final GBMemory memory;
    private final GBProcessor cpu;

    /**
     * Creates a timer bound to the active CPU and memory bus.
     *
     * @param cpu    CPU instance for interrupt requests
     * @param memory memory bus for register mirroring
     */
    public GBTimerSet(GBProcessor cpu, GBMemory memory) {
        this.cpu = cpu;
        this.memory = memory;
    }

    /**
     * Seeds the DMG post-boot divider phase used when the boot ROM is skipped.
     */
    public void InitialiseDmgBootState() {
        internalCounter = 0xABCC;
        memory.SetDividerFromTimer((internalCounter >> 8) & 0xFF);
        SyncTimerBit();
    }

    /**
     * Advances the timer by one T-cycle.
     */
    public void Tick() {
        if (overflowCounter > 0) {
            overflowCounter--;
            if (overflowCounter == 0 && timaOverflowPending) {
                memory.SetTimaFromTimer(memory.ReadRegisterDirect(GBMemAddresses.TMA));
                cpu.RequestInterrupt(GBProcessor.Interrupt.TIMER);
                timaOverflowPending = false;
            }
        }

        internalCounter = (internalCounter + 1) & 0xFFFF;
        memory.SetDividerFromTimer((internalCounter >> 8) & 0xFF);
        UpdateTima();
    }

    /**
     * Resets the divider and applies the usual divider glitch behaviour.
     */
    public void ResetDiv() {
        int tac = memory.ReadRegisterDirect(GBMemAddresses.TAC);
        boolean timerEnabled = (tac & tacEnableBit) != 0;
        int monitoredBit = GetMonitoredBit(tac);
        boolean wasOne = timerEnabled && ((internalCounter & (1 << monitoredBit)) != 0);

        internalCounter = 0;
        if (wasOne) {
            IncrementTima();
        }

        previousTimerBit = false;
        memory.SetDividerFromTimer(0);
    }

    /**
     * Recomputes the sampled timer bit after external state changes.
     */
    public void SyncTimerBit() {
        int tac = memory.ReadRegisterDirect(GBMemAddresses.TAC);
        boolean timerEnabled = (tac & tacEnableBit) != 0;
        int monitoredBit = GetMonitoredBit(tac);
        previousTimerBit = timerEnabled && ((internalCounter & (1 << monitoredBit)) != 0);
    }

    /**
     * Writes TAC and applies the edge-triggered glitch when the selected timer
     * bit falls from high to low.
     *
     * @param value new TAC value
     */
    public void WriteTac(int value) {
        int oldTac = memory.ReadRegisterDirect(GBMemAddresses.TAC);
        boolean oldTimerBit = GetTimerBit(oldTac);

        memory.WriteDirect(GBMemAddresses.TAC, 0xF8 | (value & 0x07));

        boolean newTimerBit = GetTimerBit(value);
        if (oldTimerBit && !newTimerBit) {
            IncrementTima();
        }

        previousTimerBit = newTimerBit;
    }

    /**
     * Returns the internal 16-bit divider counter.
     *
     * @return raw internal counter
     */
    public int GetInternalCounter() {
        return internalCounter;
    }

    /**
     * Cancels a pending delayed TIMA reload.
     */
    public void CancelPendingOverflow() {
        timaOverflowPending = false;
        overflowCounter = 0;
    }

    /**
     * Captures the internal divider and overflow timing state.
     *
     * @return timer state snapshot
     */
    public TimerState CaptureState() {
        return new TimerState(internalCounter, previousTimerBit, overflowCounter, timaOverflowPending);
    }

    /**
     * Restores the internal divider and overflow timing state.
     *
     * @param state timer snapshot to restore
     */
    public void RestoreState(TimerState state) {
        if (state == null) {
            throw new IllegalArgumentException("A timer quick state is required.");
        }

        internalCounter = state.internalCounter() & 0xFFFF;
        previousTimerBit = state.previousTimerBit();
        overflowCounter = Math.max(0, state.overflowCounter());
        timaOverflowPending = state.timaOverflowPending();
    }

    private void UpdateTima() {
        int tac = memory.ReadRegisterDirect(GBMemAddresses.TAC);
        boolean timerEnabled = (tac & tacEnableBit) != 0;
        int monitoredBit = GetMonitoredBit(tac);
        boolean currentTimerBit = timerEnabled && ((internalCounter & (1 << monitoredBit)) != 0);

        if (previousTimerBit && !currentTimerBit) {
            IncrementTima();
        }

        previousTimerBit = currentTimerBit;
    }

    private void IncrementTima() {
        int tima = memory.ReadRegisterDirect(GBMemAddresses.TIMA);
        if (tima == 0xFF) {
            memory.SetTimaFromTimer(0x00);
            timaOverflowPending = true;
            overflowCounter = 4;
        } else {
            memory.SetTimaFromTimer((tima + 1) & 0xFF);
        }
    }

    private int GetMonitoredBit(int tac) {
        return switch (tac & tacClockSelectMask) {
            case 0 -> 9;
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 7;
            default -> 9;
        };
    }

    private boolean GetTimerBit(int tac) {
        boolean timerEnabled = (tac & tacEnableBit) != 0;
        int monitoredBit = GetMonitoredBit(tac);
        return timerEnabled && ((internalCounter & (1 << monitoredBit)) != 0);
    }

}
