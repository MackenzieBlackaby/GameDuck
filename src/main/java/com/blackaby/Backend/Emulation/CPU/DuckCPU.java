package com.blackaby.Backend.Emulation.CPU;

import com.blackaby.Backend.Emulation.Memory.DuckAddresses;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;
import com.blackaby.OldBackEnd.Emulation.DuckEmulation; // Ideally remove this dependency later if possible

/**
 * Represents the Central Processing Unit (Sharp LR35902) of the Game Boy.
 * <p>
 * Manages registers, flags, interrupt state machine, and instruction execution
 * cycle.
 * </p>
 */
public class DuckCPU {

    // =============================================================
    // ENUMS
    // =============================================================

    public enum Register {
        // 8-bit Registers
        B(0), C(1), D(2), E(3), H(4), L(5), HL_ADDR(6), A(7),

        // Special / Internal
        F(8), IR(9), IE(10),

        // 16-bit Registers
        BC(11), DE(12), HL(13), AF(14), SP(15), PC(16);

        private final int id;

        Register(int id) {
            this.id = id;
        }

        /** Helper to decode instructions (e.g., Load r, r'). */
        public static Register getRegFrom3Bit(int bitID) {
            return switch (bitID & 0b111) {
                case 0 -> B;
                case 1 -> C;
                case 2 -> D;
                case 3 -> E;
                case 4 -> H;
                case 5 -> L;
                case 6 -> HL_ADDR;
                case 7 -> A;
                default -> throw new IllegalArgumentException("Invalid 3-bit Register ID");
            };
        }

        /** Helper to decode instructions (e.g., PUSH rr). */
        public static Register getRegFrom2Bit(int bitID, boolean isAFContext) {
            return switch (bitID & 0b11) {
                case 0 -> BC;
                case 1 -> DE;
                case 2 -> HL;
                case 3 -> isAFContext ? AF : SP;
                default -> throw new IllegalArgumentException("Invalid 2-bit Register ID");
            };
        }
    }

    public enum Flag {
        Z(7), N(6), H(5), C(4);

        private final int bit;

        Flag(int bit) {
            this.bit = bit;
        }

        public int getBit() {
            return bit;
        }
    }

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

        public int getMask() {
            return mask;
        }

        public int getAddress() {
            return address;
        }

        public static Interrupt getInterrupt(int index) {
            return switch (index) {
                case 0 -> VBLANK;
                case 1 -> LCD_STAT;
                case 2 -> TIMER;
                case 3 -> SERIAL;
                case 4 -> JOYPAD;
                default -> throw new IllegalArgumentException("Invalid Interrupt Index");
            };
        }
    }

    // =============================================================
    // STATE
    // =============================================================

    // Registers (Stored as ints, but treated as unsigned bytes/shorts)
    private int pc; // Program Counter
    private int sp; // Stack Pointer

    // Main Registers
    private int a, f;
    private int b, c;
    private int d, e;
    private int h, l;

    // Internal State
    private int instructionRegister; // Used for decoding logic
    private boolean interruptMasterEnable = false; // IME
    private int imeDelayCounter = 0; // For EI instruction delay

    private boolean isHalted = false;
    private boolean isStopped = false;

    // References
    public final DuckMemory memory;
    public final DuckEmulation emulation;

    public DuckCPU(DuckMemory memory, DuckEmulation emulation) {
        this.memory = memory;
        this.emulation = emulation;
    }

    // =============================================================
    // EXECUTION CYCLE
    // =============================================================

    /**
     * Executes the provided instruction and handles interrupt states.
     * 
     * @param instruction The decoded instruction to run.
     * @return The total T-Cycles consumed (Instruction + potential Interrupts).
     */
    public int execute(Instruction instruction) {
        int cycles = 0;

        // 1. Execute Instruction (if not halted)
        if (instruction != null && !isHalted) {
            instruction.resetCycleCount();
            instruction.run();
            cycles += instruction.getCycleCount();
        } else if (isHalted) {
            // CPU effectively executes a NOP (4 cycles) while halted
            cycles += 4;
        }

        // 2. Handle IME Delay (The EI instruction effect happens after 1 instruction)
        if (imeDelayCounter > 0) {
            imeDelayCounter--;
            if (imeDelayCounter == 0) {
                interruptMasterEnable = true;
            }
        }

        // 3. Handle Interrupts
        // If an interrupt occurs, it consumes 20 T-Cycles (5 M-Cycles)
        if (handleInterrupts()) {
            cycles += 20;
        }

        return cycles;
    }

    private boolean handleInterrupts() {
        int ie = memory.read(DuckAddresses.IE);
        int ifReg = memory.read(DuckAddresses.INTERRUPT_FLAG);

        // Check pending interrupts that are also enabled
        int pending = ie & ifReg & 0x1F;

        if (pending == 0)
            return false;

        // HALT BUG / WAKE UP:
        // If CPU is Halted and an interrupt is pending (even if IME is off),
        // the CPU wakes up.
        if (isHalted) {
            isHalted = false;
        }

        // Interrupt Service Routine (ISR) logic only runs if IME is ON
        if (interruptMasterEnable) {
            interruptMasterEnable = false; // Disable nested interrupts immediately

            // Determine priority (Bit 0 -> Bit 4)
            // Integer.numberOfTrailingZeros is a fast way to find the lowest set bit
            int interruptIndex = Integer.numberOfTrailingZeros(pending);
            Interrupt intr = Interrupt.getInterrupt(interruptIndex);

            // 1. Clear the specific IF bit
            memory.write(DuckAddresses.INTERRUPT_FLAG, ifReg & ~intr.getMask());

            // 2. Push PC to Stack
            pushStack16(pc);

            // 3. Jump to Vector
            pc = intr.getAddress();

            return true; // Interrupt serviced
        }

        return false;
    }

    // =============================================================
    // REGISTER ACCESS
    // =============================================================

    public void regSet(Register reg, int value) {
        value &= 0xFF; // Sanitize input
        switch (reg) {
            case A -> a = value;
            case F -> f = value & 0xF0; // HARDWARE FIX: Low nibble of F is always 0
            case B -> b = value;
            case C -> c = value;
            case D -> d = value;
            case E -> e = value;
            case H -> h = value;
            case L -> l = value;
            case IR -> instructionRegister = value;
            case HL_ADDR -> memory.write(getHL(), value);
            default -> throw new IllegalArgumentException("Unknown 8-bit register: " + reg);
        }
    }

    public int regGet(Register reg) {
        return switch (reg) {
            case A -> a;
            case F -> f;
            case B -> b;
            case C -> c;
            case D -> d;
            case E -> e;
            case H -> h;
            case L -> l;
            case IR -> instructionRegister;
            case HL_ADDR -> memory.read(getHL());
            default -> throw new IllegalArgumentException("Unknown 8-bit register: " + reg);
        };
    }

    public void regSet16(Register reg, int value) {
        value &= 0xFFFF; // Sanitize
        switch (reg) {
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
            } // Low nibble fix
            default -> throw new IllegalArgumentException("Invalid 16-bit register: " + reg);
        }
    }

    public int regGet16(Register reg) {
        return switch (reg) {
            case PC -> pc;
            case SP -> sp;
            case BC -> (b << 8) | c;
            case DE -> (d << 8) | e;
            case HL -> (h << 8) | l;
            case AF -> (a << 8) | f;
            default -> throw new IllegalArgumentException("Invalid 16-bit register: " + reg);
        };
    }

    // --- Fast Access Helpers (Avoid Switch Overhead) ---

    public int getHL() {
        return (h << 8) | l;
    }

    public void setHL(int val) {
        h = (val >> 8) & 0xFF;
        l = val & 0xFF;
    }

    public int getBC() {
        return (b << 8) | c;
    }

    public void setBC(int val) {
        b = (val >> 8) & 0xFF;
        c = val & 0xFF;
    }

    public int getDE() {
        return (d << 8) | e;
    }

    public void setDE(int val) {
        d = (val >> 8) & 0xFF;
        e = val & 0xFF;
    }

    public int getAF() {
        return (a << 8) | f;
    }

    public void setAF(int val) {
        a = (val >> 8) & 0xFF;
        f = val & 0xF0;
    }

    public int getPC() {
        return pc;
    }

    public void setPC(int val) {
        pc = val & 0xFFFF;
    }

    public int getSP() {
        return sp;
    }

    public void setSP(int val) {
        sp = val & 0xFFFF;
    }

    // =============================================================
    // FLAGS & CONTROL
    // =============================================================

    public void setFlag(Flag flag, boolean value) {
        if (value) {
            f |= (1 << flag.getBit());
        } else {
            f &= ~(1 << flag.getBit());
        }
        f &= 0xF0; // Enforce hardware constraint
    }

    public boolean getFlag(Flag flag) {
        return (f & (1 << flag.getBit())) != 0;
    }

    public void clearFlags() {
        f = 0;
    }

    public void setHalted(boolean halted) {
        isHalted = halted;
    }

    public boolean isHalted() {
        return isHalted;
    }

    public void setStopped(boolean stopped) {
        isStopped = stopped;
    }

    /**
     * Schedules the IME to be enabled after the *next* instruction.
     * (Used by EI instruction).
     */
    public void scheduleEnableInterrupts() {
        imeDelayCounter = 2;
    }

    /**
     * Immediately disables interrupts (Used by DI instruction).
     */
    public void disableInterrupts() {
        interruptMasterEnable = false;
        imeDelayCounter = 0;
    }

    public void requestInterrupt(Interrupt interrupt) {
        int ifReg = memory.read(DuckAddresses.INTERRUPT_FLAG);
        memory.write(DuckAddresses.INTERRUPT_FLAG, ifReg | interrupt.getMask());
    }

    /**
     * Returns the current state of the Interrupt Master Enable (IME) flag.
     * Required by the HALT instruction to detect the Halt Bug.
     */
    public boolean isInterruptMasterEnable() {
        return interruptMasterEnable;
    }

    // =============================================================
    // STACK HELPERS
    // =============================================================

    private void pushStack16(int value) {
        // High byte first
        sp = (sp - 1) & 0xFFFF;
        memory.write(sp, (value >> 8) & 0xFF);
        // Low byte second
        sp = (sp - 1) & 0xFFFF;
        memory.write(sp, value & 0xFF);
    }

    // Note: popStack16 isn't strictly needed inside CPU class unless internal logic
    // uses it,
    // as instructions (RET, POP) usually handle it themselves via Memory,
    // but useful to have if you move RET logic here later.

    @Override
    public String toString() {
        return String.format("A:%02X F:%02X B:%02X C:%02X D:%02X E:%02X H:%02X L:%02X SP:%04X PC:%04X",
                a, f, b, c, d, e, h, l, sp, pc);
    }
}