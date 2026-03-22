package com.blackaby.Backend.Emulation.CPU;

@FunctionalInterface
public interface OpcodeHandler {
    int Execute();

    @Deprecated
    default int execute() {
        return Execute();
    }
}
