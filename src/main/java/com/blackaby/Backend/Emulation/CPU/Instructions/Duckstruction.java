package com.blackaby.Backend.Emulation.CPU.Instructions;

import com.blackaby.Backend.Emulation.Memory.DuckMemory;
import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.DuckCPU.Register;
import com.blackaby.Backend.Emulation.CPU.Instructions.InstructionTypeManager.InstructionType;

/**
 * 
 */
public class Duckstruction {

    protected DuckCPU cpu;
    protected DuckMemory memory;

    private byte[] values = { 0, 0, 0 };
    private InstructionType type;

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
     * This method executes the instruction
     * It loads a value into a register
     */
    public void execute() {
        System.out.println("Executing instruction: " + type);
        switch (type) {
            case REGISTER_REGISTER:
                System.out.println("Setting register " + Register.getRegFrom3Bit(values[1]) + " to value of register "
                        + Register.getRegFrom3Bit(values[0]));
                cpu.regSet(Register.getRegFrom3Bit(values[1]), cpu.regGet(Register.getRegFrom3Bit(values[0])));
                break;
            case IMMEDIATE_REGISTER:
                cpu.regSet(Register.getRegFrom3Bit(values[0]), values[1]);
                break;
            case MEMORY_REGISTER_HL:
                cpu.regSet(Register.getRegFrom3Bit(values[0]), memory.read(cpu.regGet16(Register.HL)));
                break;
            case REGISTER_MEMORY_HL:
                memory.write(cpu.regGet16(Register.HL), cpu.regGet(Register.getRegFrom3Bit(values[0])));
                break;
            case IMMEDIATE_MEMORY_HL:
                memory.write(cpu.regGet16(Register.HL), values[0]);
                break;
            case MEMORY_ACCUMULATOR_BC:
                cpu.regSet(Register.A, memory.read(cpu.regGet16(Register.BC)));
                break;
            case MEMORY_ACCUMULATOR_DE:
                cpu.regSet(Register.A, memory.read(cpu.regGet16(Register.DE)));
                break;
            case ACCUMULATOR_MEMORY_BC:
                memory.write(cpu.regGet16(Register.BC), cpu.regGet(Register.A));
                break;
            case ACCUMULATOR_MEMORY_DE:
                memory.write(cpu.regGet16(Register.DE), cpu.regGet(Register.A));
                break;
            case MEMORY_ACCUMULATOR_IMMEDIATE:
                // Values[0] is LSB
                cpu.regSet(Register.A, memory.read((values[1] << 8) | values[0]));
                break;
            case ACCUMULATOR_MEMORY_IMMEDIATE:
                // Values[0] is LSB
                memory.write((values[1] << 8) | values[0], cpu.regGet(Register.A));
                break;
            case MEMORY_ACCUMULATOR_MSB_0xFF_C:
                cpu.regSet(Register.A, memory.read(0xFF00 + cpu.regGet(Register.C)));
                break;
            case ACCUMULATOR_MEMORY_MSB_0xFF_C:
                memory.write(0xFF00 + cpu.regGet(Register.C), cpu.regGet(Register.A));
                break;
            case MEMORY_ACCUMULATOR_MSB_0xFF_IMMEDIATE:
                cpu.regSet(Register.A, memory.read(0xFF00 + values[0]));
                break;
            case ACCUMULATOR_MEMORY_MSB_0xFF_IMMEDIATE:
                memory.write(0xFF00 + values[0], cpu.regGet(Register.A));
                break;
            case MEMORY_ACCUMULATOR_HL_DECREMENT:
                cpu.regSet(Register.A, memory.read(cpu.regGet16(Register.HL)));
                cpu.regSet16(Register.HL, (short) (cpu.regGet16(Register.HL) - 1));
                break;
            case ACCUMULATOR_MEMORY_HL_DECREMENT:
                memory.write(cpu.regGet16(Register.HL), cpu.regGet(Register.A));
                cpu.regSet16(Register.HL, (short) (cpu.regGet16(Register.HL) - 1));
                break;
            case MEMORY_ACCUMULATOR_HL_INCREMENT:
                cpu.regSet(Register.A, memory.read(cpu.regGet16(Register.HL)));
                cpu.regSet16(Register.HL, (short) (cpu.regGet16(Register.HL) + 1));
                break;
            case ACCUMULATOR_MEMORY_HL_INCREMENT:
                memory.write(cpu.regGet16(Register.HL), cpu.regGet(Register.A));
                cpu.regSet16(Register.HL, (short) (cpu.regGet16(Register.HL) + 1));
                break;
            case IMMEDIATE_PAIR:
                cpu.regSet16(Register.getRegFrom2Bit(values[0]), (short) ((values[2] << 8) | values[1]));
                break;
            case SP_MEMORY_IMMEDIATE:
                int nn = (values[1] << 8) | values[0];
                short sp = cpu.regGet(Register.SP);
                memory.write(nn, (byte) (sp >> 8));
                memory.write(nn + 1, (byte) sp);
                break;
            case HL_SP:
                cpu.regSet16(Register.SP, cpu.regGet16(Register.HL));
                break;
            case STACKPUSH_RR:
                short currentSPValue = cpu.regGet16(Register.SP);
                System.out.println("Current SP value: " + currentSPValue);
                short valToWrite = cpu.regGet16(Register.getRegFrom2Bit(values[0]));
                System.out
                        .println("Got from register: " + Register.getRegFrom2Bit(values[0]) + " value: " + valToWrite);
                memory.stackWrite(currentSPValue - 1, (byte) (valToWrite >> 8));
                memory.stackWrite(currentSPValue - 2, (byte) valToWrite);
                cpu.regSet16(Register.SP, (byte) (currentSPValue - 2));
                break;
            case STACKPOP_RR:
                byte lsb = memory.stackRead(cpu.regGet16(Register.SP));
                byte msb = memory.stackRead((byte) (cpu.regGet16(Register.SP) + 1));
                short value = (short) ((msb << 8) | lsb);
                cpu.regSet16(Register.getRegFrom2Bit(values[0]), value);
                cpu.regSet16(Register.SP, (byte) (cpu.regGet16(Register.SP) + 2));
                break;
            case SP_PLUS_IMMEDIATE8_HL:
                short spValue = cpu.regGet16(Register.SP);
                short offset = (short) (values[0]);
                short result = (short) (spValue + offset);
                cpu.regSet16(Register.HL, result);
                // TODO: Set the flags from this
                break;
            default:
                System.err.println("Unsupported load type: " + type);
        }

    }

}
