package com.blackaby.Backend.Emulation.CPU;

import com.blackaby.Backend.Emulation.Memory.DuckMemory;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Flag;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Register;
import com.blackaby.Backend.Emulation.CPU.InstructionTypeManager.InstructionType;

/**
 * This class represents an instruction for the CPU
 * It has a method for executing the instruction, and multiple different types
 * of instructions that can be executed
 * The instruction is created with a CPU, Memory, instruction type, opcode, and
 * operands
 */
public class Duckstruction {

    protected DuckCPU cpu;
    protected DuckMemory memory;

    private byte[] values = { 0, 0, 0 };
    private InstructionType type;

    /**
     * Creates a new Duckstruction with the specified CPU, Memory, instruction type,
     * opcode, and operands
     * 
     * @param cpu      the CPU to use
     * @param memory   the Memory to use
     * @param type     the type of instruction
     * @param opcode   the opcode of the instruction
     * @param operands the operands of the instruction
     */
    public Duckstruction(DuckCPU cpu, DuckMemory memory, InstructionType type, byte opcode, byte[] operands) {
        this.cpu = cpu;
        this.memory = memory;
        this.type = type;
        int opcodeCount = 0;
        // If one of the instruction types that includes values in opcode, extract value
        // and set as values
        if (type.doesExtractOpcode()) {
            // Find a in opcode and extract value
            String opcodeString = type.getOpcode();
            byte opcodeValues[] = new byte[2];
            char currentChar = 'a';
            for (int currentValue = 0; opcodeString.indexOf(currentChar) != -1; currentValue++) {
                for (int j = 0; j < opcodeString.length(); j++) {
                    if (opcodeString.charAt(j) == currentChar) {
                        // Shift firstValue left by 1
                        opcodeValues[currentValue] <<= 1;
                        // Get bit from byte opcode
                        opcodeValues[currentValue] |= (opcode & (1 << (7 - j))) >> (7 - j);
                    }
                }
                currentChar++;
                opcodeCount++;
            }
            // Set values to opcode values
            for (int i = 0; i < opcodeCount; i++)
                this.values[i] = opcodeValues[i];
        }
        if (operands != null) {
            int remainingValues = Math.min(values.length - opcodeCount, operands.length);
            for (int i = 0; i < remainingValues; i++) {
                this.values[i + opcodeCount] = operands[i];
            }
        }
    }

    /**
     * This method executes the instruction based on the type of instruction
     */
    public void execute() {
        System.out.println("Executing instruction: " + type);
        switch (type) {
            // Loads
            case REGISTER_REGISTER: {
                Register reg1 = Register.getRegFrom3Bit(values[0]);
                Register reg2 = Register.getRegFrom3Bit(values[1]);
                if (reg1 == Register.HL_ADDR) {
                    if (reg2 == Register.HL_ADDR) {
                        // Same as NOP
                    } else {
                        memory.write(cpu.regGet16(Register.HL), cpu.regGet(reg2));
                    }
                } else if (reg2 == Register.HL_ADDR) {
                    if (reg1 == Register.HL_ADDR) {
                        // Same as NOP
                    } else {
                        cpu.regSet(reg1, memory.read(cpu.regGet16(Register.HL)));
                    }
                } else {
                    cpu.regSet(reg1, cpu.regGet(reg2));
                }
                break;
            }
            case IMMEDIATE_REGISTER: {
                Register reg = Register.getRegFrom3Bit(values[0]);
                if (reg != Register.HL_ADDR) {
                    cpu.regSet(reg, values[1]);
                } else {
                    // Same as Immediate Memory HL
                    memory.write(cpu.regGet16(Register.HL), values[1]);
                }
                break;
            }
            // Loads with memory
            case MEMORY_REGISTER_HL: {
                cpu.regSet(Register.getRegFrom3Bit(values[0]), memory.read(cpu.regGet16(Register.HL)));
                break;
            }
            case REGISTER_MEMORY_HL: {
                memory.write(cpu.regGet16(Register.HL), cpu.regGet(Register.getRegFrom3Bit(values[0])));
                break;
            }
            case IMMEDIATE_MEMORY_HL: {
                memory.write(cpu.regGet16(Register.HL), values[0]);
                break;
            }
            case MEMORY_ACCUMULATOR_BC: {
                cpu.regSet(Register.A, memory.read(cpu.regGet16(Register.BC)));
                break;
            }
            case MEMORY_ACCUMULATOR_DE: {
                cpu.regSet(Register.A, memory.read(cpu.regGet16(Register.DE)));
                break;
            }
            case ACCUMULATOR_MEMORY_BC: {
                memory.write(cpu.regGet16(Register.BC), cpu.regGet(Register.A));
                break;
            }
            case ACCUMULATOR_MEMORY_DE: {
                memory.write(cpu.regGet16(Register.DE), cpu.regGet(Register.A));
                break;
            }
            case MEMORY_ACCUMULATOR_IMMEDIATE: {
                short address = (short) ((values[1] << 8) | (values[0] & 0xFF));
                cpu.regSet(Register.A, memory.read(address));
                break;
            }
            case ACCUMULATOR_MEMORY_IMMEDIATE: {
                memory.write((values[1] << 8) | values[0], cpu.regGet(Register.A));
                break;
            }
            case MEMORY_ACCUMULATOR_MSB_0xFF_C: {
                cpu.regSet(Register.A, memory.read(0xFF00 + cpu.regGet(Register.C)));
                break;
            }
            case ACCUMULATOR_MEMORY_MSB_0xFF_C: {
                memory.write(0xFF00 + cpu.regGet(Register.C), cpu.regGet(Register.A));
                break;
            }
            case MEMORY_ACCUMULATOR_MSB_0xFF_IMMEDIATE: {
                cpu.regSet(Register.A, memory.read(0xFF00 + values[0]));
                break;
            }
            case ACCUMULATOR_MEMORY_MSB_0xFF_IMMEDIATE: {
                memory.write(0xFF00 + values[0], cpu.regGet(Register.A));
                break;
            }
            case MEMORY_ACCUMULATOR_HL_DECREMENT: {
                cpu.regSet(Register.A, memory.read(cpu.regGet16(Register.HL)));
                cpu.regSet16(Register.HL, (short) (cpu.regGet16(Register.HL) - 1));
                break;
            }
            case ACCUMULATOR_MEMORY_HL_DECREMENT: {
                memory.write(cpu.regGet16(Register.HL), cpu.regGet(Register.A));
                cpu.regSet16(Register.HL, (short) (cpu.regGet16(Register.HL) - 1));
                break;
            }
            case MEMORY_ACCUMULATOR_HL_INCREMENT: {
                cpu.regSet(Register.A, memory.read(cpu.regGet16(Register.HL)));
                cpu.regSet16(Register.HL, (short) (cpu.regGet16(Register.HL) + 1));
                break;
            }
            case ACCUMULATOR_MEMORY_HL_INCREMENT: {
                memory.write(cpu.regGet16(Register.HL), cpu.regGet(Register.A));
                cpu.regSet16(Register.HL, (short) (cpu.regGet16(Register.HL) + 1));
                break;
            }
            // Misc
            case IMMEDIATE_PAIR: {
                cpu.regSet16(Register.getRegFrom2Bit(values[0]), (short) ((values[2] << 8) | values[1]));
                break;
            }
            // Stack
            case SP_MEMORY_IMMEDIATE: {
                int nn = (values[1] << 8) | values[0];
                short sp = cpu.regGet16(Register.SP);
                memory.write(nn, (byte) (sp >> 8));
                memory.write(nn + 1, (byte) sp);
                break;
            }
            case HL_SP: {
                cpu.regSet16(Register.SP, cpu.regGet16(Register.HL));
                break;
            }
            case STACKPUSH_RR: {
                short sp = cpu.regGet16(Register.SP);
                short value = cpu.regGet16(Register.getRegFrom2Bit(values[0]));
                memory.stackWrite(sp - 1, (byte) (value >> 8));
                memory.stackWrite(sp - 2, (byte) value);
                cpu.regSet16(Register.SP, (byte) (sp - 2));
                break;
            }
            case STACKPOP_RR: {
                byte lsb = memory.stackRead(cpu.regGet16(Register.SP));
                byte msb = memory.stackRead((byte) (cpu.regGet16(Register.SP) + 1));
                short value = (short) ((msb << 8) | lsb);
                cpu.regSet16(Register.getRegFrom2Bit(values[0]), value);
                cpu.regSet16(Register.SP, (byte) (cpu.regGet16(Register.SP) + 2));
                break;
            }
            case SP_PLUS_IMMEDIATE8_HL: {
                short sp = cpu.regGet16(Register.SP);
                short offset = (short) (values[0]);
                short result = (short) (sp + offset);
                cpu.regSet16(Register.HL, result);
                cpu.deactivateFlags(Flag.Z, Flag.N);
                cpu.setFlag(Flag.H, (sp & 0xF) + (offset & 0xF) > 0xF);
                cpu.setFlag(Flag.C, (sp & 0xFF) + (offset & 0xFF) > 0xFF);
                break;
            }
            // Additions
            case ADD_REGISTER_ACCUMULATOR: {
                cpu.alu.Add(Register.getRegFrom3Bit(values[0]), Register.A);
                break;
            }
            case ADD_MEMORY_HL: {
                cpu.alu.Add(memory.read(cpu.regGet16(Register.HL)), Register.A);
                break;
            }
            case ADD_IMMEDIATE: {
                cpu.alu.Add(values[0], Register.A);
                break;
            }
            // Additions with carry
            case ADC_REGISTER: {
                cpu.alu.Add(cpu.getFlag(Flag.C), Register.A);
                cpu.alu.Add(Register.getRegFrom3Bit(values[0]), Register.A);
                break;
            }
            case ADC_MEMORY_HL: {
                cpu.alu.Add(cpu.getFlag(Flag.C), Register.A);
                cpu.alu.Add(memory.read(cpu.regGet16(Register.HL)), Register.A);
                break;
            }
            case ADC_IMMEDIATE: {
                cpu.alu.Add(cpu.getFlag(Flag.C), Register.A);
                cpu.alu.Add(values[0], Register.A);
                break;
            }
            // Subtractions
            case SUB_REGISTER: {
                cpu.alu.Sub(Register.getRegFrom3Bit(values[0]), Register.A);
                break;
            }
            case SUB_MEMORY_HL: {
                cpu.alu.Sub(memory.read(cpu.regGet16(Register.HL)), Register.A);
                break;
            }
            case SUB_IMMEDIATE: {
                cpu.alu.Sub(values[0], Register.A);
                break;
            }
            // Subtractions with carry
            case SBC_REGISTER: {
                cpu.alu.Sub(cpu.getFlag(Flag.C), Register.A);
                cpu.alu.Sub(Register.getRegFrom3Bit(values[0]), Register.A);
                break;
            }
            case SBC_MEMORY_HL: {
                cpu.alu.Sub(cpu.getFlag(Flag.C), Register.A);
                cpu.alu.Sub(memory.read(cpu.regGet16(Register.HL)), Register.A);
                break;
            }
            case SBC_IMMEDIATE: {
                cpu.alu.Sub(cpu.getFlag(Flag.C), Register.A);
                cpu.alu.Sub(values[0], Register.A);
                break;
            }
            // Comparison functions
            case CP_REGISTER: {
                byte from = cpu.regGet(Register.A);
                byte with = cpu.regGet(Register.getRegFrom3Bit(values[0]));
                short result = (short) (from - with);
                cpu.alu.updateSubFlags(result, from, with);
                break;
            }
            case CP_MEMORY_HL: {
                byte from = cpu.regGet(Register.A);
                byte with = memory.read(cpu.regGet16(Register.HL));
                short result = (short) (from - with);
                cpu.alu.updateSubFlags(result, from, with);
                break;
            }
            case CP_IMMEDIATE: {
                byte from = cpu.regGet(Register.A);
                byte with = values[0];
                short result = (short) (from - with);
                cpu.alu.updateSubFlags(result, from, with);
                break;
            }
            // IDU functions
            case INC_REGISTER: {
                Register reg = Register.getRegFrom3Bit(values[0]);
                cpu.regSet(reg, cpu.idu.calculate(cpu.regGet(reg), true));
                break;
            }
            case INC_MEMORY_HL: {
                short address = cpu.regGet16(Register.HL);
                memory.write(address, cpu.idu.calculate(memory.read(address), true));
                break;
            }
            case DEC_REGISTER: {
                Register reg = Register.getRegFrom3Bit(values[0]);
                cpu.regSet(reg, cpu.idu.calculate(cpu.regGet(reg), true));
                break;
            }
            case DEC_MEMORY_HL: {
                short address = cpu.regGet16(Register.HL);
                memory.write(address, cpu.idu.calculate(memory.read(address), true));
                break;
            }
            // Bitwise functions
            case AND_REGISTER: {
                byte result = (byte) ((byte) cpu.regGet(Register.A)
                        & (byte) cpu.regGet(Register.getRegFrom3Bit(values[0])));
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.setFlag(Flag.H, true);
                cpu.deactivateFlags(Flag.N, Flag.C);
                break;
            }
            case AND_MEMORY_HL: {
                byte result = (byte) ((byte) cpu.regGet(Register.A) & (byte) memory.read(cpu.regGet16(Register.HL)));
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.setFlag(Flag.H, true);
                cpu.deactivateFlags(Flag.N, Flag.C);
                break;
            }
            case AND_IMMEDIATE: {
                byte result = (byte) ((byte) cpu.regGet(Register.A) & (byte) values[0]);
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.setFlag(Flag.H, true);
                cpu.deactivateFlags(Flag.N, Flag.C);
                break;
            }
            case OR_REGISTER: {
                byte result = (byte) ((byte) cpu.regGet(Register.A)
                        | (byte) cpu.regGet(Register.getRegFrom3Bit(values[0])));
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.C);
                break;
            }
            case OR_MEMORY_HL: {
                byte result = (byte) ((byte) cpu.regGet(Register.A) | (byte) memory.read(cpu.regGet16(Register.HL)));
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.C);
                break;
            }
            case OR_IMMEDIATE: {
                byte result = (byte) ((byte) cpu.regGet(Register.A) | (byte) values[0]);
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.C);
                break;
            }
            case XOR_REGISTER: {
                byte result = (byte) ((byte) cpu.regGet(Register.A)
                        ^ (byte) cpu.regGet(Register.getRegFrom3Bit(values[0])));
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.C);
                break;
            }
            case XOR_MEMORY_HL: {
                byte result = (byte) ((byte) cpu.regGet(Register.A) ^ (byte) memory.read(cpu.regGet16(Register.HL)));
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.C);
                break;
            }
            case XOR_IMMEDIATE: {
                byte result = (byte) ((byte) cpu.regGet(Register.A) ^ (byte) values[0]);
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.C);
                break;
            }
            case CCF: {
                cpu.deactivateFlags(Flag.N, Flag.H);
                cpu.setFlag(Flag.C, !cpu.getFlagBoolean(Flag.C));
                break;
            }
            case SCF: {
                cpu.deactivateFlags(Flag.N, Flag.H);
                cpu.setFlag(Flag.C, true);
                break;
            }
            case DAA: {
                // TODO: Research and implement DAA function
                break;
            }
            case CPL: {
                cpu.regSet(Register.A, (byte) ~cpu.regGet(Register.A));
                cpu.setFlag(Flag.N, true);
                cpu.setFlag(Flag.H, true);
                break;
            }
            // 16-bit arithmetic instructions
            case INC_REGISTER_16: {
                Register reg = Register.getRegFrom2Bit(values[0]);
                cpu.regSet16(reg, (short) (cpu.regGet16(reg) + 1));
                break;
            }
            case DEC_REGISTER_16: {
                Register reg = Register.getRegFrom2Bit(values[0]);
                cpu.regSet16(reg, (short) (cpu.regGet16(reg) - 1));
                break;
            }
            case ADD_PAIR_TO_HL: {
                short value = cpu.regGet16(Register.getRegFrom2Bit(values[0]));
                cpu.regSet16(Register.HL, (short) (cpu.regGet16(Register.HL) + value));
                cpu.deactivateFlags(Flag.N);
                cpu.setFlag(Flag.H, (value & 0xFFF) + (cpu.regGet16(Register.HL) & 0xFFF) > 0xFFF);
                cpu.setFlag(Flag.C, (value & 0xFFFF) + (cpu.regGet16(Register.HL) & 0xFFFF) > 0xFFFF);
            }
            case ADD_BYTE_TO_SP: {
                short result = (short) (cpu.regGet16(Register.SP) + (byte) values[0]);
                cpu.regSet16(Register.SP, result);
                cpu.deactivateFlags(Flag.Z, Flag.N);
                cpu.setFlag(Flag.H, (cpu.regGet16(Register.SP) & 0xF) + (values[0] & 0xF) > 0xF);
                cpu.setFlag(Flag.C, (cpu.regGet16(Register.SP) & 0xFF) + (values[0] & 0xFF) > 0xFF);
            }
            case ROTATE_LEFT_CIRCLE_ACCUMULATOR: {
                // TODO: Implement rotate left circle accumulator
                break;
            }
            case ROTATE_RIGHT_CIRCLE_ACCUMULATOR: {
                // TODO: Implement rotate right circle accumulator
                break;
            }
            case ROTATE_LEFT_ACCUMULATOR: {
                // TODO: Implement rotate left accumulator
                break;
            }
            case ROTATE_RIGHT_ACCUMULATOR: {
                // TODO: Implement rotate right accumulator
                break;
            }
            case ROTATE_LEFT_CIRCLE_REGISTER: {
                // TODO: Implement rotate left circle register
                break;
            }
            case ROTATE_LEFT_CIRCLE_HL: {
                // TODO: Implement rotate left circle HL
                break;
            }
            case ROTATE_RIGHT_CIRCLE_REGISTER: {
                // TODO: Implement rotate right circle register
                break;
            }
            case ROTATE_RIGHT_CIRCLE_HL: {
                // TODO: Implement rotate right circle HL
                break;
            }
            case ROTATE_LEFT_REGISTER: {
                // TODO: Implement rotate left register
                break;
            }
            case ROTATE_LEFT_HL: {
                // TODO: Implement rotate left HL
                break;
            }
            case ROTATE_RIGHT_REGISTER: {
                // TODO: Implement rotate right register
                break;
            }
            case ROTATE_RIGHT_HL: {
                // TODO: Implement rotate right HL
                break;
            }
            case SHIFT_LEFT_ARITHMETIC_REGISTER: {
                // TODO: Implement shift left arithmetic
                break;
            }
            case SHIFT_LEFT_ARITHMETIC_HL: {
                // TODO: Implement shift left arithmetic
                break;
            }
            case SHIFT_RIGHT_ARITHMETIC_REGISTER: {
                // TODO: Implement shift right arithmetic
                break;
            }
            case SHIFT_RIGHT_ARITHMETIC_HL: {
                // TODO: Implement shift right arithmetic
                break;
            }
            case SWAP_NIBBLES_REGISTER: {
                // TODO: Implement swap nibbles
                break;
            }
            case SWAP_NIBBLES_HL: {
                // TODO: Implement swap nibbles
                break;
            }
            case SHIFT_RIGHT_LOGICAL_REGISTER: {
                // TODO: Implement shift right logical
                break;
            }
            case SHIFT_RIGHT_LOGICAL_HL: {
                // TODO: Implement shift right logical
                break;
            }
            case TEST_BIT_REGISTER: {
                // TODO: Implement bit test register
                break;
            }
            case TEST_BIT_HL: {
                // TODO: Implement bit test HL
                break;
            }
            case RESET_BIT_REGISTER: {
                // TODO: Implement bit reset register
                break;
            }
            case RESET_BIT_HL: {
                // TODO: Implement bit reset HL
                break;
            }
            case SET_BIT_REGISTER: {
                // TODO: Implement bit set register
                int bitPosition = values[0];
                Register reg = Register.getRegFrom3Bit(values[1]);
                byte value = cpu.regGet(reg);
                value |= (1 << bitPosition);
                cpu.regSet(reg, value);
                break;
            }
            case SET_BIT_HL: {
                // TODO: Implement bit set HL
                break;
            }
            // Control flow instructions
            case JUMP_UNCONDITIONAL: {
                byte lsb = values[0], msb = values[1];
                short address = (short) ((msb << 8) | lsb);
                cpu.regSet16(Register.PC, address);
                break;
            }
            case JUMP_HL: {
                cpu.regSet16(Register.PC, cpu.regGet16(Register.HL));
                break;
            }
            case JUMP_CONDITIONAL: {
                byte lsb = values[1], msb = values[2];
                short address = (short) ((msb << 8) | lsb);
                if (cpu.getFlagBoolean(Flag.getFlagFrom2Bit(values[0]))) {
                    cpu.regSet16(Register.PC, address);
                }
                break;
            }
            case JUMP_RELATIVE_UNCONDITIONAL: {
                byte offset = values[0];
                short address = (short) (cpu.regGet16(Register.PC) + offset);
                cpu.regSet16(Register.PC, address);
                break;
            }
            case JUMP_RELATIVE_CONDITIONAL: {
                byte offset = values[1];
                if (cpu.getFlagBoolean(Flag.getFlagFrom2Bit(values[0]))) {
                    short address = (short) (cpu.regGet16(Register.PC) + offset);
                    cpu.regSet16(Register.PC, address);
                }
                break;
            }
            case CALL_UNCONDITIONAL: {
                byte lsb = values[0], msb = values[1];
                short address = (short) ((msb << 8) | lsb);
                short sp = cpu.regGet16(Register.SP);
                memory.stackWrite(sp - 1, (byte) (cpu.regGet16(Register.PC) >> 8));
                memory.stackWrite(sp - 2, (byte) cpu.regGet16(Register.PC));
                cpu.regSet16(Register.SP, (short) (sp - 2));
                cpu.regSet16(Register.PC, address);
                break;
            }
            case CALL_CONDITIONAL: {
                if (cpu.getFlagBoolean(Flag.getFlagFrom2Bit(values[0]))) {
                    byte lsb = values[1], msb = values[2];
                    short address = (short) ((msb << 8) | lsb);
                    short sp = cpu.regGet16(Register.SP);
                    memory.stackWrite(sp - 1, (byte) (cpu.regGet16(Register.PC) >> 8));
                    memory.stackWrite(sp - 2, (byte) cpu.regGet16(Register.PC));
                    cpu.regSet16(Register.SP, (short) (sp - 2));
                    cpu.regSet16(Register.PC, address);
                }
                break;
            }
            case RETURN_UNCONDITIONAL: {
                short sp = cpu.regGet16(Register.SP);
                short lsb = memory.stackRead(sp);
                short msb = memory.stackRead((short) (sp + 1));
                short address = (short) ((msb << 8) | lsb);
                cpu.regSet16(Register.SP, (short) (sp + 2));
                cpu.regSet16(Register.PC, address);
                break;
            }
            case RETURN_CONDITIONAL: {
                if (cpu.getFlagBoolean(Flag.getFlagFrom2Bit(values[0]))) {
                    short sp = cpu.regGet16(Register.SP);
                    short lsb = memory.stackRead(sp);
                    short msb = memory.stackRead((short) (sp + 1));
                    short address = (short) ((msb << 8) | lsb);
                    cpu.regSet16(Register.SP, (short) (sp + 2));
                    cpu.regSet16(Register.PC, address);
                }
                break;
            }
            case RETURN_INTERRUPT: {
                byte lsb = memory.stackRead(cpu.regGet16(Register.SP));
                byte msb = memory.stackRead((short) (cpu.regGet16(Register.SP) + 1));
                short address = (short) ((msb << 8) | lsb);
                cpu.regSet16(Register.SP, (short) (cpu.regGet16(Register.SP) + 2));
                cpu.regSet16(Register.PC, address);
                cpu.regSet(Register.IR, (byte) 1);
                break;
            }
            case RESTART_UNCONDITIONAL: {
                byte lsb = values[0], msb = 0x00;
                short address = (short) ((msb << 8) | lsb);
                short sp = cpu.regGet16(Register.SP);
                memory.stackWrite(sp - 1, (byte) (cpu.regGet16(Register.PC) >> 8));
                memory.stackWrite(sp - 2, (byte) cpu.regGet16(Register.PC));
                cpu.regSet16(Register.SP, (short) (sp - 2));
                cpu.regSet16(Register.PC, address);
                break;
            }
            case HALT: {
                // TODO: Implement halt
                break;
            }
            case STOP: {
                // TODO: Implement stop
                break;
            }
            case DISABLE_INTERRUPTS: {
                cpu.regSet(Register.IR, (byte) 0);
                break;
            }
            case ENABLE_INTERRUPTS: {
                cpu.regSet(Register.IR, (byte) 1);
                break;
            }
            case NOP: {
                // Do nothing
                break;
            }
            default: {
                System.err.println("Unsupported instruction type: " + type);
            }
        }

    }

}
