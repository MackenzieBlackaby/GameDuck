package com.blackaby.Backend.Emulation.Memory;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.Misc.ROM;
import com.blackaby.Backend.Emulation.Peripherals.DuckTimer;

/**
 * Represents the Game Boy's memory system.
 * <p>
 * Provides read/write access to all memory regions, including handling for:
 * - Echo RAM (Mirrored WRAM)
 * - DMA transfers
 * - Special register behavior (DIV, TIMA, TAC)
 * </p>
 * This version is optimized for "ROM Only" games (e.g., Tetris) and does not
 * handle MBC Bank Switching.
 */
public class DuckMemory {

    private int[] ram;
    private int[] rom;
    private DuckTimer timerSet;
    private DuckCPU cpu;

    // DMA Handling
    private boolean dmaActive = false;
    private int dmaCounter = 0;
    private int dmaSource = 0;

    public DuckMemory() {
        this.ram = new int[DuckAddresses.MEMORY_SIZE];
        this.rom = new int[0];
    }

    public void setTimerSet(DuckTimer timerSet) {
        this.timerSet = timerSet;
    }

    public void setCPU(DuckCPU cpu) {
        this.cpu = cpu;
    }

    /**
     * Loads a ROM into the memory map (0x0000 - 0x7FFF).
     */
    public void loadROM(ROM rom) {
        this.rom = rom.getData();
        this.ram = new int[DuckAddresses.MEMORY_SIZE];

        // Safety check to ensure we don't overflow the memory map
        int lengthToCopy = Math.min(this.rom.length, DuckAddresses.MEMORY_SIZE);
        System.arraycopy(this.rom, 0, ram, 0, lengthToCopy);
    }

    /**
     * Reads a byte from memory.
     */
    public int read(int address) {
        address &= 0xFFFF; // Defensive masking

        // Note: We do not calculate DIV here on the fly anymore.
        // We rely on DuckTimer to push the value to ram[DIV] via setDividerFromTimer.

        // Handle Echo RAM (0xE000 - 0xFDFF) -> Mirrors WRAM (0xC000 - 0xDDFF)
        if (address >= DuckAddresses.ECHO_RAM_START && address <= DuckAddresses.ECHO_RAM_END) {
            int offset = address - DuckAddresses.ECHO_RAM_START;
            return ram[DuckAddresses.WORK_RAM_START + offset] & 0xFF;
        }

        // Handle Unusable Memory (0xFEA0 - 0xFEFF)
        if (address >= DuckAddresses.NOT_USABLE_START && address <= DuckAddresses.NOT_USABLE_END) {
            return 0xFF;
        }

        // Standard Read
        return ram[address] & 0xFF;
    }

    /**
     * Writes a byte to memory with hardware side-effects.
     */
    public void write(int address, int value) {
        address &= 0xFFFF;
        value &= 0xFF;

        // 1. ROM Area (0x0000 - 0x7FFF)
        // For Tetris (ROM ONLY), writes here are ignored.
        // No MBC banking logic implemented.
        if (address < 0x8000) {
            return;
        }

        // 2. Unusable Memory
        if (address >= DuckAddresses.NOT_USABLE_START && address <= DuckAddresses.NOT_USABLE_END) {
            return;
        }

        // 3. Echo RAM (Redirect to WRAM)
        if (address >= DuckAddresses.ECHO_RAM_START && address <= DuckAddresses.ECHO_RAM_END) {
            int offset = address - DuckAddresses.ECHO_RAM_START;
            ram[DuckAddresses.WORK_RAM_START + offset] = value;
            return;
        }

        // 4. Peripherals & Registers

        // DIV Register: Writing any value resets it to 0
        if (address == DuckAddresses.DIV) {
            if (timerSet != null)
                timerSet.resetDIV();
            return;
        }

        // TIMA Register: Handle overflow cancellation
        if (address == DuckAddresses.TIMA) {
            if (timerSet != null && timerSet.timaOverflowPending) {
                timerSet.cancelPendingOverflow();
            }
        }

        // TAC Register: Notify timer of configuration change to prevent glitches
        if (address == DuckAddresses.TAC) {
            // Write the value first so syncTimerBit reads the new state
            ram[address] = value;
            if (timerSet != null)
                timerSet.syncTimerBit();
            return;
        }

        // DMA Transfer Trigger
        if (address == DuckAddresses.DMA) {
            dmaSource = value << 8; // Value * 0x100
            dmaCounter = 0;
            dmaActive = true;
            ram[address] = value; // Technically valid to store the value
            return;
        }

        // Standard Write
        ram[address] = value;
    }

    /**
     * Emulates a single DMA transfer tick (should be called frequently by CPU).
     */
    public void tickDMA() {
        if (!dmaActive)
            return;

        // OAM is at 0xFE00. Transfer 1 byte per tick.
        int dest = (DuckAddresses.OAM_START + dmaCounter) & 0xFFFF;
        int src = (dmaSource + dmaCounter) & 0xFFFF;

        // Perform transfer
        int data = read(src);
        ram[dest] = data;

        dmaCounter++;

        // DMA lasts for 160 bytes (0xA0)
        if (dmaCounter >= 0xA0) {
            dmaActive = false;
            dmaCounter = 0;
        }
    }

    /**
     * Helper to read a sequence of bytes (useful for debugging/rendering).
     */
    public int[] readBytes(int start, int count) {
        int[] bytes = new int[count];
        for (int i = 0; i < count; i++) {
            bytes[i] = read(start + i);
        }
        return bytes;
    }

    public void stackPushByte(int val) {
        int sp = cpu.getSP();
        sp = (sp - 1) & 0xFFFF;
        write(sp, val & 0xFF);
        cpu.setSP(sp);
    }

    public void stackPushShort(int val) {
        int firstByte = (val >> 8) & 0xFF;
        int secondByte = val & 0xFF;
        stackPushByte(firstByte);
        stackPushByte(secondByte);
    }

    public int stackPopByte() {
        int sp = cpu.getSP();
        int val = read(sp);
        sp++;
        cpu.setSP(sp);
        return val;
    }

    public int stackPopShort() {
        int lsb = stackPopByte();
        int msb = stackPopByte();
        int constructedValue = (((msb << 8) & 0xFF00) | (lsb & 0xFF)) & 0xFFFF;
        return constructedValue;
    }

    // --- Peripheral Callbacks ---

    public void setDividerFromTimer(int value) {
        ram[DuckAddresses.DIV] = value & 0xFF;
    }

    public void setTIMAFromTimer(int value) {
        ram[DuckAddresses.TIMA] = value & 0xFF;
    }
}