package com.blackaby.Backend.GB.Memory;

import com.blackaby.Backend.GB.Misc.ROM;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.LongSupplier;

/**
 * Mapper for MBC3 cartridges.
 * <p>
 * ROM banking, RAM banking, and the real-time clock are implemented here.
 */
final class Mbc3CartridgeController extends CartridgeController {

    private static final int rtcRegisterSeconds = 0x08;
    private static final int rtcRegisterMinutes = 0x09;
    private static final int rtcRegisterHours = 0x0A;
    private static final int rtcRegisterDayLow = 0x0B;
    private static final int rtcRegisterDayHigh = 0x0C;

    private static final long secondsPerMinute = 60L;
    private static final long secondsPerHour = 60L * secondsPerMinute;
    private static final long secondsPerDay = 24L * secondsPerHour;
    private static final long maxRtcDays = 512L;
    private static final long maxRtcSeconds = maxRtcDays * secondsPerDay;

    private static final int rtcPersistenceMagic = 0x47525443;
    private static final int rtcPersistenceVersion = 1;

    private boolean ramEnabled;
    private int romBank = 1;
    private int ramBankOrRtcRegister;
    private final boolean hasRtc;
    private final LongSupplier epochSecondSupplier;

    private long rtcSeconds;
    private long lastRtcUpdateEpochSeconds;
    private boolean rtcHalted;
    private boolean rtcCarry;
    private boolean latchArmed;
    private boolean latchedRtcValid;
    private final int[] latchedRtcRegisters = new int[5];

    Mbc3CartridgeController(ROM rom) {
        this(rom, () -> System.currentTimeMillis() / 1000L);
    }

    Mbc3CartridgeController(ROM rom, LongSupplier epochSecondSupplier) {
        super(rom, rom.GetExternalRamSizeBytes());
        this.epochSecondSupplier = epochSecondSupplier == null ? () -> System.currentTimeMillis() / 1000L : epochSecondSupplier;
        hasRtc = rom != null && rom.HasRtc();
        lastRtcUpdateEpochSeconds = CurrentEpochSeconds();
    }

    @Override
    public int ReadRom(int address) {
        if (address < 0x4000) {
            return ReadRomBank(0, address & 0x3FFF);
        }
        return ReadRomBank(romBank, address - 0x4000);
    }

    @Override
    public int ReadRam(int address) {
        if (!ramEnabled) {
            return 0xFF;
        }
        if (IsRtcRegisterSelected()) {
            return ReadRtcRegister();
        }
        return ReadRamBank(ramBankOrRtcRegister, address & 0x1FFF);
    }

    @Override
    public void Write(int address, int value) {
        if (address <= 0x1FFF) {
            ramEnabled = (value & 0x0F) == 0x0A;
            return;
        }
        if (address <= 0x3FFF) {
            romBank = value & 0x7F;
            if (romBank == 0) {
                romBank = 1;
            }
            return;
        }
        if (address <= 0x5FFF) {
            ramBankOrRtcRegister = value & 0x0F;
            return;
        }
        if (address <= 0x7FFF) {
            HandleRtcLatchWrite(value);
            return;
        }

        if (address >= DuckAddresses.EXTERNAL_RAM_START && address <= DuckAddresses.EXTERNAL_RAM_END
                && ramEnabled && ramBankOrRtcRegister < 0x04) {
            WriteRamBank(ramBankOrRtcRegister, address - DuckAddresses.EXTERNAL_RAM_START, value);
            return;
        }

        if (address >= DuckAddresses.EXTERNAL_RAM_START && address <= DuckAddresses.EXTERNAL_RAM_END
                && ramEnabled && IsRtcRegisterSelected()) {
            WriteRtcRegister(value);
        }
    }

    @Override
    protected int[] CaptureRegisters() {
        return new int[] { ramEnabled ? 1 : 0, romBank, ramBankOrRtcRegister, latchArmed ? 1 : 0, latchedRtcValid ? 1 : 0,
                latchedRtcRegisters[0], latchedRtcRegisters[1], latchedRtcRegisters[2], latchedRtcRegisters[3],
                latchedRtcRegisters[4], (int) rtcSeconds, (int) (rtcSeconds >>> 32), rtcHalted ? 1 : 0,
                rtcCarry ? 1 : 0 };
    }

    @Override
    protected void RestoreRegisters(int[] registers) {
        if (registers == null || registers.length < 14) {
            throw new IllegalArgumentException("The MBC3 quick state is invalid.");
        }

        ramEnabled = registers[0] != 0;
        romBank = registers[1] & 0x7F;
        if (romBank == 0) {
            romBank = 1;
        }
        ramBankOrRtcRegister = registers[2] & 0x0F;
        latchArmed = registers[3] != 0;
        latchedRtcValid = registers[4] != 0;
        for (int index = 0; index < latchedRtcRegisters.length; index++) {
            latchedRtcRegisters[index] = registers[5 + index] & 0xFF;
        }
        rtcSeconds = (((long) registers[11]) << 32) | (registers[10] & 0xFFFFFFFFL);
        rtcSeconds = NormaliseRtcSeconds(rtcSeconds);
        rtcHalted = registers[12] != 0;
        rtcCarry = registers[13] != 0;
        lastRtcUpdateEpochSeconds = CurrentEpochSeconds();
    }

    @Override
    public byte[] ExportSupplementalSaveData() {
        if (!hasRtc) {
            return new byte[0];
        }

        SyncRtc();
        try {
            ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(outputBytes)) {
                output.writeInt(rtcPersistenceMagic);
                output.writeInt(rtcPersistenceVersion);
                output.writeLong(rtcSeconds);
                output.writeBoolean(rtcHalted);
                output.writeBoolean(rtcCarry);
                output.writeBoolean(latchArmed);
                output.writeBoolean(latchedRtcValid);
                for (int registerValue : latchedRtcRegisters) {
                    output.writeByte(registerValue & 0xFF);
                }
            }
            return outputBytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialise MBC3 RTC state.", exception);
        }
    }

    @Override
    public void LoadSupplementalSaveData(byte[] saveData) {
        rtcSeconds = 0L;
        rtcHalted = false;
        rtcCarry = false;
        latchArmed = false;
        latchedRtcValid = false;
        for (int index = 0; index < latchedRtcRegisters.length; index++) {
            latchedRtcRegisters[index] = 0;
        }
        lastRtcUpdateEpochSeconds = CurrentEpochSeconds();

        if (!hasRtc || saveData == null || saveData.length == 0) {
            return;
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(saveData))) {
            int magic = input.readInt();
            int version = input.readInt();
            if (magic != rtcPersistenceMagic || version != rtcPersistenceVersion) {
                throw new IllegalArgumentException("The MBC3 RTC save data is invalid.");
            }

            rtcSeconds = NormaliseRtcSeconds(input.readLong());
            rtcHalted = input.readBoolean();
            rtcCarry = input.readBoolean();
            latchArmed = input.readBoolean();
            latchedRtcValid = input.readBoolean();
            for (int index = 0; index < latchedRtcRegisters.length; index++) {
                latchedRtcRegisters[index] = input.readUnsignedByte();
            }
            lastRtcUpdateEpochSeconds = CurrentEpochSeconds();
        } catch (IOException exception) {
            throw new IllegalArgumentException("The MBC3 RTC save data could not be read.", exception);
        }
    }

    @Override
    protected boolean HasSupplementalSaveData() {
        return hasRtc;
    }

    private boolean IsRtcRegisterSelected() {
        return hasRtc && ramBankOrRtcRegister >= rtcRegisterSeconds && ramBankOrRtcRegister <= rtcRegisterDayHigh;
    }

    private int ReadRtcRegister() {
        if (latchedRtcValid) {
            return latchedRtcRegisters[ramBankOrRtcRegister - rtcRegisterSeconds] & 0xFF;
        }

        SyncRtc();
        return ReadCurrentRtcRegister(ramBankOrRtcRegister);
    }

    private void WriteRtcRegister(int value) {
        if (!hasRtc) {
            return;
        }

        SyncRtc();

        int seconds = CurrentSecondsField();
        int minutes = CurrentMinutesField();
        int hours = CurrentHoursField();
        int dayCounter = CurrentDayCounter();
        boolean nextHalt = rtcHalted;
        boolean nextCarry = rtcCarry;

        switch (ramBankOrRtcRegister) {
            case rtcRegisterSeconds -> seconds = Math.min(59, value & 0x3F);
            case rtcRegisterMinutes -> minutes = Math.min(59, value & 0x3F);
            case rtcRegisterHours -> hours = Math.min(23, value & 0x1F);
            case rtcRegisterDayLow -> dayCounter = (dayCounter & 0x100) | (value & 0xFF);
            case rtcRegisterDayHigh -> {
                dayCounter = ((value & 0x01) << 8) | (dayCounter & 0xFF);
                nextHalt = (value & 0x40) != 0;
                nextCarry = (value & 0x80) != 0;
            }
            default -> {
                return;
            }
        }

        rtcSeconds = (((long) dayCounter) * secondsPerDay)
                + (((long) hours) * secondsPerHour)
                + (((long) minutes) * secondsPerMinute)
                + seconds;
        rtcSeconds = NormaliseRtcSeconds(rtcSeconds);
        rtcCarry = nextCarry;
        rtcHalted = nextHalt;
        lastRtcUpdateEpochSeconds = CurrentEpochSeconds();

        if (!latchedRtcValid) {
            return;
        }
        LatchCurrentRtc();
    }

    private void HandleRtcLatchWrite(int value) {
        if (!hasRtc) {
            return;
        }

        boolean latchBitSet = (value & 0x01) != 0;
        if (latchArmed && latchBitSet) {
            SyncRtc();
            LatchCurrentRtc();
            latchedRtcValid = true;
        }
        latchArmed = !latchBitSet;
    }

    private void SyncRtc() {
        if (!hasRtc) {
            return;
        }

        long now = CurrentEpochSeconds();
        if (rtcHalted) {
            lastRtcUpdateEpochSeconds = now;
            return;
        }
        if (now <= lastRtcUpdateEpochSeconds) {
            return;
        }

        long elapsedSeconds = now - lastRtcUpdateEpochSeconds;
        long advancedSeconds = rtcSeconds + elapsedSeconds;
        if (advancedSeconds >= maxRtcSeconds) {
            rtcCarry = true;
            advancedSeconds %= maxRtcSeconds;
        }

        rtcSeconds = advancedSeconds;
        lastRtcUpdateEpochSeconds = now;
    }

    private void LatchCurrentRtc() {
        latchedRtcRegisters[0] = CurrentSecondsField();
        latchedRtcRegisters[1] = CurrentMinutesField();
        latchedRtcRegisters[2] = CurrentHoursField();
        latchedRtcRegisters[3] = CurrentDayCounter() & 0xFF;
        latchedRtcRegisters[4] = ((CurrentDayCounter() >>> 8) & 0x01)
                | (rtcHalted ? 0x40 : 0x00)
                | (rtcCarry ? 0x80 : 0x00);
    }

    private int ReadCurrentRtcRegister(int rtcRegister) {
        return switch (rtcRegister) {
            case rtcRegisterSeconds -> CurrentSecondsField();
            case rtcRegisterMinutes -> CurrentMinutesField();
            case rtcRegisterHours -> CurrentHoursField();
            case rtcRegisterDayLow -> CurrentDayCounter() & 0xFF;
            case rtcRegisterDayHigh -> ((CurrentDayCounter() >>> 8) & 0x01)
                    | (rtcHalted ? 0x40 : 0x00)
                    | (rtcCarry ? 0x80 : 0x00);
            default -> 0xFF;
        };
    }

    private int CurrentSecondsField() {
        return (int) (rtcSeconds % secondsPerMinute);
    }

    private int CurrentMinutesField() {
        return (int) ((rtcSeconds / secondsPerMinute) % 60L);
    }

    private int CurrentHoursField() {
        return (int) ((rtcSeconds / secondsPerHour) % 24L);
    }

    private int CurrentDayCounter() {
        return (int) ((rtcSeconds / secondsPerDay) % maxRtcDays);
    }

    private long CurrentEpochSeconds() {
        return Math.max(0L, epochSecondSupplier.getAsLong());
    }

    private long NormaliseRtcSeconds(long value) {
        return Math.floorMod(value, maxRtcSeconds);
    }
}

