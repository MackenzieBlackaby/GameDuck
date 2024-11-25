package com.blackaby.Backend.Emulation.CPU.Instructions;

public class DebugConsole implements Duckstruction {
    /**
     * This method executes a simple debug console instruction
     * It prints a message to the console
     */
    @Override
    public void execute() {
        System.out.println("Debug Duckstruction :)");
    }
}
