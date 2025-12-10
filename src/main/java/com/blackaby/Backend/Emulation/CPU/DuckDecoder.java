package com.blackaby.Backend.Emulation.CPU;

import com.blackaby.Backend.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Register;
import com.blackaby.Backend.Emulation.CPU.InstructionLogic.ArithmeticType;
import com.blackaby.Backend.Emulation.CPU.InstructionLogic.BitOpType;
import com.blackaby.Backend.Emulation.CPU.InstructionLogic.BitwiseType;
import com.blackaby.Backend.Emulation.CPU.InstructionLogic.RotateType;
import com.blackaby.Backend.Emulation.CPU.InstructionLogic.ShiftType;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;

public class DuckDecoder {

    private final DuckCPU cpu;
    private final DuckMemory memory;
    public final OpcodeHandler[] opcodeTable = new OpcodeHandler[256];
    public final OpcodeHandler[] cbOpcodeTable = new OpcodeHandler[256];

    // Helper array to map indices 0-7 to Registers for automated table population
    // 0=B, 1=C, 2=D, 3=E, 4=H, 5=L, 6=(HL), 7=A
    private final Register[] rMap = {
            Register.B, Register.C, Register.D, Register.E,
            Register.H, Register.L, Register.HL_ADDR, Register.A
    };

    private final Register[] rpMap = { Register.BC, Register.DE, Register.HL, Register.SP };
    private final Register[] rpMap2 = { Register.BC, Register.DE, Register.HL, Register.AF }; // For PUSH/POP

    public DuckDecoder(DuckCPU cpu, DuckMemory memory) {
        this.cpu = cpu;
        this.memory = memory;

        // Default to unimplemented to prevent crashes on invalid opcodes
        for (int i = 0; i < 256; i++) {
            final int code = i;
            opcodeTable[i] = () -> {
                System.err.println("Unimplemented instruction: 0x" + Integer.toHexString(code));
                return 0;
            };
            cbOpcodeTable[i] = opcodeTable[i];
        }

        InitialiseOpcodes();
        InitialiseCBOpcodes();
    }

    // ==========================================
    // DATA FETCH HELPERS
    // ==========================================

    /**
     * Reads the byte at the current PC and increments PC.
     * Used for immediate operands (d8).
     */
    private int fetchByte() {
        int pc = cpu.getPC();
        int value = memory.read(pc);
        cpu.setPC((pc + 1) & 0xFFFF);
        return value;
    }

    /**
     * Reads two bytes at the current PC (Little Endian) and increments PC by 2.
     * Used for 16-bit immediate operands (d16, a16).
     */
    private int fetchWord() {
        int low = fetchByte();
        int high = fetchByte();
        return (high << 8) | low;
    }

    // ==========================================
    // TABLE INITIALIZATION
    // ==========================================

    private void InitialiseOpcodes() {
        // TODO: Implement all opcodes here

        // CB Prefix
        // Reads the next byte as the opcode for the CB table
        opcodeTable[0xCB] = () -> {
            int cbOpcode = fetchByte();
            return cbOpcodeTable[cbOpcode].execute();
        };
    }

    private void InitialiseCBOpcodes() {
        // Pattern: Operation (Rows) x Register (Cols)
        // Order of ops in CB space: RLC, RRC, RL, RR, SLA, SRA, SWAP, SRL

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int opcode = (row * 8) + col;
                Register reg = rMap[col];

                switch (row) {
                    case 0:
                        cbOpcodeTable[opcode] = () -> InstructionLogic.Rotate(RotateType.RLC, reg);
                        break;
                    case 1:
                        cbOpcodeTable[opcode] = () -> InstructionLogic.Rotate(RotateType.RRC, reg);
                        break;
                    case 2:
                        cbOpcodeTable[opcode] = () -> InstructionLogic.Rotate(RotateType.RL, reg);
                        break;
                    case 3:
                        cbOpcodeTable[opcode] = () -> InstructionLogic.Rotate(RotateType.RR, reg);
                        break;
                    case 4:
                        cbOpcodeTable[opcode] = () -> InstructionLogic.Shift(ShiftType.SLA, reg);
                        break;
                    case 5:
                        cbOpcodeTable[opcode] = () -> InstructionLogic.Shift(ShiftType.SRA, reg);
                        break;
                    case 6:
                        cbOpcodeTable[opcode] = () -> InstructionLogic.Swap(reg);
                        break;
                    case 7:
                        cbOpcodeTable[opcode] = () -> InstructionLogic.Shift(ShiftType.SRL, reg);
                        break;
                }
            }
        }

        // BIT operations [0x40 - 0x7F]
        for (int bit = 0; bit < 8; bit++) {
            for (int reg = 0; reg < 8; reg++) {
                int opcode = 0x40 + (bit * 8) + reg;
                Register r = rMap[reg];
                final int b = bit;
                if (r == Register.HL_ADDR)
                    cbOpcodeTable[opcode] = () -> InstructionLogic.BitOperationHL(BitOpType.BIT, b);
                else
                    cbOpcodeTable[opcode] = () -> InstructionLogic.BitOperation(BitOpType.BIT, b, r);
            }
        }

        // RES operations [0x80 - 0xBF]
        for (int bit = 0; bit < 8; bit++) {
            for (int reg = 0; reg < 8; reg++) {
                int opcode = 0x80 + (bit * 8) + reg;
                Register r = rMap[reg];
                final int b = bit;
                if (r == Register.HL_ADDR)
                    cbOpcodeTable[opcode] = () -> InstructionLogic.BitOperationHL(BitOpType.RES, b);
                else
                    cbOpcodeTable[opcode] = () -> InstructionLogic.BitOperation(BitOpType.RES, b, r);
            }
        }

        // SET operations [0xC0 - 0xFF]
        for (int bit = 0; bit < 8; bit++) {
            for (int reg = 0; reg < 8; reg++) {
                int opcode = 0xC0 + (bit * 8) + reg;
                Register r = rMap[reg];
                final int b = bit;
                if (r == Register.HL_ADDR)
                    cbOpcodeTable[opcode] = () -> InstructionLogic.BitOperationHL(BitOpType.SET, b);
                else
                    cbOpcodeTable[opcode] = () -> InstructionLogic.BitOperation(BitOpType.SET, b, r);
            }
        }
    }

    public OpcodeHandler DecodeInstruction(int opcode, boolean cb) {
        if (cb) {
            return cbOpcodeTable[opcode];
        } else {
            return opcodeTable[opcode];
        }
    }
}