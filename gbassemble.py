import sys

registerOpcode3bit = {"b": 0, "c": 1, "d": 2, "e": 3, "h": 4, "l": 5, "(hl)": 6, "a": 7}

registerOpcode2bit = {"bc": 0, "de": 1, "hl": 2, "sp": 3}

opcodesWithOperands = []


def getOpcode(line):
    return None


def hasOperands(opcode):
    return opcode in opcodesWithOperands


def getOperands(line):
    return None


def assemble(lines):
    machineCode = []

    for line in lines:
        opcode = getOpcode(line)
        operands = []
        if opcode == None:
            print("Error: Invalid instruction")
            sys.exit(1)
        if hasOperands(opcode):
            operands = getOperands(line)
        machineCode.append(opcode + operands)

    return machineCode


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python assembler.py <inputFile> <output_file>")
        sys.exit(1)

    inputFile = sys.argv[1]
    outputFile = sys.argv[2]

    with open(inputFile, "r") as f:
        lines = f.readlines()

    for i in range(len(lines)):
        lines[i] = lines[i].strip().lower()

    machineCode = assemble(lines)

    with open(outputFile, "w") as f:
        for code in machineCode:
            f.write(code)

    print("Assembly complete. Output written to", outputFile)
