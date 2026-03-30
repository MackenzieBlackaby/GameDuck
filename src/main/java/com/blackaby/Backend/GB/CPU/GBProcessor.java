package com.blackaby.Backend.GB.CPU;

import com.blackaby.Backend.GB.GBRuntime;
import com.blackaby.Backend.GB.Memory.GBMemAddresses;
import com.blackaby.Backend.GB.Memory.GBMemory;
import com.blackaby.Backend.GB.Misc.GBRom;

/**
 * Emulates the Game Boy CPU core and its register state.
 * <p>
 * The class owns the LR35902 register file, interrupt state, HALT behaviour,
 * and the fetch-decode-execute flow used by the main emulation loop.
 */
public class GBProcessor {

    public record CpuState(
            int pc,
            int sp,
            int a,
            int f,
            int b,
            int c,
            int d,
            int e,
            int h,
            int l,
            int instructionRegister,
            boolean interruptMasterEnable,
            int imeDelayCounter,
            boolean halted,
            boolean stopped,
            boolean haltBug) implements java.io.Serializable {
    }

    /**
     * Identifies 8-bit and 16-bit CPU registers used by the decoder and
     * instruction helpers.
     */
    public enum Register {
        B, C, D, E, H, L, HL_ADDR, A,
        F, IR, IE,
        BC, DE, HL, AF, SP, PC;

        /**
         * Decodes the three-bit register field used by many byte instructions.
         *
         * @param bitId encoded register field
         * @return decoded register
         */
        public static Register GetRegFrom3Bit(int bitId) {
            return switch (bitId & 0b111) {
                case 0 -> B;
                case 1 -> C;
                case 2 -> D;
                case 3 -> E;
                case 4 -> H;
                case 5 -> L;
                case 6 -> HL_ADDR;
                case 7 -> A;
                default -> throw new IllegalArgumentException("Invalid 3-bit register field");
            };
        }

        /**
         * Decodes the two-bit register-pair field used by 16-bit instructions.
         *
         * @param bitId       encoded register field
         * @param isAfContext whether field `11` should resolve to `AF` instead of
         *                    `SP`
         * @return decoded register pair
         */
        public static Register GetRegFrom2Bit(int bitId, boolean isAfContext) {
            return switch (bitId & 0b11) {
                case 0 -> BC;
                case 1 -> DE;
                case 2 -> HL;
                case 3 -> isAfContext ? AF : SP;
                default -> throw new IllegalArgumentException("Invalid 2-bit register field");
            };
        }

    }

    /**
     * CPU status flags stored in the upper nibble of register `F`.
     */
    public enum Flag {
        Z(7), N(6), H(5), C(4);

        private final int bit;

        Flag(int bit) {
            this.bit = bit;
        }

        /**
         * Returns the bit position used for this flag in register `F`.
         *
         * @return flag bit index
         */
        public int GetBit() {
            return bit;
        }

    }

    /**
     * Interrupt sources exposed through the IF and IE registers.
     */
    public enum Interrupt {
        VBLANK(0x01, 0x40),
        LCD_STAT(0x02, 0x48),
        TIMER(0x04, 0x50),
        SERIAL(0x08, 0x58),
        JOYPAD(0x10, 0x60);

        private final int mask;
        private final int address;

        Interrupt(int mask, int address) {
            this.mask = mask;
            this.address = address;
        }

        /**
         * Returns the IF and IE bit mask for this interrupt source.
         *
         * @return interrupt bit mask
         */
        public int GetMask() {
            return mask;
        }

        /**
         * Returns the interrupt vector address.
         *
         * @return vector address
         */
        public int GetAddress() {
            return address;
        }

        /**
         * Resolves an interrupt from priority order index.
         *
         * @param index priority index from 0 to 4
         * @return interrupt entry
         */
        public static Interrupt GetInterrupt(int index) {
            return switch (index) {
                case 0 -> VBLANK;
                case 1 -> LCD_STAT;
                case 2 -> TIMER;
                case 3 -> SERIAL;
                case 4 -> JOYPAD;
                default -> throw new IllegalArgumentException("Invalid interrupt index");
            };
        }
    }

    private int pc;
    private int sp;

    private int a;
    private int f;
    private int b;
    private int c;
    private int d;
    private int e;
    private int h;
    private int l;

    private int instructionRegister;
    private GBOpcodeHandler currentInstruction;
    private boolean interruptMasterEnable;
    private int imeDelayCounter;
    private boolean halted;
    private boolean stopped;
    private boolean haltBug;

    public final GBMemory memory;
    public final GBRuntime emulation;
    public final GBRom rom;

    private final GBDecoder decoder;

    /**
     * Creates a CPU bound to the active memory bus, emulator controller, and
     * cartridge metadata.
     *
     * @param memory    memory bus
     * @param emulation owning emulator controller
     * @param rom       loaded cartridge
     */
    public GBProcessor(GBMemory memory, GBRuntime emulation, GBRom rom) {
        this.memory = memory;
        this.emulation = emulation;
        this.rom = rom;
        decoder = new GBDecoder(this, memory);
    }

    /**
     * Fetches the next opcode byte from memory.
     */
    public void Fetch() {
        instructionRegister = memory.Read(pc);
        if (haltBug) {
            haltBug = false;
            return;
        }

        pc = (pc + 1) & 0xFFFF;
    }

    /**
     * Decodes the currently fetched opcode into an executable handler.
     */
    public void Decode() {
        currentInstruction = decoder.DecodeInstruction(instructionRegister, false);
    }

    /**
     * Executes the current instruction and then services delayed IME and
     * interrupt entry logic.
     *
     * @return T-cycles consumed by the instruction, plus interrupt entry if one
     *         is taken
     */
    public int Execute() {
        int cycles = halted ? 4 : ExecuteLoadedInstruction();

        if (imeDelayCounter > 0) {
            imeDelayCounter--;
            if (imeDelayCounter == 0) {
                interruptMasterEnable = true;
            }
        }

        if (HandleInterrupts()) {
            cycles += 20;
        }

        return cycles;
    }

    /**
     * Writes an 8-bit value to a register or the byte addressed by `HL`.
     *
     * @param register target register
     * @param value    value to store
     */
    public void SetRegister(Register register, int value) {
        value &= 0xFF;
        switch (register) {
            case A -> a = value;
            case F -> f = value & 0xF0;
            case B -> b = value;
            case C -> c = value;
            case D -> d = value;
            case E -> e = value;
            case H -> h = value;
            case L -> l = value;
            case IR -> instructionRegister = value;
            case HL_ADDR -> memory.Write(GetHL(), value);
            default -> throw new IllegalArgumentException("Unknown 8-bit register: " + register);
        }
    }

    /**
     * Reads an 8-bit value from a register or the byte addressed by `HL`.
     *
     * @param register source register
     * @return register value
     */
    public int GetRegister(Register register) {
        return switch (register) {
            case A -> a;
            case F -> f;
            case B -> b;
            case C -> c;
            case D -> d;
            case E -> e;
            case H -> h;
            case L -> l;
            case IR -> instructionRegister;
            case HL_ADDR -> memory.Read(GetHL());
            default -> throw new IllegalArgumentException("Unknown 8-bit register: " + register);
        };
    }

    /**
     * Writes a 16-bit value to a register pair.
     *
     * @param register target pair
     * @param value    value to store
     */
    public void SetRegisterPair(Register register, int value) {
        value &= 0xFFFF;
        switch (register) {
            case PC -> pc = value;
            case SP -> sp = value;
            case BC -> {
                b = (value >> 8) & 0xFF;
                c = value & 0xFF;
            }
            case DE -> {
                d = (value >> 8) & 0xFF;
                e = value & 0xFF;
            }
            case HL -> {
                h = (value >> 8) & 0xFF;
                l = value & 0xFF;
            }
            case AF -> {
                a = (value >> 8) & 0xFF;
                f = value & 0xF0;
            }
            default -> throw new IllegalArgumentException("Invalid 16-bit register: " + register);
        }
    }

    /**
     * Reads a 16-bit value from a register pair.
     *
     * @param register source pair
     * @return register-pair value
     */
    public int GetRegisterPair(Register register) {
        return switch (register) {
            case PC -> pc;
            case SP -> sp;
            case BC -> (b << 8) | c;
            case DE -> (d << 8) | e;
            case HL -> (h << 8) | l;
            case AF -> (a << 8) | f;
            default -> throw new IllegalArgumentException("Invalid 16-bit register: " + register);
        };
    }

    /**
     * Returns register pair `HL`.
     *
     * @return current `HL` value
     */
    public int GetHL() {
        return (h << 8) | l;
    }

    /**
     * Writes register pair `HL`.
     *
     * @param value new `HL` value
     */
    public void SetHL(int value) {
        h = (value >> 8) & 0xFF;
        l = value & 0xFF;
    }

    /**
     * Returns register pair `BC`.
     *
     * @return current `BC` value
     */
    public int GetBC() {
        return (b << 8) | c;
    }

    /**
     * Writes register pair `BC`.
     *
     * @param value new `BC` value
     */
    public void SetBC(int value) {
        b = (value >> 8) & 0xFF;
        c = value & 0xFF;
    }

    /**
     * Returns register pair `DE`.
     *
     * @return current `DE` value
     */
    public int GetDE() {
        return (d << 8) | e;
    }

    /**
     * Writes register pair `DE`.
     *
     * @param value new `DE` value
     */
    public void SetDE(int value) {
        d = (value >> 8) & 0xFF;
        e = value & 0xFF;
    }

    /**
     * Returns register pair `AF`.
     *
     * @return current `AF` value
     */
    public int GetAF() {
        return (a << 8) | f;
    }

    /**
     * Writes register pair `AF`.
     *
     * @param value new `AF` value
     */
    public void SetAF(int value) {
        a = (value >> 8) & 0xFF;
        f = value & 0xF0;
    }

    /**
     * Returns the program counter.
     *
     * @return program counter
     */
    public int GetPC() {
        return pc;
    }

    /**
     * Writes the program counter.
     *
     * @param value new PC value
     */
    public void SetPC(int value) {
        pc = value & 0xFFFF;
    }

    /**
     * Returns the stack pointer.
     *
     * @return stack pointer
     */
    public int GetSP() {
        return sp;
    }

    /**
     * Writes the stack pointer.
     *
     * @param value new SP value
     */
    public void SetSP(int value) {
        sp = value & 0xFFFF;
    }

    /**
     * Returns register `A`.
     *
     * @return accumulator value
     */
    public int GetAccumulator() {
        return a;
    }

    /**
     * Writes register `A`.
     *
     * @param value new accumulator value
     */
    public void SetAccumulator(int value) {
        a = value & 0xFF;
    }

    /**
     * Returns register `C`.
     *
     * @return register `C`
     */
    public int GetC() {
        return c;
    }

    /**
     * Writes register `C`.
     *
     * @param value new register value
     */
    public void SetC(int value) {
        c = value & 0xFF;
    }

    /**
     * Returns the most recently fetched opcode.
     *
     * @return instruction register contents
     */
    public int GetInstructionRegister() {
        return instructionRegister;
    }

    /**
     * Sets or clears a status flag.
     *
     * @param flag  flag to update
     * @param value new flag state
     */
    public void SetFlag(Flag flag, boolean value) {
        if (value) {
            f |= (1 << flag.GetBit());
        } else {
            f &= ~(1 << flag.GetBit());
        }
        f &= 0xF0;
    }

    /**
     * Returns the state of a status flag.
     *
     * @param flag flag to read
     * @return `true` if the flag is set
     */
    public boolean GetFlag(Flag flag) {
        return (f & (1 << flag.GetBit())) != 0;
    }

    /**
     * Clears all status flags.
     */
    public void ClearFlags() {
        f = 0;
    }

    /**
     * Sets the CPU HALT state.
     *
     * @param halted whether the CPU is halted
     */
    public void SetHalted(boolean halted) {
        this.halted = halted;
    }

    /**
     * Returns whether the CPU is halted.
     *
     * @return `true` if HALT is active
     */
    public boolean IsHalted() {
        return halted;
    }

    /**
     * Sets the CPU STOP state.
     *
     * @param stopped whether the CPU is stopped
     */
    public void SetStopped(boolean stopped) {
        this.stopped = stopped;
    }

    /**
     * Returns whether the CPU is stopped.
     *
     * @return `true` if STOP is active
     */
    public boolean IsStopped() {
        return stopped;
    }

    /**
     * Arms the HALT bug for the next fetch.
     */
    public void SetHaltBug() {
        haltBug = true;
    }

    /**
     * Returns whether the HALT bug is pending.
     *
     * @return `true` if the next fetch should skip the PC increment
     */
    public boolean IsHaltBug() {
        return haltBug;
    }

    /**
     * Schedules IME to be enabled after the next instruction completes.
     */
    public void ScheduleEnableInterrupts() {
        if (!interruptMasterEnable && imeDelayCounter == 0) {
            imeDelayCounter = 2;
        }
    }

    /**
     * Disables interrupts immediately.
     */
    public void DisableInterrupts() {
        interruptMasterEnable = false;
        imeDelayCounter = 0;
    }

    /**
     * Enables interrupts immediately.
     */
    public void EnableInterruptsImmediately() {
        interruptMasterEnable = true;
        imeDelayCounter = 0;
    }

    /**
     * Sets the IF bit for an interrupt source.
     *
     * @param interrupt interrupt to request
     */
    public void RequestInterrupt(Interrupt interrupt) {
        int ifRegister = memory.Read(GBMemAddresses.INTERRUPT_FLAG);
        memory.Write(GBMemAddresses.INTERRUPT_FLAG, ifRegister | interrupt.GetMask());
    }

    /**
     * Returns the current IME state.
     *
     * @return `true` if interrupts are globally enabled
     */
    public boolean IsInterruptMasterEnable() {
        return interruptMasterEnable;
    }

    /**
     * Captures the live CPU register and interrupt state.
     *
     * @return CPU state snapshot
     */
    public CpuState CaptureState() {
        return new CpuState(
                pc,
                sp,
                a,
                f,
                b,
                c,
                d,
                e,
                h,
                l,
                instructionRegister,
                interruptMasterEnable,
                imeDelayCounter,
                halted,
                stopped,
                haltBug);
    }

    /**
     * Restores the CPU register and interrupt state from a snapshot.
     *
     * @param state CPU snapshot to restore
     */
    public void RestoreState(CpuState state) {
        if (state == null) {
            throw new IllegalArgumentException("A CPU quick state is required.");
        }

        pc = state.pc() & 0xFFFF;
        sp = state.sp() & 0xFFFF;
        a = state.a() & 0xFF;
        f = state.f() & 0xF0;
        b = state.b() & 0xFF;
        c = state.c() & 0xFF;
        d = state.d() & 0xFF;
        e = state.e() & 0xFF;
        h = state.h() & 0xFF;
        l = state.l() & 0xFF;
        instructionRegister = state.instructionRegister() & 0xFF;
        interruptMasterEnable = state.interruptMasterEnable();
        imeDelayCounter = Math.max(0, state.imeDelayCounter());
        halted = state.halted();
        stopped = state.stopped();
        haltBug = state.haltBug();
        currentInstruction = null;
    }

    private int ExecuteLoadedInstruction() {
        return currentInstruction.Execute();
    }

    private boolean HandleInterrupts() {
        int ieRegister = memory.Read(GBMemAddresses.IE);
        int ifRegister = memory.Read(GBMemAddresses.INTERRUPT_FLAG);
        int pending = ieRegister & ifRegister & 0x1F;

        if (pending == 0) {
            return false;
        }

        if (halted) {
            halted = false;
        }

        if (!interruptMasterEnable) {
            return false;
        }

        interruptMasterEnable = false;
        Interrupt interrupt = Interrupt.GetInterrupt(Integer.numberOfTrailingZeros(pending));
        memory.Write(GBMemAddresses.INTERRUPT_FLAG, ifRegister & ~interrupt.GetMask());
        PushStack16(pc);
        pc = interrupt.GetAddress();
        return true;
    }

    private void PushStack16(int value) {
        sp = (sp - 1) & 0xFFFF;
        memory.Write(sp, (value >> 8) & 0xFF);
        sp = (sp - 1) & 0xFFFF;
        memory.Write(sp, value & 0xFF);
    }

    @Override
    public String toString() {
        return String.format(
                "A:%02X F:%02X B:%02X C:%02X D:%02X E:%02X H:%02X L:%02X SP:%04X PC:%04X",
                a, f, b, c, d, e, h, l, sp, pc);
    }
}
