import sys

def binary_to_hex(binary_str):
    try:
        decimal_value = int(binary_str, 2)
        hex_value = hex(decimal_value)
        return hex_value
    except ValueError:
        return "Invalid binary number"

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python hexconverter.py <binary_value>")
    else:
        binary_value = sys.argv[1]
        hex_value = binary_to_hex(binary_value)
        print(f"Hexadecimal value: {hex_value}")