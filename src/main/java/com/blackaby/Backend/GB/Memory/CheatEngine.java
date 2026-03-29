package com.blackaby.Backend.GB.Memory;

import com.blackaby.Backend.Platform.EmulatorCheat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies compiled cheat definitions to memory reads and mutable memory writes.
 */
public final class CheatEngine {

    private volatile CompiledCheats compiledCheats = CompiledCheats.Empty();

    /**
     * Replaces the active cheat list.
     *
     * @param cheats current cheat definitions
     */
    public void SetCheats(List<EmulatorCheat> cheats) {
        compiledCheats = CompiledCheats.Compile(cheats);
    }

    /**
     * Applies read-time overrides for the requested address.
     *
     * @param address memory address
     * @param originalValue value returned by the raw memory map
     * @return possibly overridden byte value
     */
    public int ApplyReadOverride(int address, int originalValue) {
        return compiledCheats.ApplyReadOverride(address, originalValue);
    }

    /**
     * Returns whether any active cheats currently override memory reads.
     *
     * @return {@code true} when at least one enabled cheat is active
     */
    public boolean HasReadOverrides() {
        return compiledCheats.HasReadOverrides();
    }

    /**
     * Returns whether any active cheats need live memory writes.
     *
     * @return {@code true} when enabled cheats target mutable memory
     */
    public boolean HasLiveWriteCheats() {
        return compiledCheats.HasLiveWriteCheats();
    }

    /**
     * Writes active mutable-memory cheats into RAM or I/O registers.
     *
     * @param memory live memory bus
     */
    public void ApplyWriteCheats(DuckMemory memory) {
        compiledCheats.ApplyWriteCheats(memory);
    }

    private record CompiledCheats(Map<Integer, List<EmulatorCheat>> cheatsByAddress,
                                  List<EmulatorCheat> liveWriteCheats) {

        private static CompiledCheats Empty() {
            return new CompiledCheats(Map.of(), List.of());
        }

        private static CompiledCheats Compile(List<EmulatorCheat> cheats) {
            if (cheats == null || cheats.isEmpty()) {
                return Empty();
            }

            Map<Integer, List<EmulatorCheat>> cheatsByAddress = new LinkedHashMap<>();
            List<EmulatorCheat> writeCheats = new java.util.ArrayList<>();
            for (EmulatorCheat cheat : cheats) {
                if (cheat == null || !cheat.enabled()) {
                    continue;
                }

                int address = cheat.address() & 0xFFFF;
                cheatsByAddress.computeIfAbsent(address, ignored -> new java.util.ArrayList<>()).add(cheat);
                if (address >= 0x8000) {
                    writeCheats.add(cheat);
                }
            }

            Map<Integer, List<EmulatorCheat>> frozenMap = new LinkedHashMap<>();
            for (Map.Entry<Integer, List<EmulatorCheat>> entry : cheatsByAddress.entrySet()) {
                frozenMap.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return new CompiledCheats(Map.copyOf(frozenMap), List.copyOf(writeCheats));
        }

        private int ApplyReadOverride(int address, int originalValue) {
            List<EmulatorCheat> cheats = cheatsByAddress.get(address & 0xFFFF);
            if (cheats == null || cheats.isEmpty()) {
                return originalValue & 0xFF;
            }

            int resolvedValue = originalValue & 0xFF;
            for (EmulatorCheat cheat : cheats) {
                if (cheat.compareValue() == null || (originalValue & 0xFF) == (cheat.compareValue() & 0xFF)) {
                    resolvedValue = cheat.value() & 0xFF;
                }
            }
            return resolvedValue;
        }

        private boolean HasReadOverrides() {
            return !cheatsByAddress.isEmpty();
        }

        private boolean HasLiveWriteCheats() {
            return !liveWriteCheats.isEmpty();
        }

        private void ApplyWriteCheats(DuckMemory memory) {
            if (memory == null || liveWriteCheats.isEmpty()) {
                return;
            }

            for (EmulatorCheat cheat : liveWriteCheats) {
                int currentValue = memory.ReadWithoutCheats(cheat.address());
                if (cheat.compareValue() != null && currentValue != (cheat.compareValue() & 0xFF)) {
                    continue;
                }
                memory.Write(cheat.address(), cheat.value());
            }
        }
    }
}
