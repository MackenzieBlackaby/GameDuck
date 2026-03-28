package com.blackaby.Backend.GB.Peripherals;

import com.blackaby.Backend.GB.GBButton;
import com.blackaby.Backend.GB.CPU.DuckCPU;

import java.util.EnumMap;

/**
 * Emulates the DMG joypad register at {@code FF00}.
 */
public class DuckJoypad {

    public record JoypadState(int selectBits, int pressedMask) implements java.io.Serializable {
    }

    private final EnumMap<GBButton, Boolean> pressedStates = new EnumMap<>(GBButton.class);
    private DuckCPU cpu;
    private int selectBits = 0x30;

    /**
     * Creates a joypad with no CPU attached yet.
     */
    public DuckJoypad() {
        Reset();
    }

    /**
     * Creates a joypad bound to a CPU.
     *
     * @param cpu CPU instance for interrupt requests
     */
    public DuckJoypad(DuckCPU cpu) {
        this();
        this.cpu = cpu;
    }

    /**
     * Attaches the CPU used for joypad interrupt requests.
     *
     * @param cpu CPU instance
     */
    public synchronized void SetCpu(DuckCPU cpu) {
        this.cpu = cpu;
    }

    /**
     * Restores the joypad to its power-on state.
     */
    public synchronized void Reset() {
        selectBits = 0x30;
        for (GBButton button : GBButton.values()) {
            pressedStates.put(button, false);
        }
    }

    /**
     * Returns the current value visible through {@code FF00}.
     *
     * @return joypad register value
     */
    public synchronized int ReadRegister() {
        return ComputeRegisterValue();
    }

    /**
     * Writes the selection bits in {@code FF00}.
     *
     * @param value new register value
     */
    public synchronized void WriteRegister(int value) {
        UpdateState(selectBits, value & 0x30, null, null);
    }

    /**
     * Updates the pressed state for a button.
     *
     * @param button joypad button to update
     * @param pressed whether the button is pressed
     */
    public synchronized void SetButtonPressed(GBButton button, boolean pressed) {
        UpdateState(selectBits, selectBits, button, pressed);
    }

    /**
     * Captures the current row select bits and pressed button state.
     *
     * @return joypad state snapshot
     */
    public synchronized JoypadState CaptureState() {
        int pressedMask = 0;
        for (GBButton button : GBButton.values()) {
            if (pressedStates.getOrDefault(button, false)) {
                pressedMask |= (1 << button.ordinal());
            }
        }
        return new JoypadState(selectBits, pressedMask);
    }

    /**
     * Restores the current row select bits and pressed button state.
     *
     * @param state joypad snapshot to restore
     */
    public synchronized void RestoreState(JoypadState state) {
        if (state == null) {
            throw new IllegalArgumentException("A joypad quick state is required.");
        }

        selectBits = state.selectBits() & 0x30;
        int pressedMask = state.pressedMask();
        for (GBButton button : GBButton.values()) {
            pressedStates.put(button, (pressedMask & (1 << button.ordinal())) != 0);
        }
    }

    private void UpdateState(int oldSelectBits, int newSelectBits, GBButton button, Boolean pressed) {
        int previousValue = ComputeRegisterValue();

        selectBits = newSelectBits & 0x30;
        if (button != null && pressed != null) {
            pressedStates.put(button, pressed);
        }

        int currentValue = ComputeRegisterValue();
        int fallingEdges = (previousValue & ~currentValue) & 0x0F;
        if (fallingEdges != 0 && cpu != null) {
            cpu.RequestInterrupt(DuckCPU.Interrupt.JOYPAD);
        }
    }

    private int ComputeRegisterValue() {
        int lowNibble = 0x0F;
        boolean directionsSelected = (selectBits & 0x10) == 0;
        boolean actionsSelected = (selectBits & 0x20) == 0;

        if (directionsSelected) {
            lowNibble &= ApplyGroupState(GBButton.Group.DIRECTIONS);
        }
        if (actionsSelected) {
            lowNibble &= ApplyGroupState(GBButton.Group.ACTIONS);
        }

        return 0xC0 | (selectBits & 0x30) | lowNibble;
    }

    private int ApplyGroupState(GBButton.Group group) {
        int nibble = 0x0F;
        for (GBButton button : GBButton.values()) {
            if (button.GetGroup() == group && pressedStates.getOrDefault(button, false)) {
                nibble &= ~button.GetMask();
            }
        }
        return nibble;
    }

}

