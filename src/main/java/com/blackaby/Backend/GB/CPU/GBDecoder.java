package com.blackaby.Backend.GB.CPU;

import com.blackaby.Backend.GB.CPU.GBProcessor.Flag;
import com.blackaby.Backend.GB.CPU.GBProcessor.Register;
import com.blackaby.Backend.GB.CPU.GBInstructionLogic.ArithmeticType;
import com.blackaby.Backend.GB.CPU.GBInstructionLogic.BitOpType;
import com.blackaby.Backend.GB.CPU.GBInstructionLogic.BitwiseType;
import com.blackaby.Backend.GB.CPU.GBInstructionLogic.RotateType;
import com.blackaby.Backend.GB.CPU.GBInstructionLogic.ShiftType;
import com.blackaby.Backend.GB.Memory.GBMemory;

/**
 * Decodes opcode bytes into executable instruction handlers.
 * <p>
 * The decoder owns the primary and CB-prefixed lookup tables and provides the
 * immediate fetch helpers used while building those tables.
 */
public class GBDecoder {

    private final GBProcessor cpu;
    private final GBMemory memory;

    public final GBOpcodeHandler[] opcodeTable = new GBOpcodeHandler[256];
    public final GBOpcodeHandler[] cbOpcodeTable = new GBOpcodeHandler[256];

    private final Register[] registerMap = {
            Register.B, Register.C, Register.D, Register.E,
            Register.H, Register.L, Register.HL_ADDR, Register.A
    };

    /**
     * Creates the decoder and initialises its opcode tables.
     *
     * @param cpu    active CPU instance
     * @param memory active memory bus
     */
    public GBDecoder(GBProcessor cpu, GBMemory memory) {
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
        opcodeTable[0x00] = GBInstructionLogic::Nop;
        opcodeTable[0x01] = () -> GBInstructionLogic.LoadRegisterPairFromImmediate(Register.BC, FetchWord());
        opcodeTable[0x02] = () -> GBInstructionLogic.AccumulatorToMemoryViaRegisterPair(Register.BC);
        opcodeTable[0x03] = () -> GBInstructionLogic.IncrementDecrementShort(Register.BC, true);
        opcodeTable[0x04] = () -> GBInstructionLogic.IncrementDecrementByteRegister(Register.B, true);
        opcodeTable[0x05] = () -> GBInstructionLogic.IncrementDecrementByteRegister(Register.B, false);
        opcodeTable[0x06] = () -> GBInstructionLogic.LoadRegisterFromImmediate(Register.B, FetchByte());
        opcodeTable[0x07] = () -> GBInstructionLogic.Rotate(RotateType.RLCA, Register.A);
        opcodeTable[0x08] = () -> GBInstructionLogic.StoreSPInImmediateAddress(FetchWord());
        opcodeTable[0x09] = () -> GBInstructionLogic.AddPairHL(Register.BC);
        opcodeTable[0x0A] = () -> GBInstructionLogic.LoadAccumulatorFromMemoryViaRegisterPair(Register.BC);
        opcodeTable[0x0B] = () -> GBInstructionLogic.IncrementDecrementShort(Register.BC, false);
        opcodeTable[0x0C] = () -> GBInstructionLogic.IncrementDecrementByteRegister(Register.C, true);
        opcodeTable[0x0D] = () -> GBInstructionLogic.IncrementDecrementByteRegister(Register.C, false);
        opcodeTable[0x0E] = () -> GBInstructionLogic.LoadRegisterFromImmediate(Register.C, FetchByte());
        opcodeTable[0x0F] = () -> GBInstructionLogic.Rotate(RotateType.RRCA, Register.A);

        opcodeTable[0x10] = () -> {
            FetchByte();
            return GBInstructionLogic.Stop();
        };
        opcodeTable[0x11] = () -> GBInstructionLogic.LoadRegisterPairFromImmediate(Register.DE, FetchWord());
        opcodeTable[0x12] = () -> GBInstructionLogic.AccumulatorToMemoryViaRegisterPair(Register.DE);
        opcodeTable[0x13] = () -> GBInstructionLogic.IncrementDecrementShort(Register.DE, true);
        opcodeTable[0x14] = () -> GBInstructionLogic.IncrementDecrementByteRegister(Register.D, true);
        opcodeTable[0x15] = () -> GBInstructionLogic.IncrementDecrementByteRegister(Register.D, false);
        opcodeTable[0x16] = () -> GBInstructionLogic.LoadRegisterFromImmediate(Register.D, FetchByte());
        opcodeTable[0x17] = () -> GBInstructionLogic.Rotate(RotateType.RLA, Register.A);
        opcodeTable[0x18] = () -> GBInstructionLogic.Jump(true, true, false, FetchByte());
        opcodeTable[0x19] = () -> GBInstructionLogic.AddPairHL(Register.DE);
        opcodeTable[0x1A] = () -> GBInstructionLogic.LoadAccumulatorFromMemoryViaRegisterPair(Register.DE);
        opcodeTable[0x1B] = () -> GBInstructionLogic.IncrementDecrementShort(Register.DE, false);
        opcodeTable[0x1C] = () -> GBInstructionLogic.IncrementDecrementByteRegister(Register.E, true);
        opcodeTable[0x1D] = () -> GBInstructionLogic.IncrementDecrementByteRegister(Register.E, false);
        opcodeTable[0x1E] = () -> GBInstructionLogic.LoadRegisterFromImmediate(Register.E, FetchByte());
        opcodeTable[0x1F] = () -> GBInstructionLogic.Rotate(RotateType.RRA, Register.A);

        opcodeTable[0x20] = () -> GBInstructionLogic.Jump(IsNz(), true, false, FetchByte());
        opcodeTable[0x21] = () -> GBInstructionLogic.LoadRegisterPairFromImmediate(Register.HL, FetchWord());
        opcodeTable[0x22] = GBInstructionLogic::AccumulatorToMemoryViaHLIncrement;
        opcodeTable[0x23] = () -> GBInstructionLogic.IncrementDecrementShort(Register.HL, true);
        opcodeTable[0x24] = () -> GBInstructionLogic.IncrementDecrementByteRegister(Register.H, true);
        opcodeTable[0x25] = () -> GBInstructionLogic.IncrementDecrementByteRegister(Register.H, false);
        opcodeTable[0x26] = () -> GBInstructionLogic.LoadRegisterFromImmediate(Register.H, FetchByte());
        opcodeTable[0x27] = GBInstructionLogic::DecimalAdjustAccumulator;
        opcodeTable[0x28] = () -> GBInstructionLogic.Jump(IsZ(), true, false, FetchByte());
        opcodeTable[0x29] = () -> GBInstructionLogic.AddPairHL(Register.HL);
        opcodeTable[0x2A] = GBInstructionLogic::LoadAccumulatorFromMemoryViaHLIncrement;
        opcodeTable[0x2B] = () -> GBInstructionLogic.IncrementDecrementShort(Register.HL, false);
        opcodeTable[0x2C] = () -> GBInstructionLogic.IncrementDecrementByteRegister(Register.L, true);
        opcodeTable[0x2D] = () -> GBInstructionLogic.IncrementDecrementByteRegister(Register.L, false);
        opcodeTable[0x2E] = () -> GBInstructionLogic.LoadRegisterFromImmediate(Register.L, FetchByte());
        opcodeTable[0x2F] = GBInstructionLogic::ComplementAccumulator;

        opcodeTable[0x30] = () -> GBInstructionLogic.Jump(IsNc(), true, false, FetchByte());
        opcodeTable[0x31] = () -> GBInstructionLogic.LoadRegisterPairFromImmediate(Register.SP, FetchWord());
        opcodeTable[0x32] = GBInstructionLogic::AccumulatorToMemoryViaHLDecrement;
        opcodeTable[0x33] = () -> GBInstructionLogic.IncrementDecrementShort(Register.SP, true);
        opcodeTable[0x34] = () -> GBInstructionLogic.IncrementDecrementByteHL(true);
        opcodeTable[0x35] = () -> GBInstructionLogic.IncrementDecrementByteHL(false);
        opcodeTable[0x36] = () -> GBInstructionLogic.ImmediateToMemoryViaHL(FetchByte());
        opcodeTable[0x37] = GBInstructionLogic::SetCarryFlag;
        opcodeTable[0x38] = () -> GBInstructionLogic.Jump(IsC(), true, false, FetchByte());
        opcodeTable[0x39] = () -> GBInstructionLogic.AddPairHL(Register.SP);
        opcodeTable[0x3A] = GBInstructionLogic::LoadAccumulatorFromMemoryViaHLDecrement;
        opcodeTable[0x3B] = () -> GBInstructionLogic.IncrementDecrementShort(Register.SP, false);
        opcodeTable[0x3C] = () -> GBInstructionLogic.IncrementDecrementByteRegister(Register.A, true);
        opcodeTable[0x3D] = () -> GBInstructionLogic.IncrementDecrementByteRegister(Register.A, false);
        opcodeTable[0x3E] = () -> GBInstructionLogic.LoadRegisterFromImmediate(Register.A, FetchByte());
        opcodeTable[0x3F] = GBInstructionLogic::ComplementCarryFlag;

        for (int destination = 0; destination < 8; destination++) {
            for (int source = 0; source < 8; source++) {
                int opcode = 0x40 + (destination * 8) + source;
                Register destinationRegister = registerMap[destination];
                Register sourceRegister = registerMap[source];

                if (opcode == 0x76) {
                    opcodeTable[opcode] = GBInstructionLogic::Halt;
                    continue;
                }

                if (destinationRegister == Register.HL_ADDR) {
                    opcodeTable[opcode] = () -> GBInstructionLogic.RegisterToMemoryViaHL(sourceRegister);
                } else if (sourceRegister == Register.HL_ADDR) {
                    opcodeTable[opcode] = () -> GBInstructionLogic.LoadRegisterFromMemoryViaHL(destinationRegister);
                } else {
                    opcodeTable[opcode] = () -> GBInstructionLogic.LoadRegisterFromRegister(destinationRegister,
                            sourceRegister);
                }
            }
        }

        for (int index = 0; index < 8; index++) {
            Register register = registerMap[index];

            opcodeTable[0x80 + index] = register == Register.HL_ADDR
                    ? () -> GBInstructionLogic.Arithmetic(ArithmeticType.ADD)
                    : () -> GBInstructionLogic.Arithmetic(ArithmeticType.ADD, register);
            opcodeTable[0x88 + index] = register == Register.HL_ADDR
                    ? () -> GBInstructionLogic.Arithmetic(ArithmeticType.ADD, true)
                    : () -> GBInstructionLogic.Arithmetic(ArithmeticType.ADD, register, true);
            opcodeTable[0x90 + index] = register == Register.HL_ADDR
                    ? () -> GBInstructionLogic.Arithmetic(ArithmeticType.SUB)
                    : () -> GBInstructionLogic.Arithmetic(ArithmeticType.SUB, register);
            opcodeTable[0x98 + index] = register == Register.HL_ADDR
                    ? () -> GBInstructionLogic.Arithmetic(ArithmeticType.SUB, true)
                    : () -> GBInstructionLogic.Arithmetic(ArithmeticType.SUB, register, true);
            opcodeTable[0xA0 + index] = register == Register.HL_ADDR
                    ? () -> GBInstructionLogic.Bitwise(BitwiseType.AND)
                    : () -> GBInstructionLogic.Bitwise(BitwiseType.AND, register);
            opcodeTable[0xA8 + index] = register == Register.HL_ADDR
                    ? () -> GBInstructionLogic.Bitwise(BitwiseType.XOR)
                    : () -> GBInstructionLogic.Bitwise(BitwiseType.XOR, register);
            opcodeTable[0xB0 + index] = register == Register.HL_ADDR
                    ? () -> GBInstructionLogic.Bitwise(BitwiseType.OR)
                    : () -> GBInstructionLogic.Bitwise(BitwiseType.OR, register);
            opcodeTable[0xB8 + index] = register == Register.HL_ADDR
                    ? () -> GBInstructionLogic.Arithmetic(ArithmeticType.CP)
                    : () -> GBInstructionLogic.Arithmetic(ArithmeticType.CP, register);
        }

        opcodeTable[0xC0] = () -> GBInstructionLogic.Return(IsNz(), false, true);
        opcodeTable[0xC1] = () -> GBInstructionLogic.StackPopToRegisterPair(Register.BC);
        opcodeTable[0xC2] = () -> GBInstructionLogic.Jump(IsNz(), false, false, FetchWord());
        opcodeTable[0xC3] = () -> GBInstructionLogic.Jump(true, false, false, FetchWord());
        opcodeTable[0xC4] = () -> GBInstructionLogic.Call(IsNz(), FetchWord());
        opcodeTable[0xC5] = () -> GBInstructionLogic.StackPushFromRegisterPair(Register.BC);
        opcodeTable[0xC6] = () -> GBInstructionLogic.Arithmetic(ArithmeticType.ADD, FetchByte());
        opcodeTable[0xC7] = () -> GBInstructionLogic.Restart(0x00);
        opcodeTable[0xC8] = () -> GBInstructionLogic.Return(IsZ(), false, true);
        opcodeTable[0xC9] = () -> GBInstructionLogic.Return(true, false, false);
        opcodeTable[0xCA] = () -> GBInstructionLogic.Jump(IsZ(), false, false, FetchWord());
        opcodeTable[0xCB] = () -> {
            int cbOpcode = FetchByte();
            return cbOpcodeTable[cbOpcode].Execute();
        };
        opcodeTable[0xCC] = () -> GBInstructionLogic.Call(IsZ(), FetchWord());
        opcodeTable[0xCD] = () -> GBInstructionLogic.Call(true, FetchWord());
        opcodeTable[0xCE] = () -> GBInstructionLogic.Arithmetic(ArithmeticType.ADD, FetchByte(), true);
        opcodeTable[0xCF] = () -> GBInstructionLogic.Restart(0x08);

        opcodeTable[0xD0] = () -> GBInstructionLogic.Return(IsNc(), false, true);
        opcodeTable[0xD1] = () -> GBInstructionLogic.StackPopToRegisterPair(Register.DE);
        opcodeTable[0xD2] = () -> GBInstructionLogic.Jump(IsNc(), false, false, FetchWord());
        opcodeTable[0xD4] = () -> GBInstructionLogic.Call(IsNc(), FetchWord());
        opcodeTable[0xD5] = () -> GBInstructionLogic.StackPushFromRegisterPair(Register.DE);
        opcodeTable[0xD6] = () -> GBInstructionLogic.Arithmetic(ArithmeticType.SUB, FetchByte());
        opcodeTable[0xD7] = () -> GBInstructionLogic.Restart(0x10);
        opcodeTable[0xD8] = () -> GBInstructionLogic.Return(IsC(), false, true);
        opcodeTable[0xD9] = () -> GBInstructionLogic.Return(true, true, false);
        opcodeTable[0xDA] = () -> GBInstructionLogic.Jump(IsC(), false, false, FetchWord());
        opcodeTable[0xDC] = () -> GBInstructionLogic.Call(IsC(), FetchWord());
        opcodeTable[0xDE] = () -> GBInstructionLogic.Arithmetic(ArithmeticType.SUB, FetchByte(), true);
        opcodeTable[0xDF] = () -> GBInstructionLogic.Restart(0x18);

        opcodeTable[0xE0] = () -> GBInstructionLogic.AccumulatorToMemoryWithImmediateMask(FetchByte());
        opcodeTable[0xE1] = () -> GBInstructionLogic.StackPopToRegisterPair(Register.HL);
        opcodeTable[0xE2] = GBInstructionLogic::AccumulatorToMemoryWithCRegisterMask;
        opcodeTable[0xE5] = () -> GBInstructionLogic.StackPushFromRegisterPair(Register.HL);
        opcodeTable[0xE6] = () -> GBInstructionLogic.Bitwise(BitwiseType.AND, FetchByte());
        opcodeTable[0xE7] = () -> GBInstructionLogic.Restart(0x20);
        opcodeTable[0xE8] = () -> GBInstructionLogic.AddByteSP(FetchByte());
        opcodeTable[0xE9] = () -> GBInstructionLogic.Jump(true, false, true, 0);
        opcodeTable[0xEA] = () -> GBInstructionLogic.AccumulatorToMemoryImmediate(FetchWord());
        opcodeTable[0xEE] = () -> GBInstructionLogic.Bitwise(BitwiseType.XOR, FetchByte());
        opcodeTable[0xEF] = () -> GBInstructionLogic.Restart(0x28);

        opcodeTable[0xF0] = () -> GBInstructionLogic.LoadAccumulatorFromMemoryViaMaskedImmediate(FetchByte());
        opcodeTable[0xF1] = () -> GBInstructionLogic.StackPopToRegisterPair(Register.AF);
        opcodeTable[0xF2] = GBInstructionLogic::LoadAccumulatorFromMemoryViaCRegisterMask;
        opcodeTable[0xF3] = () -> GBInstructionLogic.InterruptControl(false);
        opcodeTable[0xF5] = () -> GBInstructionLogic.StackPushFromRegisterPair(Register.AF);
        opcodeTable[0xF6] = () -> GBInstructionLogic.Bitwise(BitwiseType.OR, FetchByte());
        opcodeTable[0xF7] = () -> GBInstructionLogic.Restart(0x30);
        opcodeTable[0xF8] = () -> GBInstructionLogic.LoadToHLStackPointerPlusImmediate(FetchByte());
        opcodeTable[0xF9] = GBInstructionLogic::SetSPToHL;
        opcodeTable[0xFA] = () -> GBInstructionLogic.LoadAccumulatorFromMemoryViaImmediate(FetchWord());
        opcodeTable[0xFB] = () -> GBInstructionLogic.InterruptControl(true);
        opcodeTable[0xFE] = () -> GBInstructionLogic.Arithmetic(ArithmeticType.CP, FetchByte());
        opcodeTable[0xFF] = () -> GBInstructionLogic.Restart(0x38);
    }

    private void InitialiseCbOpcodes() {
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 8; column++) {
                int opcode = (row * 8) + column;
                Register register = registerMap[column];

                switch (row) {
                    case 0 -> cbOpcodeTable[opcode] = () -> GBInstructionLogic.Rotate(RotateType.RLC, register);
                    case 1 -> cbOpcodeTable[opcode] = () -> GBInstructionLogic.Rotate(RotateType.RRC, register);
                    case 2 -> cbOpcodeTable[opcode] = () -> GBInstructionLogic.Rotate(RotateType.RL, register);
                    case 3 -> cbOpcodeTable[opcode] = () -> GBInstructionLogic.Rotate(RotateType.RR, register);
                    case 4 -> cbOpcodeTable[opcode] = () -> GBInstructionLogic.Shift(ShiftType.SLA, register);
                    case 5 -> cbOpcodeTable[opcode] = () -> GBInstructionLogic.Shift(ShiftType.SRA, register);
                    case 6 -> cbOpcodeTable[opcode] = () -> GBInstructionLogic.Swap(register);
                    case 7 -> cbOpcodeTable[opcode] = () -> GBInstructionLogic.Shift(ShiftType.SRL, register);
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
                    cbOpcodeTable[opcode] = () -> GBInstructionLogic.BitOperationHL(BitOpType.BIT, targetBit);
                } else {
                    cbOpcodeTable[opcode] = () -> GBInstructionLogic.BitOperation(BitOpType.BIT, targetBit, register);
                }
            }
        }

        for (int bit = 0; bit < 8; bit++) {
            for (int registerIndex = 0; registerIndex < 8; registerIndex++) {
                int opcode = 0x80 + (bit * 8) + registerIndex;
                Register register = registerMap[registerIndex];
                final int targetBit = bit;
                if (register == Register.HL_ADDR) {
                    cbOpcodeTable[opcode] = () -> GBInstructionLogic.BitOperationHL(BitOpType.RES, targetBit);
                } else {
                    cbOpcodeTable[opcode] = () -> GBInstructionLogic.BitOperation(BitOpType.RES, targetBit, register);
                }
            }
        }

        for (int bit = 0; bit < 8; bit++) {
            for (int registerIndex = 0; registerIndex < 8; registerIndex++) {
                int opcode = 0xC0 + (bit * 8) + registerIndex;
                Register register = registerMap[registerIndex];
                final int targetBit = bit;
                if (register == Register.HL_ADDR) {
                    cbOpcodeTable[opcode] = () -> GBInstructionLogic.BitOperationHL(BitOpType.SET, targetBit);
                } else {
                    cbOpcodeTable[opcode] = () -> GBInstructionLogic.BitOperation(BitOpType.SET, targetBit, register);
                }
            }
        }
    }

    /**
     * Returns the executable handler for an opcode byte.
     *
     * @param opcode     opcode value
     * @param cbPrefixed whether the byte comes from CB-prefixed space
     * @return opcode handler
     */
    public GBOpcodeHandler DecodeInstruction(int opcode, boolean cbPrefixed) {
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
