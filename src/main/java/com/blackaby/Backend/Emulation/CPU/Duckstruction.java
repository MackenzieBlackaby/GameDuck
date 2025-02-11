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
        switch (type) {
            // Loads
            case REGISTER_REGISTER: {
                System.out.println("Executing REGISTER_REGISTER: values[0]=" + values[0] + ", values[1]=" + values[1]);
                cpu.regSet(Register.getRegFrom3Bit(values[0]), cpu.regGet(Register.getRegFrom3Bit(values[1])));
                break;
            }
            case IMMEDIATE_REGISTER: {
                System.out.println("Executing IMMEDIATE_REGISTER: values[0]=" + values[0] + ", values[1]=" + values[1]);
                cpu.regSet(Register.getRegFrom3Bit(values[0]), values[1]);
                break;
            }
            // Loads with memory
            case MEMORY_REGISTER_HL: {
                System.out.println("Executing MEMORY_REGISTER_HL: values[0]=" + values[0]);
                cpu.regSet(Register.getRegFrom3Bit(values[0]), memory.read(cpu.regGet16(Register.HL)));
                break;
            }
            case REGISTER_MEMORY_HL: {
                System.out.println("Executing REGISTER_MEMORY_HL: values[0]=" + values[0]);
                memory.write(cpu.regGet16(Register.HL), cpu.regGet(Register.getRegFrom3Bit(values[0])));
                break;
            }
            case IMMEDIATE_MEMORY_HL: {
                System.out.println("Executing IMMEDIATE_MEMORY_HL: values[0]=" + values[0]);
                memory.write(cpu.regGet16(Register.HL), values[0]);
                break;
            }
            case MEMORY_ACCUMULATOR_BC: {
                System.out.println("Executing MEMORY_ACCUMULATOR_BC");
                cpu.regSet(Register.A, memory.read(cpu.regGet16(Register.BC)));
                break;
            }
            case MEMORY_ACCUMULATOR_DE: {
                System.out.println("Executing MEMORY_ACCUMULATOR_DE");
                cpu.regSet(Register.A, memory.read(cpu.regGet16(Register.DE)));
                break;
            }
            case ACCUMULATOR_MEMORY_BC: {
                System.out.println("Executing ACCUMULATOR_MEMORY_BC");
                memory.write(cpu.regGet16(Register.BC), cpu.regGet(Register.A));
                break;
            }
            case ACCUMULATOR_MEMORY_DE: {
                System.out.println("Executing ACCUMULATOR_MEMORY_DE");
                memory.write(cpu.regGet16(Register.DE), cpu.regGet(Register.A));
                break;
            }
            case MEMORY_ACCUMULATOR_IMMEDIATE: {
                System.out.println(
                        "Executing MEMORY_ACCUMULATOR_IMMEDIATE: values[0]=" + values[0] + ", values[1]=" + values[1]);
                short address = (short) ((values[1] << 8) | (values[0] & 0xFF));
                cpu.regSet(Register.A, memory.read(address));
                break;
            }
            case ACCUMULATOR_MEMORY_IMMEDIATE: {
                System.out.println(
                        "Executing ACCUMULATOR_MEMORY_IMMEDIATE: values[0]=" + values[0] + ", values[1]=" + values[1]);
                int msb = values[1] & 0xFF;
                int lsb = values[0] & 0xFF;
                memory.write((msb << 8) | lsb, cpu.regGet(Register.A));
                break;
            }
            case MEMORY_ACCUMULATOR_MSB_0xFF_C: {
                System.out.println("Executing MEMORY_ACCUMULATOR_MSB_0xFF_C");
                cpu.regSet(Register.A, memory.read(0xFF00 + cpu.regGet(Register.C)));
                break;
            }
            case ACCUMULATOR_MEMORY_MSB_0xFF_C: {
                System.out.println("Executing ACCUMULATOR_MEMORY_MSB_0xFF_C");
                memory.write(0xFF00 + cpu.regGet(Register.C), cpu.regGet(Register.A));
                break;
            }
            case MEMORY_ACCUMULATOR_MSB_0xFF_IMMEDIATE: {
                System.out.println("Executing MEMORY_ACCUMULATOR_MSB_0xFF_IMMEDIATE: values[0]=" + values[0]);
                cpu.regSet(Register.A, memory.read(0xFF00 + values[0]));
                break;
            }
            case ACCUMULATOR_MEMORY_MSB_0xFF_IMMEDIATE: {
                System.out.println("Executing ACCUMULATOR_MEMORY_MSB_0xFF_IMMEDIATE: values[0]=" + values[0]);
                memory.write(0xFF00 + values[0], cpu.regGet(Register.A));
                break;
            }
            case MEMORY_ACCUMULATOR_HL_DECREMENT: {
                System.out.println("Executing MEMORY_ACCUMULATOR_HL_DECREMENT");
                cpu.regSet(Register.A, memory.read(cpu.regGet16(Register.HL)));
                cpu.regSet16(Register.HL, (short) (cpu.regGet16(Register.HL) - 1));
                break;
            }
            case ACCUMULATOR_MEMORY_HL_DECREMENT: {
                System.out.println("Executing ACCUMULATOR_MEMORY_HL_DECREMENT");
                memory.write(cpu.regGet16(Register.HL), cpu.regGet(Register.A));
                cpu.regSet16(Register.HL, (short) (cpu.regGet16(Register.HL) - 1));
                break;
            }
            case MEMORY_ACCUMULATOR_HL_INCREMENT: {
                System.out.println("Executing MEMORY_ACCUMULATOR_HL_INCREMENT");
                cpu.regSet(Register.A, memory.read(cpu.regGet16(Register.HL)));
                cpu.regSet16(Register.HL, (short) (cpu.regGet16(Register.HL) + 1));
                break;
            }
            case ACCUMULATOR_MEMORY_HL_INCREMENT: {
                System.out.println("Executing ACCUMULATOR_MEMORY_HL_INCREMENT");
                memory.write(cpu.regGet16(Register.HL), cpu.regGet(Register.A));
                cpu.regSet16(Register.HL, (short) (cpu.regGet16(Register.HL) + 1));
                break;
            }
            // Misc
            case IMMEDIATE_PAIR: {
                System.out.println("Executing IMMEDIATE_PAIR: values[0]=" + values[0] + ", values[1]=" + values[1]
                        + ", values[2]=" + values[2]);
                cpu.regSet16(Register.getRegFrom2Bit(values[0]), (short) ((values[2] << 8) | values[1]));
                break;
            }
            // Stack
            case SP_MEMORY_IMMEDIATE: {
                System.out
                        .println("Executing SP_MEMORY_IMMEDIATE: values[0]=" + values[0] + ", values[1]=" + values[1]);
                short sp = cpu.regGet16(Register.SP);
                short address = (short) ((values[1] << 8) | values[0]);
                byte lsb = (byte) (sp & 0xFF);
                byte msb = (byte) ((sp >> 8) & 0xFF);
                memory.write(address, lsb);
                memory.write((short) (address + 1), msb);
                break;
            }
            case HL_SP: {
                System.out.println("Executing HL_SP");
                cpu.regSet16(Register.SP, cpu.regGet16(Register.HL));
                break;
            }
            case STACKPUSH_RR: {
                System.out.println("Executing STACKPUSH_RR: values[0]=" + values[0]);
                int sp = cpu.regGet16(Register.SP);
                short value = cpu.regGet16(Register.getRegFrom2Bit(values[0]));
                sp = memory.stackPush(sp, (byte) ((value >> 8) & 0xFF)); // Push MSB
                sp = memory.stackPush(sp, (byte) (value & 0xFF)); // Push LSB
                cpu.regSet16(Register.SP, (short) sp);
                break;
            }
            case STACKPOP_RR: {
                System.out.println("Executing STACKPOP_RR: values[0]=" + values[0]);
                int sp = cpu.regGet16(Register.SP);
                sp = (sp + 1) & 0xFFFF;
                byte lsb = memory.stackPop(sp);
                sp = (sp + 1) & 0xFFFF;
                byte msb = memory.stackPop(sp);
                short value = (short) ((msb << 8) | (lsb & 0xFF));
                cpu.regSet16(Register.getRegFrom2Bit(values[0]), value);
                cpu.regSet16(Register.SP, (short) sp);
                break;
            }
            case SP_PLUS_IMMEDIATE8_HL: {
                System.out.println("Executing SP_PLUS_IMMEDIATE8_HL: values[0]=" + values[0]);
                short sp = cpu.regGet16(Register.SP);
                byte offset = values[0];
                short result = (short) (sp + offset);
                cpu.regSet16(Register.HL, result);
                cpu.deactivateFlags(Flag.Z, Flag.N);
                cpu.setFlag(Flag.H, ((sp & 0xF) + (offset & 0xF)) > 0xF);
                cpu.setFlag(Flag.C, ((sp & 0xFF) + (offset & 0xFF)) > 0xFF);
                break;
            }
            // Additions
            case ADD_REGISTER_ACCUMULATOR: {
                System.out.println("Executing ADD_REGISTER_ACCUMULATOR: values[0]=" + values[0]);
                cpu.alu.Add(Register.getRegFrom3Bit(values[0]), Register.A);
                break;
            }
            case ADD_MEMORY_HL: {
                System.out.println("Executing ADD_MEMORY_HL");
                cpu.alu.Add(memory.read(cpu.regGet16(Register.HL)), Register.A);
                break;
            }
            case ADD_IMMEDIATE: {
                System.out.println("Executing ADD_IMMEDIATE: values[0]=" + values[0]);
                cpu.alu.Add(values[0], Register.A);
                break;
            }
            // Additions with carry
            case ADC_REGISTER: {
                System.out.println("Executing ADC_REGISTER: values[0]=" + values[0]);
                cpu.alu.Add(cpu.getFlag(Flag.C), Register.A);
                cpu.alu.Add(Register.getRegFrom3Bit(values[0]), Register.A);
                break;
            }
            case ADC_MEMORY_HL: {
                System.out.println("Executing ADC_MEMORY_HL");
                cpu.alu.Add(cpu.getFlag(Flag.C), Register.A);
                cpu.alu.Add(memory.read(cpu.regGet16(Register.HL)), Register.A);
                break;
            }
            case ADC_IMMEDIATE: {
                System.out.println("Executing ADC_IMMEDIATE: values[0]=" + values[0]);
                cpu.alu.Add(cpu.getFlag(Flag.C), Register.A);
                cpu.alu.Add(values[0], Register.A);
                break;
            }
            // Subtractions
            case SUB_REGISTER: {
                System.out.println("Executing SUB_REGISTER: values[0]=" + values[0]);
                cpu.alu.Sub(Register.getRegFrom3Bit(values[0]), Register.A);
                break;
            }
            case SUB_MEMORY_HL: {
                System.out.println("Executing SUB_MEMORY_HL");
                cpu.alu.Sub(memory.read(cpu.regGet16(Register.HL)), Register.A);
                break;
            }
            case SUB_IMMEDIATE: {
                System.out.println("Executing SUB_IMMEDIATE: values[0]=" + values[0]);
                cpu.alu.Sub(values[0], Register.A);
                break;
            }
            // Subtractions with carry
            case SBC_REGISTER: {
                System.out.println("Executing SBC_REGISTER: values[0]=" + values[0]);
                cpu.alu.Sub(cpu.getFlag(Flag.C), Register.A);
                cpu.alu.Sub(Register.getRegFrom3Bit(values[0]), Register.A);
                break;
            }
            case SBC_MEMORY_HL: {
                System.out.println("Executing SBC_MEMORY_HL");
                cpu.alu.Sub(cpu.getFlag(Flag.C), Register.A);
                cpu.alu.Sub(memory.read(cpu.regGet16(Register.HL)), Register.A);
                break;
            }
            case SBC_IMMEDIATE: {
                System.out.println("Executing SBC_IMMEDIATE: values[0]=" + values[0]);
                cpu.alu.Sub(cpu.getFlag(Flag.C), Register.A);
                cpu.alu.Sub(values[0], Register.A);
                break;
            }
            // Comparison functions
            case CP_REGISTER: {
                System.out.println("Executing CP_REGISTER: values[0]=" + values[0]);
                byte from = cpu.regGet(Register.A);
                byte with = cpu.regGet(Register.getRegFrom3Bit(values[0]));
                short result = (short) (from - with);
                cpu.alu.updateSubFlags(result, from, with);
                break;
            }
            case CP_MEMORY_HL: {
                System.out.println("Executing CP_MEMORY_HL");
                byte from = cpu.regGet(Register.A);
                byte with = memory.read(cpu.regGet16(Register.HL));
                short result = (short) (from - with);
                cpu.alu.updateSubFlags(result, from, with);
                break;
            }
            case CP_IMMEDIATE: {
                System.out.println("Executing CP_IMMEDIATE: values[0]=" + values[0]);
                byte from = cpu.regGet(Register.A);
                byte with = values[0];
                short result = (short) (from - with);
                cpu.alu.updateSubFlags(result, from, with);
                break;
            }
            // IDU functions
            case INC_REGISTER: {
                System.out.println("Executing INC_REGISTER: values[0]=" + values[0]);
                Register reg = Register.getRegFrom3Bit(values[0]);
                cpu.regSet(reg, cpu.idu.calculate(cpu.regGet(reg), true));
                break;
            }
            case INC_MEMORY_HL: {
                System.out.println("Executing INC_MEMORY_HL");
                short address = cpu.regGet16(Register.HL);
                memory.write(address, cpu.idu.calculate(memory.read(address), true));
                break;
            }
            case DEC_REGISTER: {
                System.out.println("Executing DEC_REGISTER: values[0]=" + values[0]);
                Register reg = Register.getRegFrom3Bit(values[0]);
                cpu.regSet(reg, cpu.idu.calculate(cpu.regGet(reg), true));
                break;
            }
            case DEC_MEMORY_HL: {
                System.out.println("Executing DEC_MEMORY_HL");
                short address = cpu.regGet16(Register.HL);
                memory.write(address, cpu.idu.calculate(memory.read(address), true));
                break;
            }
            // Bitwise functions
            case AND_REGISTER: {
                System.out.println("Executing AND_REGISTER: values[0]=" + values[0]);
                byte result = (byte) ((byte) cpu.regGet(Register.A)
                        & (byte) cpu.regGet(Register.getRegFrom3Bit(values[0])));
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.setFlag(Flag.H, true);
                cpu.deactivateFlags(Flag.N, Flag.C);
                break;
            }
            case AND_MEMORY_HL: {
                System.out.println("Executing AND_MEMORY_HL");
                byte result = (byte) ((byte) cpu.regGet(Register.A) & (byte) memory.read(cpu.regGet16(Register.HL)));
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.setFlag(Flag.H, true);
                cpu.deactivateFlags(Flag.N, Flag.C);
                break;
            }
            case AND_IMMEDIATE: {
                System.out.println("Executing AND_IMMEDIATE: values[0]=" + values[0]);
                byte result = (byte) ((byte) cpu.regGet(Register.A) & (byte) values[0]);
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.setFlag(Flag.H, true);
                cpu.deactivateFlags(Flag.N, Flag.C);
                break;
            }
            case OR_REGISTER: {
                System.out.println("Executing OR_REGISTER: values[0]=" + values[0]);
                byte result = (byte) ((byte) cpu.regGet(Register.A)
                        | (byte) cpu.regGet(Register.getRegFrom3Bit(values[0])));
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.C);
                break;
            }
            case OR_MEMORY_HL: {
                System.out.println("Executing OR_MEMORY_HL");
                byte result = (byte) ((byte) cpu.regGet(Register.A) | (byte) memory.read(cpu.regGet16(Register.HL)));
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.C);
                break;
            }
            case OR_IMMEDIATE: {
                System.out.println("Executing OR_IMMEDIATE: values[0]=" + values[0]);
                byte result = (byte) ((byte) cpu.regGet(Register.A) | (byte) values[0]);
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.C);
                break;
            }
            case XOR_REGISTER: {
                System.out.println("Executing XOR_REGISTER: values[0]=" + values[0]);
                byte result = (byte) ((byte) cpu.regGet(Register.A)
                        ^ (byte) cpu.regGet(Register.getRegFrom3Bit(values[0])));
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.C);
                break;
            }
            case XOR_MEMORY_HL: {
                System.out.println("Executing XOR_MEMORY_HL");
                byte result = (byte) ((byte) cpu.regGet(Register.A) ^ (byte) memory.read(cpu.regGet16(Register.HL)));
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.C);
                break;
            }
            case XOR_IMMEDIATE: {
                System.out.println("Executing XOR_IMMEDIATE: values[0]=" + values[0]);
                byte result = (byte) ((byte) cpu.regGet(Register.A) ^ (byte) values[0]);
                cpu.regSet(Register.A, result);
                cpu.setFlag(Flag.Z, result == 0);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.C);
                break;
            }
            case CCF: {
                System.out.println("Executing CCF");
                cpu.deactivateFlags(Flag.N, Flag.H);
                cpu.setFlag(Flag.C, !cpu.getFlagBoolean(Flag.C));
                break;
            }
            case SCF: {
                System.out.println("Executing SCF");
                cpu.deactivateFlags(Flag.N, Flag.H);
                cpu.setFlag(Flag.C, true);
                break;
            }
            case DAA: {
                System.out.println("Executing DAA");
                int a = cpu.regGet(Register.A) & 0xFF;
                boolean n = cpu.getFlagBoolean(Flag.N);
                boolean c = cpu.getFlagBoolean(Flag.C);
                boolean h = cpu.getFlagBoolean(Flag.H);
                int correction = 0;
                if (n) {
                    if (c) {
                        correction -= 0x60;
                        a &= 0xFF;
                    }
                    if (h) {
                        correction -= 0x06;
                        a &= 0xFF;
                    }
                } else {
                    if (c || a > 0x99) {
                        correction += 0x60;
                        c = true;
                    }
                    if (h || (a & 0x0F) > 0x09) {
                        correction += 0x06;
                    }
                }
                a = (a + correction) & 0xFF;
                cpu.regSet(Register.A, (byte) a);
                cpu.setFlag(Flag.C, c);
                cpu.setFlag(Flag.Z, a == 0);
                cpu.deactivateFlags(Flag.H);
                break;
            }
            case CPL: {
                System.out.println("Executing CPL");
                cpu.regSet(Register.A, (byte) ~cpu.regGet(Register.A));
                cpu.setFlag(Flag.N, true);
                cpu.setFlag(Flag.H, true);
                break;
            }
            // 16-bit arithmetic instructions
            case INC_REGISTER_16: {
                System.out.println("Executing INC_REGISTER_16: values[0]=" + values[0]);
                Register reg = Register.getRegFrom2Bit(values[0]);
                cpu.regSet16(reg, (short) (cpu.regGet16(reg) + 1));
                break;
            }
            case DEC_REGISTER_16: {
                System.out.println("Executing DEC_REGISTER_16: values[0]=" + values[0]);
                Register reg = Register.getRegFrom2Bit(values[0]);
                cpu.regSet16(reg, (short) (cpu.regGet16(reg) - 1));
                break;
            }
            case ADD_PAIR_TO_HL: {
                System.out.println("Executing ADD_PAIR_TO_HL: values[0]=" + values[0]);
                short value = cpu.regGet16(Register.getRegFrom2Bit(values[0]));
                cpu.regSet16(Register.HL, (short) (cpu.regGet16(Register.HL) + value));
                cpu.deactivateFlags(Flag.N);
                cpu.setFlag(Flag.H, (value & 0xFFF) + (cpu.regGet16(Register.HL) & 0xFFF) > 0xFFF);
                cpu.setFlag(Flag.C, (value & 0xFFFF) + (cpu.regGet16(Register.HL) & 0xFFFF) > 0xFFFF);
            }
            case ADD_BYTE_TO_SP: {
                System.out.println("Executing ADD_BYTE_TO_SP: values[0]=" + values[0]);
                short sp = cpu.regGet16(Register.SP);
                byte offset = values[0];
                short result = (short) (sp + offset);
                cpu.regSet16(Register.SP, result);
                cpu.deactivateFlags(Flag.Z, Flag.N);
                cpu.setFlag(Flag.H, (sp & 0xF) + (offset & 0xF) > 0xF);
                cpu.setFlag(Flag.C, (sp & 0xFF) + (offset & 0xFF) > 0xFF);
                break;
            }
            case ROTATE_LEFT_CIRCLE_ACCUMULATOR: {
                System.out.println("Executing ROTATE_LEFT_CIRCLE_ACCUMULATOR");
                byte value = cpu.regGet(Register.A);
                boolean carry = (value & 0x80) != 0;
                value <<= 1;
                if (carry) {
                    value |= 0x01;
                }
                cpu.regSet(Register.A, value);
                cpu.setFlag(Flag.C, carry);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.Z);
                break;
            }
            case ROTATE_RIGHT_CIRCLE_ACCUMULATOR: {
                System.out.println("Executing ROTATE_RIGHT_CIRCLE_ACCUMULATOR");
                byte value = cpu.regGet(Register.A);
                boolean carry = (value & 0x01) != 0;
                value >>>= 1;
                if (carry) {
                    value |= 0x80;
                }
                cpu.regSet(Register.A, value);
                cpu.setFlag(Flag.C, carry);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.Z);
                break;
            }
            case ROTATE_LEFT_ACCUMULATOR: {
                System.out.println("Executing ROTATE_LEFT_ACCUMULATOR");
                byte value = cpu.regGet(Register.A);
                boolean carry = (value & 0x80) != 0;
                value <<= 1;
                cpu.regSet(Register.A, value);
                cpu.setFlag(Flag.C, carry);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.Z);
                break;
            }
            case ROTATE_RIGHT_ACCUMULATOR: {
                System.out.println("Executing ROTATE_RIGHT_ACCUMULATOR");
                byte value = cpu.regGet(Register.A);
                boolean carry = (value & 0x01) != 0;
                value >>>= 1;
                cpu.regSet(Register.A, value);
                cpu.setFlag(Flag.C, carry);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.Z);
                break;
            }
            case ROTATE_LEFT_CIRCLE_REGISTER: {
                System.out.println("Executing ROTATE_LEFT_CIRCLE_REGISTER");
                Register reg = Register.getRegFrom3Bit(values[0]);
                byte value = cpu.regGet(reg);
                boolean carry = (value & 0x80) != 0;
                value <<= 1;
                if (carry) {
                    value |= 0x01;
                }
                cpu.regSet(reg, value);
                cpu.setFlag(Flag.C, carry);
                cpu.setFlag(Flag.Z, value == 0);
                cpu.deactivateFlags(Flag.N, Flag.H);
                break;
            }
            case ROTATE_LEFT_CIRCLE_HL: {
                System.out.println("Executing ROTATE_LEFT_CIRCLE_HL");
                short address = cpu.regGet16(Register.HL);
                byte value = memory.read(address);
                boolean carry = (value & 0x80) != 0;
                value <<= 1;
                if (carry) {
                    value |= 0x01;
                }
                memory.write(address, value);
                cpu.setFlag(Flag.C, carry);
                cpu.setFlag(Flag.Z, value == 0);
                cpu.deactivateFlags(Flag.N, Flag.H);
                break;
            }
            case ROTATE_RIGHT_CIRCLE_REGISTER: {
                System.out.println("Executing ROTATE_RIGHT_CIRCLE_REGISTER");
                Register reg = Register.getRegFrom3Bit(values[0]);
                byte value = cpu.regGet(reg);
                boolean carry = (value & 0x01) != 0;
                value >>>= 1;
                if (carry) {
                    value |= 0x80;
                }
                cpu.regSet(reg, value);
                cpu.setFlag(Flag.C, carry);
                cpu.setFlag(Flag.Z, value == 0);
                cpu.deactivateFlags(Flag.N, Flag.H);
                break;
            }
            case ROTATE_RIGHT_CIRCLE_HL: {
                System.out.println("Executing ROTATE_RIGHT_CIRCLE_HL");
                short address = cpu.regGet16(Register.HL);
                byte value = memory.read(address);
                boolean carry = (value & 0x01) != 0;
                value >>>= 1;
                if (carry) {
                    value |= 0x80;
                }
                memory.write(address, value);
                cpu.setFlag(Flag.C, carry);
                cpu.setFlag(Flag.Z, value == 0);
                cpu.deactivateFlags(Flag.N, Flag.H);
                break;
            }
            case ROTATE_LEFT_REGISTER: {
                System.out.println("Executing ROTATE_LEFT_REGISTER");
                Register reg = Register.getRegFrom3Bit(values[0]);
                byte value = cpu.regGet(reg);
                boolean carry = (value & 0x80) != 0; // Check if the most significant bit is 1
                value <<= 1; // Shift left by one position
                if (carry) {
                    value |= 0x01; // Set the least significant bit if carry was set
                }
                cpu.regSet(reg, value);
                cpu.setFlag(Flag.C, carry);
                cpu.setFlag(Flag.Z, value == 0);
                cpu.deactivateFlags(Flag.N, Flag.H);
                break;
            }
            case ROTATE_LEFT_HL: {
                System.out.println("Executing ROTATE_LEFT_HL");
                short address = cpu.regGet16(Register.HL);
                byte value = memory.read(address);
                boolean carry = (value & 0x80) != 0;
                value <<= 1;
                if (carry) {
                    value |= 0x01;
                }
                memory.write(address, value);
                cpu.setFlag(Flag.C, carry);
                cpu.setFlag(Flag.Z, value == 0);
                cpu.deactivateFlags(Flag.N, Flag.H);
                break;
            }
            case ROTATE_RIGHT_REGISTER: {
                System.out.println("Executing ROTATE_RIGHT_REGISTER");
                Register reg = Register.getRegFrom3Bit(values[0]);
                byte value = cpu.regGet(reg);
                boolean carry = (value & 0x01) != 0;
                value >>>= 1; // Logical right shift
                if (carry) {
                    value |= 0x80; // Set the most significant bit if carry was set
                }
                cpu.regSet(reg, value);
                cpu.setFlag(Flag.C, carry);
                cpu.setFlag(Flag.Z, value == 0);
                cpu.deactivateFlags(Flag.N, Flag.H);
                break;
            }
            case ROTATE_RIGHT_HL: {
                System.out.println("Executing ROTATE_RIGHT_HL");
                short address = cpu.regGet16(Register.HL);
                byte value = memory.read(address);
                boolean carry = (value & 0x01) != 0;
                value >>>= 1;
                memory.write(address, value);
                cpu.setFlag(Flag.C, carry);
                cpu.setFlag(Flag.Z, value == 0);
                cpu.deactivateFlags(Flag.N, Flag.H);
                break;
            }
            case SHIFT_LEFT_ARITHMETIC_REGISTER: {
                System.out.println("Executing SHIFT_LEFT_ARITHMETIC_REGISTER: values[0]=" + values[0]);
                Register reg = Register.getRegFrom3Bit(values[0]);
                byte value = cpu.regGet(reg);
                boolean carry = (value & 0x80) != 0; // Check if the most significant bit is 1
                value <<= 1; // Shift left by one position
                cpu.regSet(reg, value);
                cpu.setFlag(Flag.C, carry); // Set carry flag if the shifted bit was 1
                cpu.setFlag(Flag.Z, value == 0); // Set zero flag if the result is zero
                cpu.deactivateFlags(Flag.N, Flag.H); // Clear N and H flags
                break;
            }
            case SHIFT_LEFT_ARITHMETIC_HL: {
                System.out.println("Executing SHIFT_LEFT_ARITHMETIC_HL");
                short address = cpu.regGet16(Register.HL);
                byte value = memory.read(address);
                boolean carry = (value & 0x80) != 0;
                value <<= 1;
                memory.write(address, value);
                cpu.setFlag(Flag.C, carry);
                cpu.setFlag(Flag.Z, value == 0);
                cpu.deactivateFlags(Flag.N, Flag.H);
                break;
            }
            case SHIFT_RIGHT_ARITHMETIC_REGISTER: {
                System.out.println("Executing SHIFT_RIGHT_ARITHMETIC_REGISTER: values[0]=" + values[0]);
                Register reg = Register.getRegFrom3Bit(values[0]);
                byte value = cpu.regGet(reg);
                boolean carry = (value & 0x01) != 0; // Check if the least significant bit is 1
                boolean bit7 = (value & 0x80) != 0; // Store the value of the most significant bit
                value >>= 1; // Shift right by one position
                if (bit7) {
                    value |= 0x80; // Restore the most significant bit
                }
                cpu.regSet(reg, value);
                cpu.setFlag(Flag.C, carry); // Set carry flag if the shifted bit was 1
                cpu.setFlag(Flag.Z, value == 0); // Set zero flag if the result is zero
                cpu.deactivateFlags(Flag.N, Flag.H); // Clear N and H flags
                break;
            }
            case SHIFT_RIGHT_ARITHMETIC_HL: {
                System.out.println("Executing SHIFT_RIGHT_ARITHMETIC_HL");
                short address = cpu.regGet16(Register.HL);
                byte value = memory.read(address);
                boolean carry = (value & 0x01) != 0;
                boolean bit7 = (value & 0x80) != 0;
                value >>= 1;
                if (bit7) {
                    value |= 0x80;
                }
                memory.write(address, value);
                cpu.setFlag(Flag.C, carry);
                cpu.setFlag(Flag.Z, value == 0);
                cpu.deactivateFlags(Flag.N, Flag.H);
                break;
            }
            case SWAP_NIBBLES_REGISTER: {
                System.out.println("Executing SWAP_NIBBLES_REGISTER: values[0]=" + values[0]);
                Register reg = Register.getRegFrom3Bit(values[0]);
                byte value = cpu.regGet(reg);
                byte upperNibble = (byte) ((value >> 4) & 0x0F);
                byte lowerNibble = (byte) (value & 0x0F);
                byte swappedValue = (byte) ((lowerNibble << 4) | upperNibble);
                cpu.regSet(reg, swappedValue);
                cpu.setFlag(Flag.Z, swappedValue == 0);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.C);
                break;
            }
            case SWAP_NIBBLES_HL: {
                System.out.println("Executing SWAP_NIBBLES_HL");
                short address = cpu.regGet16(Register.HL);
                byte value = memory.read(address);
                byte upperNibble = (byte) ((value >> 4) & 0x0F);
                byte lowerNibble = (byte) (value & 0x0F);
                byte swappedValue = (byte) ((lowerNibble << 4) | upperNibble);
                memory.write(address, swappedValue);
                cpu.setFlag(Flag.Z, swappedValue == 0);
                cpu.deactivateFlags(Flag.N, Flag.H, Flag.C);
                break;
            }
            case SHIFT_RIGHT_LOGICAL_REGISTER: {
                System.out.println("Executing SHIFT_RIGHT_LOGICAL_REGISTER");
                Register reg = Register.getRegFrom3Bit(values[0]);
                byte value = cpu.regGet(reg);
                boolean carry = (value & 0x01) != 0;
                value >>>= 1; // Logical shift right, fills with 0
                cpu.regSet(reg, value);
                cpu.setFlag(Flag.C, carry);
                cpu.setFlag(Flag.Z, value == 0);
                cpu.deactivateFlags(Flag.N, Flag.H);
                break;
            }
            case SHIFT_RIGHT_LOGICAL_HL: {
                System.out.println("Executing SHIFT_RIGHT_LOGICAL_HL");
                short address = cpu.regGet16(Register.HL);
                byte value = memory.read(address);
                boolean carry = (value & 0x01) != 0;
                value >>>= 1; // Logical shift right, fills with 0
                memory.write(address, value);
                cpu.setFlag(Flag.C, carry);
                cpu.setFlag(Flag.Z, value == 0);
                cpu.deactivateFlags(Flag.N, Flag.H);
                break;
            }
            case TEST_BIT_REGISTER: {
                System.out.println("Executing TEST_BIT_REGISTER");
                int bitPosition = values[0];
                Register reg = Register.getRegFrom3Bit(values[1]);
                byte value = cpu.regGet(reg);
                boolean isBitSet = (value & (1 << bitPosition)) != 0;
                cpu.setFlag(Flag.Z, !isBitSet); // Set Z flag if the bit is 0
                cpu.deactivateFlags(Flag.N); // N flag is always reset
                cpu.setFlag(Flag.H, true); // H flag is always set
                break;
            }
            case TEST_BIT_HL: {
                System.out.println("Executing TEST_BIT_HL");
                short address = cpu.regGet16(Register.HL);
                byte value = memory.read(address);
                int bitPosition = values[0];
                boolean isBitSet = (value & (1 << bitPosition)) != 0;
                cpu.setFlag(Flag.Z, !isBitSet); // Set Z flag if the bit is 0
                cpu.deactivateFlags(Flag.N); // N flag is always reset
                cpu.setFlag(Flag.H, true); // H flag is always set
                break;
            }
            case RESET_BIT_REGISTER: {
                System.out.println("Executing RESET_BIT_REGISTER");
                int bitPosition = values[0];
                Register reg = Register.getRegFrom3Bit(values[1]);
                byte value = cpu.regGet(reg);
                value &= ~(1 << bitPosition); // Clear the bit
                cpu.regSet(reg, value);
                break;
            }
            case RESET_BIT_HL: {
                System.out.println("Executing RESET_BIT_HL");
                short address = cpu.regGet16(Register.HL);
                byte value = memory.read(address);
                int bitPosition = values[0];
                value &= ~(1 << bitPosition); // Clear the bit
                memory.write(address, value);
                break;
            }
            case SET_BIT_REGISTER: {
                System.out.println("Executing SET_BIT_REGISTER");
                int bitPosition = values[0];
                Register reg = Register.getRegFrom3Bit(values[1]);
                byte value = cpu.regGet(reg);
                value |= (1 << bitPosition);
                cpu.regSet(reg, value);
                break;
            }
            case SET_BIT_HL: {
                System.out.println("Executing SET_BIT_HL");
                short address = cpu.regGet16(Register.HL);
                byte value = memory.read(address);
                int bitPosition = values[0]; // Assuming values[0] holds the bit position
                value |= (1 << bitPosition); // Set the bit
                memory.write(address, value);
                break;
            }
            // Control flow instructions
            case JUMP_UNCONDITIONAL: {
                System.out.println("Executing JUMP_UNCONDITIONAL: values[0]=" + values[0] + ", values[1]=" + values[1]);
                byte lsb = values[0], msb = values[1];
                short address = (short) ((msb << 8) | lsb);
                cpu.regSet16(Register.PC, address);
                break;
            }
            case JUMP_HL: {
                System.out.println("Executing JUMP_HL");
                cpu.regSet16(Register.PC, cpu.regGet16(Register.HL));
                break;
            }
            case JUMP_CONDITIONAL: {
                System.out.println("Executing JUMP_CONDITIONAL: values[0]=" + values[0] + ", values[1]=" + values[1]
                        + ", values[2]=" + values[2]);
                byte lsb = values[1], msb = values[2];
                short address = (short) ((msb << 8) | lsb);
                if (cpu.getFlagBoolean(Flag.getFlagFrom2Bit(values[0]))) {
                    cpu.regSet16(Register.PC, address);
                }
                break;
            }
            case JUMP_RELATIVE_UNCONDITIONAL: {
                System.out.println("Executing JUMP_RELATIVE_UNCONDITIONAL: values[0]=" + values[0]);
                byte offset = values[0];
                short address = (short) (cpu.regGet16(Register.PC) + offset);
                cpu.regSet16(Register.PC, address);
                break;
            }
            case JUMP_RELATIVE_CONDITIONAL: {
                System.out.println(
                        "Executing JUMP_RELATIVE_CONDITIONAL: values[0]=" + values[0] + ", values[1]=" + values[1]);
                byte offset = values[1];
                if (cpu.getFlagBoolean(Flag.getFlagFrom2Bit(values[0]))) {
                    short address = (short) (cpu.regGet16(Register.PC) + offset);
                    cpu.regSet16(Register.PC, address);
                }
                break;
            }
            case CALL_UNCONDITIONAL:
            case CALL_CONDITIONAL: {
                System.out.println("Executing CALL (Unconditional/Conditional)");
                if (type == InstructionType.CALL_CONDITIONAL && !cpu.getFlagBoolean(Flag.getFlagFrom2Bit(values[0]))) {
                    break;
                }
                short address = (short) ((values[1] << 8) | (values[0] & 0xFF));
                int sp = cpu.regGet16(Register.SP);
                sp = memory.stackPush(sp, (byte) ((cpu.regGet16(Register.PC) >> 8) & 0xFF));
                sp = memory.stackPush(sp, (byte) (cpu.regGet16(Register.PC) & 0xFF));
                cpu.regSet16(Register.SP, (short) sp);
                cpu.regSet16(Register.PC, address);
                break;
            }
            case RETURN_UNCONDITIONAL:
            case RETURN_CONDITIONAL: {
                System.out.println("Executing RETURN (Unconditional/Conditional)");
                if (type == InstructionType.RETURN_CONDITIONAL
                        && !cpu.getFlagBoolean(Flag.getFlagFrom2Bit(values[0]))) {
                    break;
                }
                int sp = cpu.regGet16(Register.SP);
                byte lsb = memory.stackPop(sp);
                byte msb = memory.stackPop(sp + 1);
                short address = (short) ((msb << 8) | (lsb & 0xFF));
                sp = (sp + 2) & 0xFFFF;
                cpu.regSet16(Register.SP, (short) sp);
                cpu.regSet16(Register.PC, address);
                break;
            }
            case RETURN_INTERRUPT: {
                System.out.println("Executing RETURN_INTERRUPT");
                byte lsb = memory.read(cpu.regGet16(Register.SP));
                byte msb = memory.read((short) (cpu.regGet16(Register.SP) + 1));
                short address = (short) ((msb << 8) | lsb);
                cpu.regSet16(Register.SP, (short) (cpu.regGet16(Register.SP) + 2));
                cpu.regSet16(Register.PC, address);
                cpu.regSet(Register.IR, (byte) 1);
                break;
            }
            case RESTART_UNCONDITIONAL: {
                System.out.println("Executing RESTART_UNCONDITIONAL: values[0]=" + values[0]);
                byte lsb = values[0], msb = 0x00;
                short address = (short) ((msb << 8) | lsb);
                short sp = cpu.regGet16(Register.SP);
                memory.write(sp, (byte) (cpu.regGet16(Register.PC) >> 8));
                memory.write(sp - 1, (byte) cpu.regGet16(Register.PC));
                cpu.regSet16(Register.SP, (short) (sp - 2));
                cpu.regSet16(Register.PC, address);
                break;
            }
            case HALT: {
                System.out.println("Executing HALT");
                System.out.println("HALT Unimplemented!!!!!!!!!!!!!!!");
                // TODO: Implement halt
                break;
            }
            case STOP: {
                System.out.println("Executing STOP");
                System.out.println("STOP Unimplemented!!!!!!!!!!!!!!!");
                // TODO: Implement stop
                break;
            }
            case DISABLE_INTERRUPTS: {
                System.out.println("Executing DISABLE_INTERRUPTS");
                cpu.regSet(Register.IR, (byte) 0);
                break;
            }
            case ENABLE_INTERRUPTS: {
                System.out.println("Executing ENABLE_INTERRUPTS");
                cpu.regSet(Register.IR, (byte) 1);
                break;
            }
            case NOP: {
                System.out.println("Executing NOP");
                // Do nothing
                break;
            }
            default: {
                System.err.println("Unsupported instruction type: " + type);
            }
        }

    }

}
