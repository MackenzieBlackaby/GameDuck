# Game Boy Pseudocode Library

## Overview

This library provides a collection of pseudocode functions and utilities for emulating Game Boy and Game Boy Colour hardware. As I was developing this emulator, I found it hard to discover a clean and complete documentation of the Game Boy's hardware and instruction set. Things were either half-complete or imprecise, or written in a way that was difficult to understand. This library serves as a reference for the various components of the Game Boy's hardware and instruction set. It is not meant to be a complete technical documentation, but rather a collection of useful functions and utilities that give the main idea of how the Game Boy works.

## Whats in here?

- **CPU Instructions**: A collection of pseudocode functions that represent the various instructions of the Game Boy's Sharp LR35902 CPU. These functions are designed to be easy to understand and follow the structure of the actual instructions as closely as possible.

- **Memory Management**: Functions and utilities for managing the Game Boy's memory, including reading and writing to memory, handling memory-mapped I/O, and managing the various memory banks.

- **Graphics and Display**: Pseudocode functions for handling the PPU (Pixel Processing Unit), including rendering sprites, managing the background and window layers, and handling the various display modes.

- **Audio**: Functions and utilities for handling the Game Boy's audio hardware, including sound generation and mixing.

- **Timing and Interrupts**: Pseudocode functions for managing the Game Boy's timing and interrupt system, including handling the various timers and managing the interrupt flags.

- **Peripherals**: Functions and utilities for handling the various peripherals of the Game Boy, such as the joypad input and serial communication.

- **Main Loop** and Emulation Flow: A high-level overview of the main loop and emulation flow, including how the various components interact with each other.