package com.blackaby.Backend.Emulation.Peripherals;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

public class DuckTimer {
    private int internalCounter = 0;
    private boolean previousTimerBit = false;
    private int overflowCounter = 0;

    private final DuckMemory memory;
    private final DuckCPU cpu;

    public DuckTimer(DuckCPU cpu, DuckMemory memory) {
        this.cpu = cpu;
        this.memory = memory;
    }

    public void tick() {
        if (overflowCounter == 1) {
            memory.write(DuckMemory.TIMA, memory.read(DuckMemory.TMA));
            cpu.requestInterrupt(DuckCPU.Interrupt.TIMER);
            overflowCounter = 0;
        }

        internalCounter = (internalCounter + 1) & 0xFFFF;
        memory.write(DuckMemory.DIV, (internalCounter >> 8) & 0xFF);

        updateTIMA();
    }

    private void updateTIMA() {
        int tac = memory.read(DuckMemory.TAC);
        boolean timerEnabled = (tac & 0x04) != 0;
        int monitoredBit = getMonitoredBit(tac);

        boolean currentTimerBit = timerEnabled && ((internalCounter & (1 << monitoredBit)) != 0);

        if (previousTimerBit && !currentTimerBit) {
            int tima = memory.read(DuckMemory.TIMA) & 0xFF;
            if (tima == 0xFF) {
                memory.write(DuckMemory.TIMA, 0x00);
                overflowCounter = 1;
            } else {
                memory.write(DuckMemory.TIMA, (tima + 1) & 0xFF);
            }
        }

        previousTimerBit = currentTimerBit;
    }

    public void resetDIV() {
        int tac = memory.read(DuckMemory.TAC);
        boolean timerEnabled = (tac & 0x04) != 0;
        int monitoredBit = getMonitoredBit(tac);

        boolean wasOne = timerEnabled && ((internalCounter & (1 << monitoredBit)) != 0);

        // Internal counter will be 0 after reset, so bit is guaranteed 0
        if (wasOne) {
            int tima = memory.read(DuckMemory.TIMA) & 0xFF;
            if (tima == 0xFF) {
                memory.write(DuckMemory.TIMA, 0x00);
                overflowCounter = 1;
            } else {
                memory.write(DuckMemory.TIMA, (tima + 1) & 0xFF);
            }
        }

        internalCounter = 0;
        previousTimerBit = false; // Timer bit is definitely 0 after reset
    }

    public void syncTimerBit() {
        int tac = memory.read(DuckMemory.TAC);
        boolean timerEnabled = (tac & 0x04) != 0;
        int monitoredBit = getMonitoredBit(tac);
        previousTimerBit = timerEnabled && ((internalCounter & (1 << monitoredBit)) != 0);
    }

    public int getInternalCounter() {
        return internalCounter;
    }

    private int getMonitoredBit(int tac) {
        return switch (tac & 0x03) {
            case 0 -> 9; // 4096 Hz
            case 1 -> 3; // 262144 Hz
            case 2 -> 5; // 65536 Hz
            case 3 -> 7; // 16384 Hz
            default -> 9;
        };
    }
}
