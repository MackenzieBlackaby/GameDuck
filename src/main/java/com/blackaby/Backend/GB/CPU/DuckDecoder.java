package com.blackaby.Backend.GB.CPU;

import com.blackaby.Backend.GB.CPU.DuckCPU.Flag;
import com.blackaby.Backend.GB.CPU.DuckCPU.Register;
import com.blackaby.Backend.GB.CPU.InstructionLogic.ArithmeticType;
import com.blackaby.Backend.GB.CPU.InstructionLogic.BitOpType;
import com.blackaby.Backend.GB.CPU.InstructionLogic.BitwiseType;
import com.blackaby.Backend.GB.CPU.InstructionLogic.RotateType;
import com.blackaby.Backend.GB.CPU.InstructionLogic.ShiftType;
import com.blackaby.Backend.GB.Memory.DuckMemory;

/**
 * Decodes opcode bytes into executable instruction handlers.
 * <p>
 * The decoder owns the primary and CB-prefixed lookup tables and provides the
 * immediate fetch helpers used while building those tables.
 */
public class DuckDecoder {

    private final DuckCPU cpu;
    private final DuckMemory memory;

    public final OpcodeHandler[] opcodeTable = new OpcodeHandler[256];
    public final OpcodeHandler[] cbOpcodeTable = new OpcodeHandler[256];

    private final Register[] registerMap = {
            Register.B, Register.C, Register.D, Register.E,
            Register.H, Register.L, Register.HL_ADDR, Register.A
    };

    /**
     * Creates the decoder and initialises its opcode tables.
     *
     * @param cpu active CPU instance
     * @param memory active memory bus
     */
    public DuckDecoder(DuckCPU cpu, DuckMemory memory) {
        this.cpu = cpu;
        this.memory = memory;

        for (int index = 0; index < 256; index++) {
            final int opcode = index;
            opcodeTable[index] = () -> {
                System.err.printf("Illegal or unimplemented instruction: 0x%02X%n", opcode);
                return 4;
            };
            cbOpcodeTable[index] = opcodeTable[index];
        }

        InitialiseOpcodes();
        InitialiseCbOpcodes();
    }

    private int FetchByte() {
        int pc = cpu.GetPC();
        int value = memory.Read(pc);
        cpu.SetPC((pc + 1) & 0xFFFF);
        return value;
    }

    private int FetchWord() {
        int low = FetchByte();
        int high = FetchByte();
        return (high << 8) | low;
    }

    private void InitialiseOpcodes() {
        opcodeTable[0x00] = InstructionLogic::Nop;
        opcodeTable[0x01] = () -> InstructionLogic.LoadRegisterPairFromImmediate(Register.BC, FetchWord());
        opcodeTable[0x02] = () -> InstructionLogic.AccumulatorToMemoryViaRegisterPair(Register.BC);
        opcodeTable[0x03] = () -> InstructionLogic.IncrementDecrementShort(Register.BC, true);
        opcodeTable[0x04] = () -> InstructionLogic.IncrementDecrementByteRegister(Register.B, true);
        opcodeTable[0x05] = () -> InstructionLogic.IncrementDecrementByteRegister(Register.B, false);
        opcodeTable[0x06] = () -> InstructionLogic.LoadRegisterFromImmediate(Register.B, FetchByte());
        opcodeTable[0x07] = () -> InstructionLogic.Rotate(RotateType.RLCA, Register.A);
        opcodeTable[0x08] = () -> InstructionLogic.StoreSPInImmediateAddress(FetchWord());
        opcodeTable[0x09] = () -> InstructionLogic.AddPairHL(Register.BC);
        opcodeTable[0x0A] = () -> InstructionLogic.LoadAccumulatorFromMemoryViaRegisterPair(Register.BC);
        opcodeTable[0x0B] = () -> InstructionLogic.IncrementDecrementShort(Register.BC, false);
        opcodeTable[0x0C] = () -> InstructionLogic.IncrementDecrementByteRegister(Register.C, true);
        opcodeTable[0x0D] = () -> InstructionLogic.IncrementDecrementByteRegister(Register.C, false);
        opcodeTable[0x0E] = () -> InstructionLogic.LoadRegisterFromImmediate(Register.C, FetchByte());
        opcodeTable[0x0F] = () -> InstructionLogic.Rotate(RotateType.RRCA, Register.A);

        opcodeTable[0x10] = () -> {
            FetchByte();
            return InstructionLogic.Stop();
        };
        opcodeTable[0x11] = () -> InstructionLogic.LoadRegisterPairFromImmediate(Register.DE, FetchWord());
        opcodeTable[0x12] = () -> InstructionLogic.AccumulatorToMemoryViaRegisterPair(Register.DE);
        opcodeTable[0x13] = () -> InstructionLogic.IncrementDecrementShort(Register.DE, true);
        opcodeTable[0x14] = () -> InstructionLogic.IncrementDecrementByteRegister(Register.D, true);
        opcodeTable[0x15] = () -> InstructionLogic.IncrementDecrementByteRegister(Register.D, false);
        opcodeTable[0x16] = () -> InstructionLogic.LoadRegisterFromImmediate(Register.D, FetchByte());
        opcodeTable[0x17] = () -> InstructionLogic.Rotate(RotateType.RLA, Register.A);
        opcodeTable[0x18] = () -> InstructionLogic.Jump(true, true, false, FetchByte());
        opcodeTable[0x19] = () -> InstructionLogic.AddPairHL(Register.DE);
        opcodeTable[0x1A] = () -> InstructionLogic.LoadAccumulatorFromMemoryViaRegisterPair(Register.DE);
        opcodeTable[0x1B] = () -> InstructionLogic.IncrementDecrementShort(Register.DE, false);
        opcodeTable[0x1C] = () -> InstructionLogic.IncrementDecrementByteRegister(Register.E, true);
        opcodeTable[0x1D] = () -> InstructionLogic.IncrementDecrementByteRegister(Register.E, false);
        opcodeTable[0x1E] = () -> InstructionLogic.LoadRegisterFromImmediate(Register.E, FetchByte());
        opcodeTable[0x1F] = () -> InstructionLogic.Rotate(RotateType.RRA, Register.A);

        opcodeTable[0x20] = () -> InstructionLogic.Jump(IsNz(), true, false, FetchByte());
        opcodeTable[0x21] = () -> InstructionLogic.LoadRegisterPairFromImmediate(Register.HL, FetchWord());
        opcodeTable[0x22] = InstructionLogic::AccumulatorToMemoryViaHLIncrement;
        opcodeTable[0x23] = () -> InstructionLogic.IncrementDecrementShort(Register.HL, true);
        opcodeTable[0x24] = () -> InstructionLogic.IncrementDecrementByteRegister(Register.H, true);
        opcodeTable[0x25] = () -> InstructionLogic.IncrementDecrementByteRegister(Register.H, false);
        opcodeTable[0x26] = () -> InstructionLogic.LoadRegisterFromImmediate(Register.H, FetchByte());
        opcodeTable[0x27] = InstructionLogic::DecimalAdjustAccumulator;
        opcodeTable[0x28] = () -> InstructionLogic.Jump(IsZ(), true, false, FetchByte());
        opcodeTable[0x29] = () -> InstructionLogic.AddPairHL(Register.HL);
        opcodeTable[0x2A] = InstructionLogic::LoadAccumulatorFromMemoryViaHLIncrement;
        opcodeTable[0x2B] = () -> InstructionLogic.IncrementDecrementShort(Register.HL, false);
        opcodeTable[0x2C] = () -> InstructionLogic.IncrementDecrementByteRegister(Register.L, true);
        opcodeTable[0x2D] = () -> InstructionLogic.IncrementDecrementByteRegister(Register.L, false);
        opcodeTable[0x2E] = () -> InstructionLogic.LoadRegisterFromImmediate(Register.L, FetchByte());
        opcodeTable[0x2F] = InstructionLogic::ComplementAccumulator;

        opcodeTable[0x30] = () -> InstructionLogic.Jump(IsNc(), true, false, FetchByte());
        opcodeTable[0x31] = () -> InstructionLogic.LoadRegisterPairFromImmediate(Register.SP, FetchWord());
        opcodeTable[0x32] = InstructionLogic::AccumulatorToMemoryViaHLDecrement;
        opcodeTable[0x33] = () -> InstructionLogic.IncrementDecrementShort(Register.SP, true);
        opcodeTable[0x34] = () -> InstructionLogic.IncrementDecrementByteHL(true);
        opcodeTable[0x35] = () -> InstructionLogic.IncrementDecrementByteHL(false);
        opcodeTable[0x36] = () -> InstructionLogic.ImmediateToMemoryViaHL(FetchByte());
        opcodeTable[0x37] = InstructionLogic::SetCarryFlag;
        opcodeTable[0x38] = () -> InstructionLogic.Jump(IsC(), true, false, FetchByte());
        opcodeTable[0x39] = () -> InstructionLogic.AddPairHL(Register.SP);
        opcodeTable[0x3A] = InstructionLogic::LoadAccumulatorFromMemoryViaHLDecrement;
        opcodeTable[0x3B] = () -> InstructionLogic.IncrementDecrementShort(Register.SP, false);
        opcodeTable[0x3C] = () -> InstructionLogic.IncrementDecrementByteRegister(Register.A, true);
        opcodeTable[0x3D] = () -> InstructionLogic.IncrementDecrementByteRegister(Register.A, false);
        opcodeTable[0x3E] = () -> InstructionLogic.LoadRegisterFromImmediate(Register.A, FetchByte());
        opcodeTable[0x3F] = InstructionLogic::ComplementCarryFlag;

        for (int destination = 0; destination < 8; destination++) {
            for (int source = 0; source < 8; source++) {
                int opcode = 0x40 + (destination * 8) + source;
                Register destinationRegister = registerMap[destination];
                Register sourceRegister = registerMap[source];

                if (opcode == 0x76) {
                    opcodeTable[opcode] = InstructionLogic::Halt;
                    continue;
                }

                if (destinationRegister == Register.HL_ADDR) {
                    opcodeTable[opcode] = () -> InstructionLogic.RegisterToMemoryViaHL(sourceRegister);
                } else if (sourceRegister == Register.HL_ADDR) {
                    opcodeTable[opcode] = () -> InstructionLogic.LoadRegisterFromMemoryViaHL(destinationRegister);
                } else {
                    opcodeTable[opcode] = () -> InstructionLogic.LoadRegisterFromRegister(destinationRegister, sourceRegister);
                }
            }
        }

        for (int index = 0; index < 8; index++) {
            Register register = registerMap[index];

            opcodeTable[0x80 + index] = register == Register.HL_ADDR
                    ? () -> InstructionLogic.Arithmetic(ArithmeticType.ADD)
                    : () -> InstructionLogic.Arithmetic(ArithmeticType.ADD, register);
            opcodeTable[0x88 + index] = register == Register.HL_ADDR
                    ? () -> InstructionLogic.Arithmetic(ArithmeticType.ADD, true)
                    : () -> InstructionLogic.Arithmetic(ArithmeticType.ADD, register, true);
            opcodeTable[0x90 + index] = register == Register.HL_ADDR
                    ? () -> InstructionLogic.Arithmetic(ArithmeticType.SUB)
                    : () -> InstructionLogic.Arithmetic(ArithmeticType.SUB, register);
            opcodeTable[0x98 + index] = register == Register.HL_ADDR
                    ? () -> InstructionLogic.Arithmetic(ArithmeticType.SUB, true)
                    : () -> InstructionLogic.Arithmetic(ArithmeticType.SUB, register, true);
            opcodeTable[0xA0 + index] = register == Register.HL_ADDR
                    ? () -> InstructionLogic.Bitwise(BitwiseType.AND)
                    : () -> InstructionLogic.Bitwise(BitwiseType.AND, register);
            opcodeTable[0xA8 + index] = register == Register.HL_ADDR
                    ? () -> InstructionLogic.Bitwise(BitwiseType.XOR)
                    : () -> InstructionLogic.Bitwise(BitwiseType.XOR, register);
            opcodeTable[0xB0 + index] = register == Register.HL_ADDR
                    ? () -> InstructionLogic.Bitwise(BitwiseType.OR)
                    : () -> InstructionLogic.Bitwise(BitwiseType.OR, register);
            opcodeTable[0xB8 + index] = register == Register.HL_ADDR
                    ? () -> InstructionLogic.Arithmetic(ArithmeticType.CP)
                    : () -> InstructionLogic.Arithmetic(ArithmeticType.CP, register);
        }

        opcodeTable[0xC0] = () -> InstructionLogic.Return(IsNz(), false, true);
        opcodeTable[0xC1] = () -> InstructionLogic.StackPopToRegisterPair(Register.BC);
        opcodeTable[0xC2] = () -> InstructionLogic.Jump(IsNz(), false, false, FetchWord());
        opcodeTable[0xC3] = () -> InstructionLogic.Jump(true, false, false, FetchWord());
        opcodeTable[0xC4] = () -> InstructionLogic.Call(IsNz(), FetchWord());
        opcodeTable[0xC5] = () -> InstructionLogic.StackPushFromRegisterPair(Register.BC);
        opcodeTable[0xC6] = () -> InstructionLogic.Arithmetic(ArithmeticType.ADD, FetchByte());
        opcodeTable[0xC7] = () -> InstructionLogic.Restart(0x00);
        opcodeTable[0xC8] = () -> InstructionLogic.Return(IsZ(), false, true);
        opcodeTable[0xC9] = () -> InstructionLogic.Return(true, false, false);
        opcodeTable[0xCA] = () -> InstructionLogic.Jump(IsZ(), false, false, FetchWord());
        opcodeTable[0xCB] = () -> {
            int cbOpcode = FetchByte();
            return cbOpcodeTable[cbOpcode].Execute();
        };
        opcodeTable[0xCC] = () -> InstructionLogic.Call(IsZ(), FetchWord());
        opcodeTable[0xCD] = () -> InstructionLogic.Call(true, FetchWord());
        opcodeTable[0xCE] = () -> InstructionLogic.Arithmetic(ArithmeticType.ADD, FetchByte(), true);
        opcodeTable[0xCF] = () -> InstructionLogic.Restart(0x08);

        opcodeTable[0xD0] = () -> InstructionLogic.Return(IsNc(), false, true);
        opcodeTable[0xD1] = () -> InstructionLogic.StackPopToRegisterPair(Register.DE);
        opcodeTable[0xD2] = () -> InstructionLogic.Jump(IsNc(), false, false, FetchWord());
        opcodeTable[0xD4] = () -> InstructionLogic.Call(IsNc(), FetchWord());
        opcodeTable[0xD5] = () -> InstructionLogic.StackPushFromRegisterPair(Register.DE);
        opcodeTable[0xD6] = () -> InstructionLogic.Arithmetic(ArithmeticType.SUB, FetchByte());
        opcodeTable[0xD7] = () -> InstructionLogic.Restart(0x10);
        opcodeTable[0xD8] = () -> InstructionLogic.Return(IsC(), false, true);
        opcodeTable[0xD9] = () -> InstructionLogic.Return(true, true, false);
        opcodeTable[0xDA] = () -> InstructionLogic.Jump(IsC(), false, false, FetchWord());
        opcodeTable[0xDC] = () -> InstructionLogic.Call(IsC(), FetchWord());
        opcodeTable[0xDE] = () -> InstructionLogic.Arithmetic(ArithmeticType.SUB, FetchByte(), true);
        opcodeTable[0xDF] = () -> InstructionLogic.Restart(0x18);

        opcodeTable[0xE0] = () -> InstructionLogic.AccumulatorToMemoryWithImmediateMask(FetchByte());
        opcodeTable[0xE1] = () -> InstructionLogic.StackPopToRegisterPair(Register.HL);
        opcodeTable[0xE2] = InstructionLogic::AccumulatorToMemoryWithCRegisterMask;
        opcodeTable[0xE5] = () -> InstructionLogic.StackPushFromRegisterPair(Register.HL);
        opcodeTable[0xE6] = () -> InstructionLogic.Bitwise(BitwiseType.AND, FetchByte());
        opcodeTable[0xE7] = () -> InstructionLogic.Restart(0x20);
        opcodeTable[0xE8] = () -> InstructionLogic.AddByteSP(FetchByte());
        opcodeTable[0xE9] = () -> InstructionLogic.Jump(true, false, true, 0);
        opcodeTable[0xEA] = () -> InstructionLogic.AccumulatorToMemoryImmediate(FetchWord());
        opcodeTable[0xEE] = () -> InstructionLogic.Bitwise(BitwiseType.XOR, FetchByte());
        opcodeTable[0xEF] = () -> InstructionLogic.Restart(0x28);

        opcodeTable[0xF0] = () -> InstructionLogic.LoadAccumulatorFromMemoryViaMaskedImmediate(FetchByte());
        opcodeTable[0xF1] = () -> InstructionLogic.StackPopToRegisterPair(Register.AF);
        opcodeTable[0xF2] = InstructionLogic::LoadAccumulatorFromMemoryViaCRegisterMask;
        opcodeTable[0xF3] = () -> InstructionLogic.InterruptControl(false);
        opcodeTable[0xF5] = () -> InstructionLogic.StackPushFromRegisterPair(Register.AF);
        opcodeTable[0xF6] = () -> InstructionLogic.Bitwise(BitwiseType.OR, FetchByte());
        opcodeTable[0xF7] = () -> InstructionLogic.Restart(0x30);
        opcodeTable[0xF8] = () -> InstructionLogic.LoadToHLStackPointerPlusImmediate(FetchByte());
        opcodeTable[0xF9] = InstructionLogic::SetSPToHL;
        opcodeTable[0xFA] = () -> InstructionLogic.LoadAccumulatorFromMemoryViaImmediate(FetchWord());
        opcodeTable[0xFB] = () -> InstructionLogic.InterruptControl(true);
        opcodeTable[0xFE] = () -> InstructionLogic.Arithmetic(ArithmeticType.CP, FetchByte());
        opcodeTable[0xFF] = () -> InstructionLogic.Restart(0x38);
    }

    private void InitialiseCbOpcodes() {
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 8; column++) {
                int opcode = (row * 8) + column;
                Register register = registerMap[column];

                switch (row) {
                    case 0 -> cbOpcodeTable[opcode] = () -> InstructionLogic.Rotate(RotateType.RLC, register);
                    case 1 -> cbOpcodeTable[opcode] = () -> InstructionLogic.Rotate(RotateType.RRC, register);
                    case 2 -> cbOpcodeTable[opcode] = () -> InstructionLogic.Rotate(RotateType.RL, register);
                    case 3 -> cbOpcodeTable[opcode] = () -> InstructionLogic.Rotate(RotateType.RR, register);
                    case 4 -> cbOpcodeTable[opcode] = () -> InstructionLogic.Shift(ShiftType.SLA, register);
                    case 5 -> cbOpcodeTable[opcode] = () -> InstructionLogic.Shift(ShiftType.SRA, register);
                    case 6 -> cbOpcodeTable[opcode] = () -> InstructionLogic.Swap(register);
                    case 7 -> cbOpcodeTable[opcode] = () -> InstructionLogic.Shift(ShiftType.SRL, register);
                    default -> {
                    }
                }
            }
        }

        for (int bit = 0; bit < 8; bit++) {
            for (int registerIndex = 0; registerIndex < 8; registerIndex++) {
                int opcode = 0x40 + (bit * 8) + registerIndex;
                Register register = registerMap[registerIndex];
                final int targetBit = bit;
                if (register == Register.HL_ADDR) {
                    cbOpcodeTable[opcode] = () -> InstructionLogic.BitOperationHL(BitOpType.BIT, targetBit);
                } else {
                    cbOpcodeTable[opcode] = () -> InstructionLogic.BitOperation(BitOpType.BIT, targetBit, register);
                }
            }
        }

        for (int bit = 0; bit < 8; bit++) {
            for (int registerIndex = 0; registerIndex < 8; registerIndex++) {
                int opcode = 0x80 + (bit * 8) + registerIndex;
                Register register = registerMap[registerIndex];
                final int targetBit = bit;
                if (register == Register.HL_ADDR) {
                    cbOpcodeTable[opcode] = () -> InstructionLogic.BitOperationHL(BitOpType.RES, targetBit);
                } else {
                    cbOpcodeTable[opcode] = () -> InstructionLogic.BitOperation(BitOpType.RES, targetBit, register);
                }
            }
        }

        for (int bit = 0; bit < 8; bit++) {
            for (int registerIndex = 0; registerIndex < 8; registerIndex++) {
                int opcode = 0xC0 + (bit * 8) + registerIndex;
                Register register = registerMap[registerIndex];
                final int targetBit = bit;
                if (register == Register.HL_ADDR) {
                    cbOpcodeTable[opcode] = () -> InstructionLogic.BitOperationHL(BitOpType.SET, targetBit);
                } else {
                    cbOpcodeTable[opcode] = () -> InstructionLogic.BitOperation(BitOpType.SET, targetBit, register);
                }
            }
        }
    }

    /**
     * Returns the executable handler for an opcode byte.
     *
     * @param opcode opcode value
     * @param cbPrefixed whether the byte comes from CB-prefixed space
     * @return opcode handler
     */
    public OpcodeHandler DecodeInstruction(int opcode, boolean cbPrefixed) {
        return cbPrefixed ? cbOpcodeTable[opcode] : opcodeTable[opcode];
    }

    private boolean IsZ() {
        return cpu.GetFlag(Flag.Z);
    }

    private boolean IsNz() {
        return !cpu.GetFlag(Flag.Z);
    }

    private boolean IsC() {
        return cpu.GetFlag(Flag.C);
    }

    private boolean IsNc() {
        return !cpu.GetFlag(Flag.C);
    }

}

