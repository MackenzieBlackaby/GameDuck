package com.blackaby.Backend.GB.Memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.blackaby.Backend.GB.Misc.ROM;
import com.blackaby.Backend.GB.TestSupport.EmulatorTestUtils;

class CartridgeControllerTest {

    @Test
    void createSelectsExpectedMapperImplementation() {
        ROM rom = EmulatorTestUtils.CreateBlankRom(0x06, 4, 0x00, 0x00, "mapper.gb", "mapper");

        CartridgeController controller = CartridgeController.Create(rom);

        assertInstanceOf(Mbc2CartridgeController.class, controller);
    }

    @Test
    void romOnlyMapperSupportsUnbankedRam() {
        ROM rom = EmulatorTestUtils.CreateBlankRom(0x09, 2, 0x02, 0x00, "romonly.gb", "romonly");
        RomOnlyCartridgeController controller = new RomOnlyCartridgeController(rom);

        controller.Write(DuckAddresses.EXTERNAL_RAM_START, 0x5A);

        assertEquals(0x5A, controller.ReadRam(0));
    }

    @Test
    void mbc1SwitchesRomAndRamBanks() {
        ROM rom = EmulatorTestUtils.CreatePatternedRom(0x03, 64, 0x03, 0x00, "mbc1.gb", "mbc1");
        Mbc1CartridgeController controller = new Mbc1CartridgeController(rom);

        controller.Write(0x2000, 0x02);
        controller.Write(0x4000, 0x01);
        assertEquals(0x22, controller.ReadRom(0x4000));

        controller.Write(0x0000, 0x0A);
        controller.Write(0x6000, 0x01);
        controller.Write(0x4000, 0x02);
        controller.Write(DuckAddresses.EXTERNAL_RAM_START, 0xA2);
        controller.Write(0x4000, 0x01);
        controller.Write(DuckAddresses.EXTERNAL_RAM_START, 0xB1);
        controller.Write(0x4000, 0x02);
        assertEquals(0xA2, controller.ReadRam(0));
        controller.Write(0x4000, 0x01);
        assertEquals(0xB1, controller.ReadRam(0));
    }

    @Test
    void mbc2UsesFourBitRamAndKeepsBankZeroMappedToOne() {
        ROM rom = EmulatorTestUtils.CreatePatternedRom(0x06, 4, 0x00, 0x00, "mbc2.gb", "mbc2");
        Mbc2CartridgeController controller = new Mbc2CartridgeController(rom);

        controller.Write(0x2100, 0x00);
        assertEquals(0x01, controller.ReadRom(0x4000));

        controller.Write(0x0000, 0x0A);
        controller.Write(DuckAddresses.EXTERNAL_RAM_START + 0x0123, 0xBC);
        assertEquals(0xFC, controller.ReadRam(0x0123));
    }

    @Test
    void mbc3SwitchesRomBankAndIgnoresRtcRamWindowWrites() {
        ROM rom = EmulatorTestUtils.CreatePatternedRom(0x13, 8, 0x03, 0x00, "mbc3.gb", "mbc3");
        Mbc3CartridgeController controller = new Mbc3CartridgeController(rom, () -> 0L);

        controller.Write(0x2000, 0x00);
        assertEquals(0x01, controller.ReadRom(0x4000));
        controller.Write(0x2000, 0x03);
        assertEquals(0x03, controller.ReadRom(0x4000));

        controller.Write(0x0000, 0x0A);
        controller.Write(0x4000, 0x02);
        controller.Write(DuckAddresses.EXTERNAL_RAM_START, 0x55);
        assertEquals(0x55, controller.ReadRam(0));

        controller.Write(0x4000, 0x08);
        controller.Write(DuckAddresses.EXTERNAL_RAM_START, 0x99);
        assertEquals(0x00, controller.ReadRam(0));
        controller.Write(0x4000, 0x02);
        assertEquals(0x55, controller.ReadRam(0));
    }

    @Test
    void mbc3RtcLatchesAdvancesAndHonoursHaltWrites() {
        ROM rom = EmulatorTestUtils.CreatePatternedRom(0x10, 8, 0x03, 0x00, "mbc3_rtc.gb", "mbc3-rtc");
        long[] epochSeconds = { 0L };
        Mbc3CartridgeController controller = new Mbc3CartridgeController(rom, () -> epochSeconds[0]);

        controller.Write(0x0000, 0x0A);
        controller.Write(0x4000, 0x08);
        controller.Write(0x6000, 0x00);
        controller.Write(0x6000, 0x01);
        assertEquals(0x00, controller.ReadRam(0));

        epochSeconds[0] = 75L;
        controller.Write(0x6000, 0x00);
        controller.Write(0x6000, 0x01);
        assertEquals(0x0F, controller.ReadRam(0));
        controller.Write(0x4000, 0x09);
        assertEquals(0x01, controller.ReadRam(0));

        controller.Write(0x4000, 0x0C);
        controller.Write(DuckAddresses.EXTERNAL_RAM_START, 0x40);
        epochSeconds[0] = 600L;
        controller.Write(0x4000, 0x08);
        assertEquals(0x0F, controller.ReadRam(0));

        controller.Write(0x4000, 0x0C);
        controller.Write(DuckAddresses.EXTERNAL_RAM_START, 0x00);
        epochSeconds[0] = 601L;
        controller.Write(0x6000, 0x00);
        controller.Write(0x6000, 0x01);
        controller.Write(0x4000, 0x08);
        assertEquals(0x10, controller.ReadRam(0));
    }

    @Test
    void mbc3RtcSupplementalStateRoundTripRestoresClockAndFlags() {
        ROM rom = EmulatorTestUtils.CreatePatternedRom(0x10, 8, 0x03, 0x00, "mbc3_state.gb", "mbc3-state");
        long[] epochSeconds = { 0L };
        Mbc3CartridgeController controller = new Mbc3CartridgeController(rom, () -> epochSeconds[0]);
        controller.Write(0x0000, 0x0A);
        controller.Write(0x4000, 0x08);

        epochSeconds[0] = 3661L;
        controller.Write(0x6000, 0x00);
        controller.Write(0x6000, 0x01);
        controller.Write(0x4000, 0x0C);
        controller.Write(DuckAddresses.EXTERNAL_RAM_START, 0xC0);

        CartridgeController.MapperState state = controller.CaptureState();

        epochSeconds[0] = 9999L;
        Mbc3CartridgeController restored = new Mbc3CartridgeController(rom, () -> epochSeconds[0]);
        restored.RestoreState(state);
        restored.Write(0x0000, 0x0A);
        restored.Write(0x4000, 0x08);
        assertEquals(0x01, restored.ReadRam(0));
        restored.Write(0x4000, 0x09);
        assertEquals(0x01, restored.ReadRam(0));
        restored.Write(0x4000, 0x0C);
        assertEquals(0xC0, restored.ReadRam(0));
    }

    @Test
    void mbc3RtcOnlyCartridgeStillReportsSaveSupport() {
        ROM rom = EmulatorTestUtils.CreatePatternedRom(0x0F, 4, 0x00, 0x00, "mbc3_timer.gb", "mbc3-timer");
        Mbc3CartridgeController controller = new Mbc3CartridgeController(rom, () -> 0L);

        assertTrue(controller.SupportsSaveData());
    }

    @Test
    void mbc5UsesNineBitRomBankNumbersAndBankedRam() {
        ROM rom = EmulatorTestUtils.CreatePatternedRom(0x1B, 512, 0x03, 0x00, "mbc5.gb", "mbc5");
        Mbc5CartridgeController controller = new Mbc5CartridgeController(rom);

        controller.Write(0x2000, 0x01);
        controller.Write(0x3000, 0x01);
        assertEquals(0x01, controller.ReadRom(0x4000));
        assertEquals(0x01, controller.ReadRom(0x4001));

        controller.Write(0x0000, 0x0A);
        controller.Write(0x4000, 0x03);
        controller.Write(DuckAddresses.EXTERNAL_RAM_START, 0x33);
        controller.Write(0x4000, 0x04);
        controller.Write(DuckAddresses.EXTERNAL_RAM_START, 0x44);
        controller.Write(0x4000, 0x03);
        assertEquals(0x33, controller.ReadRam(0));
        controller.Write(0x4000, 0x04);
        assertEquals(0x44, controller.ReadRam(0));
    }

    @Test
    void mapperStateRoundTripRestoresRegistersAndRam() {
        ROM rom = EmulatorTestUtils.CreatePatternedRom(0x03, 64, 0x03, 0x00, "mbc1_state.gb", "mbc1-state");
        Mbc1CartridgeController controller = new Mbc1CartridgeController(rom);
        controller.Write(0x0000, 0x0A);
        controller.Write(0x2000, 0x03);
        controller.Write(0x4000, 0x02);
        controller.Write(0x6000, 0x01);
        controller.Write(DuckAddresses.EXTERNAL_RAM_START, 0x7C);

        CartridgeController.MapperState state = controller.CaptureState();

        Mbc1CartridgeController restored = new Mbc1CartridgeController(rom);
        restored.RestoreState(state);

        assertEquals(0x03, restored.ReadRom(0x4000));
        assertEquals(0x7C, restored.ReadRam(0));
    }
}
