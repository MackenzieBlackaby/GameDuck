package com.blackaby.Backend.GB;

import com.blackaby.Backend.GB.CPU.GBProcessor;
import com.blackaby.Backend.GB.CPU.GBInstructionLogic;
import com.blackaby.Backend.GB.Graphics.GBPPU;
import com.blackaby.Backend.GB.Memory.GBMemAddresses;
import com.blackaby.Backend.GB.Memory.GBCheatEngine;
import com.blackaby.Backend.GB.Memory.GBMemory;
import com.blackaby.Backend.GB.Misc.GBRom;
import com.blackaby.Backend.GB.Misc.GBConstants;
import com.blackaby.Backend.GB.Peripherals.GBAudioProcessingUnit;
import com.blackaby.Backend.GB.Peripherals.GBGamepad;
import com.blackaby.Backend.GB.Peripherals.GBTimerSet;
import com.blackaby.Backend.Platform.EmulatorGame;
import com.blackaby.Backend.Platform.EmulatorCheat;
import com.blackaby.Backend.Platform.EmulatorHost;
import com.blackaby.Backend.Platform.EmulatorMedia;
import com.blackaby.Backend.Platform.EmulatorProfile;
import com.blackaby.Backend.Platform.EmulatorRuntime;
import com.blackaby.Backend.Platform.EmulatorStateSlot;
import com.blackaby.Frontend.DebugLogger;
import com.blackaby.Frontend.DuckDisplay;
import com.blackaby.Backend.Helpers.SaveFileManager;
import com.blackaby.Backend.Helpers.ManagedGameRegistry;
import com.blackaby.Backend.Helpers.CheatStore;
import com.blackaby.Backend.Helpers.GameLibraryStore;
import com.blackaby.Backend.Helpers.QuickStateManager;
import com.blackaby.Misc.BootRomManager;
import com.blackaby.Misc.NonGbcColourMode;
import com.blackaby.Misc.Settings;
import com.blackaby.Misc.UiText;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * Coordinates the main emulator runtime.
 * <p>
 * The class owns the active hardware blocks, loads cartridges, applies the DMG
 * boot state when the boot ROM is skipped, and runs the timed execution loop
 * used by the desktop front end.
 */
public class GBRuntime implements Runnable, EmulatorRuntime {

    private static final double maxTimeAccumulator = 50_000_000.0;
    private static final int minCyclesPerRunSlice = 2_048;
    private static final int maxCyclesPerRunSlice = 17_556;
    private static final long coarseParkThresholdNanos = 2_000_000L;
    private static final long fineSpinThresholdNanos = 250_000L;
    private static final double minRunSliceNanos = minCyclesPerRunSlice * GBConstants.nanosecondsPerCycle;

    private GBProcessor cpu;
    private GBMemory memory;
    private GBTimerSet timer;
    private GBPPU ppu;
    private volatile GBGamepad joypad;
    private GBAudioProcessingUnit apu;
    private final DuckDisplay display;
    private GBRom rom;
    private final EmulatorHost host;
    private final EmulatorProfile profile;
    private Thread emulationThread;
    private volatile boolean running;
    private volatile boolean paused;
    private final String defaultRomName = "NO ROM LOADED";
    private String romName = defaultRomName;
    private final Object stateLock = new Object();
    private final GBCheatEngine cheatEngine = new GBCheatEngine();

    /**
     * Creates an emulator controller for the main window and display.
     *
     * @param window  owning main window
     * @param display display surface for rendered frames
     */
    public GBRuntime(EmulatorHost window, DuckDisplay display, EmulatorProfile profile) {
        this.display = display;
        this.host = window;
        this.profile = profile;
    }

    @Override
    public EmulatorProfile Profile() {
        return profile;
    }

    /**
     * Loads a ROM and starts the execution thread.
     *
     * @param romFile path to the ROM file
     */
    @Override
    public void StartEmulation(String romFile) {
        StartEmulation(new GBRom(romFile));
    }

    @Override
    public void StartEmulation(EmulatorMedia media) {
        if (media instanceof GBRom loadedRom) {
            StartEmulation(loadedRom);
            return;
        }

        StartEmulation(GBRom.FromBytes(
                media.sourcePath(),
                media.programBytes(),
                media.displayName(),
                media.patchNames(),
                media.patchSourcePaths()));
    }

    /**
     * Loads a ROM image and starts the execution thread.
     *
     * @param rom ROM image to start
     */
    public void StartEmulation(GBRom rom) {
        if (running) {
            StopEmulation();
        }

        DebugLogger.ClearSerialOutput();
        running = true;
        paused = false;
        this.rom = rom;
        romName = rom.GetName();

        memory = new GBMemory();
        apu = new GBAudioProcessingUnit(memory);
        cpu = new GBProcessor(memory, this, this.rom);
        joypad = new GBGamepad(cpu);
        timer = new GBTimerSet(cpu, memory);
        ppu = new GBPPU(cpu, memory, display);

        memory.SetTimer(timer);
        memory.SetCpu(cpu);
        memory.SetJoypad(joypad);
        memory.SetApu(apu);
        memory.SetCheatEngine(cheatEngine);
        memory.LoadRom(this.rom, ShouldUseCgbHardware(this.rom));
        cheatEngine.SetCheats(CheatStore.GetCheats(this.rom));
        SaveFileManager.LoadSaveBundle(this.rom).ifPresent(saveData -> {
            memory.LoadSaveData(saveData.primaryData());
            memory.LoadSupplementalSaveData(saveData.supplementalData());
        });
        ManagedGameRegistry.RememberGame(this.rom);
        try {
            GameLibraryStore.RememberGame(this.rom);
        } catch (IllegalStateException exception) {
            exception.printStackTrace();
        }

        GBInstructionLogic.Initialise(cpu, memory);
        host.SetSubtitle(romName);
        host.SetLoadedGame(this.rom, false);
        host.LoadGameArt(this.rom);

        emulationThread = new Thread(this);
        emulationThread.start();
    }

    /**
     * Toggles the paused state of the emulator.
     */
    public void PauseEmulation() {
        paused = !paused;
        if (paused) {
            host.SetSubtitle(romName, ": Paused");
        } else {
            host.SetSubtitle(romName);
        }
    }

    /**
     * Restarts the currently loaded ROM from power-on state.
     */
    public void RestartEmulation() {
        if (rom == null) {
            return;
        }
        StartEmulation(rom);
    }

    /**
     * Stops the execution thread and clears the active hardware state.
     */
    public void StopEmulation() {
        running = false;
        paused = false;

        try {
            if (emulationThread != null) {
                emulationThread.join(1000);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        romName = defaultRomName;
        host.SetSubtitle(romName);
        host.SetLoadedGame(null);
        host.ClearGameArt();
        display.clear();

        if (rom != null && memory != null && memory.HasSaveData()) {
            SaveFileManager.Save(rom, memory.ExportSaveData(), memory.ExportSupplementalSaveData());
        }

        if (apu != null) {
            apu.Shutdown();
        }

        cheatEngine.SetCheats(List.of());
        cpu = null;
        memory = null;
        timer = null;
        ppu = null;
        joypad = null;
        apu = null;
        rom = null;
    }

    @Override
    public void run() {
        InitialiseBootState();
        ApplyLiveCheats();
        long previousTime = System.nanoTime();
        double timeAccumulator = 0.0;

        while (running) {
            if (paused) {
                try {
                    Thread.sleep(100);
                    previousTime = System.nanoTime();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                continue;
            }

            long currentTime = System.nanoTime();
            double delta = currentTime - previousTime;
            previousTime = currentTime;
            timeAccumulator += delta;

            timeAccumulator = Math.min(timeAccumulator, maxTimeAccumulator);
            int availableCycles = (int) (timeAccumulator / GBConstants.nanosecondsPerCycle);
            if (availableCycles < minCyclesPerRunSlice) {
                WaitForRunSlice(timeAccumulator);
                continue;
            }

            int cyclesToRun = Math.min(availableCycles, maxCyclesPerRunSlice);
            int executedCycles = 0;
            synchronized (stateLock) {
                ApplyLiveCheats();
                while (running && !paused && cyclesToRun > 0) {
                    if (!cpu.IsHalted()) {
                        cpu.Fetch();
                        cpu.Decode();
                    }

                    int tCycles = cpu.Execute();
                    int masterCycles = StepHardware(tCycles);

                    executedCycles += masterCycles;
                    cyclesToRun -= masterCycles;
                }
                ApplyLiveCheats();
            }

            timeAccumulator -= executedCycles * GBConstants.nanosecondsPerCycle;
        }
    }

    private void WaitForRunSlice(double timeAccumulator) {
        long remainingNanos = (long) Math.ceil(Math.max(0.0, minRunSliceNanos - timeAccumulator));
        if (remainingNanos <= 0L) {
            return;
        }

        if (remainingNanos > coarseParkThresholdNanos) {
            LockSupport.parkNanos(remainingNanos - fineSpinThresholdNanos);
            return;
        }

        if (remainingNanos > fineSpinThresholdNanos) {
            Thread.yield();
            return;
        }

        Thread.onSpinWait();
    }

    /**
     * Updates the joypad state from host input.
     *
     * @param button  joypad button to update
     * @param pressed whether the button is pressed
     */
    public void SetButtonPressed(GBButton button, boolean pressed) {
        if (button == null) {
            return;
        }
        GBGamepad activeJoypad = joypad;
        if (activeJoypad != null) {
            activeJoypad.SetButtonPressed(button, pressed);
        }
    }

    @Override
    public void SetButtonPressed(String buttonId, boolean pressed) {
        SetButtonPressed(GBButton.FromId(buttonId), pressed);
    }

    @Override
    public void ResetTransientAudioState() {
        synchronized (stateLock) {
            if (apu != null) {
                apu.ResetAudioOutputState();
            }
        }
    }

    /**
     * Returns whether a ROM image is currently available for patching or restart.
     *
     * @return {@code true} when a ROM has been loaded
     */
    public boolean HasLoadedRom() {
        return rom != null;
    }

    @Override
    public boolean HasLoadedGame() {
        return HasLoadedRom();
    }

    /**
     * Returns the active ROM image when one is loaded.
     *
     * @return loaded ROM or {@code null}
     */
    public GBRom GetLoadedRom() {
        return rom;
    }

    @Override
    public EmulatorGame GetLoadedGame() {
        return rom;
    }

    /**
     * Returns whether the current session exposes battery-backed save data.
     *
     * @return {@code true} when save data can be managed
     */
    public boolean CanManageSaveData() {
        return rom != null && memory != null && memory.HasSaveData();
    }

    /**
     * Returns a snapshot of the live cartridge save RAM.
     *
     * @return raw save bytes
     */
    public byte[] SnapshotSaveData() {
        if (!CanManageSaveData()) {
            return new byte[0];
        }
        synchronized (stateLock) {
            byte[] saveData = memory.ExportSaveData();
            return Arrays.copyOf(saveData, saveData.length);
        }
    }

    /**
     * Exports the live cartridge save RAM to an external file.
     *
     * @param destinationPath export destination
     * @throws IOException when the save cannot be written
     */
    public void ExportSaveData(Path destinationPath) throws IOException {
        if (!CanManageSaveData()) {
            throw new IllegalStateException("No battery-backed save data is available for the current game.");
        }
        synchronized (stateLock) {
            SaveFileManager.ExportSave(memory.ExportSaveData(), memory.ExportSupplementalSaveData(), destinationPath);
        }
    }

    /**
     * Imports external save RAM into the live cartridge and managed save path.
     *
     * @param sourcePath source save file
     * @return imported byte count
     * @throws IOException when the save cannot be read or persisted
     */
    public int ImportSaveData(Path sourcePath) throws IOException {
        if (!CanManageSaveData()) {
            throw new IllegalStateException("No battery-backed save data is available for the current game.");
        }

        synchronized (stateLock) {
            SaveFileManager.SaveDataBundle saveData = SaveFileManager.ImportSaveBundle(rom, sourcePath);
            memory.LoadSaveData(saveData.primaryData());
            memory.LoadSupplementalSaveData(saveData.supplementalData());
            SaveFileManager.Save(rom, memory.ExportSaveData(), memory.ExportSupplementalSaveData());
            return saveData.primaryData().length;
        }
    }

    /**
     * Clears the live cartridge save RAM and removes the managed save file.
     *
     * @throws IOException when the managed save file cannot be removed
     */
    public void DeleteSaveData() throws IOException {
        if (!CanManageSaveData()) {
            throw new IllegalStateException("No battery-backed save data is available for the current game.");
        }

        synchronized (stateLock) {
            memory.LoadSaveData(new byte[0]);
            memory.LoadSupplementalSaveData(new byte[0]);
            SaveFileManager.DeleteSave(rom);
        }
    }

    /**
     * Saves the current emulator state to the managed quick-state slot.
     *
     * @throws IOException when the quick state cannot be written
     */
    public void SaveQuickState() throws IOException {
        SaveStateSlot(QuickStateManager.quickSlot);
    }

    /**
     * Saves the current emulator state to the requested managed slot.
     *
     * @param slot save-state slot from 0 to 9
     * @throws IOException when the state cannot be written
     */
    public void SaveStateSlot(int slot) throws IOException {
        if (!HasLoadedRom() || cpu == null || memory == null || timer == null || ppu == null
                || joypad == null || apu == null) {
            throw new IllegalStateException("Load a ROM before saving a state.");
        }

        synchronized (stateLock) {
            QuickStateManager.Save(rom, slot, CaptureQuickState());
        }
        SetRuntimeStatus(UiText.GuiActions.SaveStateStatus(slot));
    }

    /**
     * Loads the managed quick-state slot into the active emulator session.
     *
     * @throws IOException when the quick state cannot be read
     */
    public void LoadQuickState() throws IOException {
        LoadStateSlot(QuickStateManager.quickSlot);
    }

    /**
     * Loads the requested managed state slot into the active emulator session.
     *
     * @param slot save-state slot from 0 to 9
     * @throws IOException when the state cannot be read
     */
    public void LoadStateSlot(int slot) throws IOException {
        if (!HasLoadedRom() || cpu == null || memory == null || timer == null || ppu == null
                || joypad == null || apu == null) {
            throw new IllegalStateException("Load a ROM before trying to load a state.");
        }

        synchronized (stateLock) {
            QuickStateManager.QuickStateData quickState = QuickStateManager.Load(rom, slot);
            RestoreQuickState(quickState);
        }
        SetRuntimeStatus(UiText.GuiActions.LoadStateStatus(slot));
    }

    /**
     * Applies an IPS patch to the currently loaded ROM and restarts emulation.
     *
     * @param patchFilename IPS patch path
     * @throws IOException when the patch file cannot be read
     */
    public void ApplyIpsPatch(String patchFilename) throws IOException {
        if (rom == null) {
            throw new IllegalStateException("No ROM is currently loaded.");
        }
        StartEmulation(GBRom.LoadPatched(rom, patchFilename));
    }

    @Override
    public void ApplyPatch(String patchFilename) throws IOException {
        ApplyIpsPatch(patchFilename);
    }

    @Override
    public List<EmulatorStateSlot> DescribeCurrentStateSlots() {
        return QuickStateManager.DescribeSlots(rom).stream()
                .map(slot -> new EmulatorStateSlot(slot.slot(), slot.path(), slot.exists(), slot.lastModified()))
                .toList();
    }

    @Override
    public List<EmulatorCheat> DescribeCurrentCheats() {
        if (!HasLoadedRom()) {
            return List.of();
        }
        return CheatStore.GetCheats(rom);
    }

    @Override
    public List<EmulatorCheat> UpdateCurrentCheats(List<EmulatorCheat> cheats) {
        if (!HasLoadedRom()) {
            throw new IllegalStateException("Load a ROM before managing cheats.");
        }

        List<EmulatorCheat> storedCheats = CheatStore.SaveCheats(rom, cheats);
        cheatEngine.SetCheats(storedCheats);
        synchronized (stateLock) {
            ApplyLiveCheats();
        }
        return storedCheats;
    }

    private void InitialiseBootState() {
        if (ShouldUseBootRom()) {
            InitialiseBootRomState();
            return;
        }
        InitialiseBootStateWithoutBootRom();
    }

    private boolean ShouldUseBootRom() {
        if (ShouldUseCgbHardware()) {
            return Settings.useCgbBootRom && BootRomManager.HasCgbBootRom();
        }
        return Settings.useBootRom && BootRomManager.HasDmgBootRom();
    }

    private void InitialiseBootRomState() {
        try {
            if (ShouldUseCgbHardware()) {
                memory.LoadBootRom(BootRomManager.LoadCgbBootRom(), true);
            } else {
                memory.LoadBootRom(BootRomManager.LoadDmgBootRom(), false);
            }
        } catch (IOException exception) {
            InitialiseBootStateWithoutBootRom();
            return;
        }

        cpu.SetPC(0x0000);
        cpu.SetAF(0x0000);
        cpu.SetBC(0x0000);
        cpu.SetDE(0x0000);
        cpu.SetHL(0x0000);
        cpu.SetSP(0x0000);
        cpu.DisableInterrupts();
    }

    private void InitialiseBootStateWithoutBootRom() {
        if (ShouldUseCgbHardware()) {
            InitialiseCgbBootStateWithoutBootRom();
            return;
        }

        cpu.SetPC(0x0100);
        cpu.SetAF(0x01B0);
        cpu.SetBC(0x0013);
        cpu.SetDE(0x00D8);
        cpu.SetHL(0x014D);
        cpu.SetSP(0xFFFE);

        memory.InitialiseDmgBootState();
        timer.InitialiseDmgBootState();
        memory.WriteDirect(GBMemAddresses.STAT, 0x82);
    }

    private void InitialiseCgbBootStateWithoutBootRom() {
        cpu.SetPC(0x0100);
        cpu.SetAF(0x1180);
        cpu.SetBC(0x0000);
        cpu.SetDE(0xFF56);
        cpu.SetHL(0x000D);
        cpu.SetSP(0xFFFE);

        memory.InitialiseCgbBootState();
        ApplyDmgCompatibilityPaletteFallback();
        timer.InitialiseDmgBootState();
        memory.WriteDirect(GBMemAddresses.STAT, 0x82);
    }

    private void ApplyDmgCompatibilityPaletteFallback() {
        if (!memory.IsDmgCompatibilityMode() || Settings.nonGbcColourMode != NonGbcColourMode.GB_ORIGINAL) {
            return;
        }
        memory.SeedDmgCompatibilityPalettes(
                Settings.CurrentGbcBackgroundPalette(),
                Settings.CurrentGbcSpritePalette0(),
                Settings.CurrentGbcSpritePalette1());
    }

    private boolean ShouldUseCgbHardware() {
        return ShouldUseCgbHardware(rom);
    }

    private boolean ShouldUseCgbHardware(GBRom loadedRom) {
        if (loadedRom == null) {
            return false;
        }
        if (loadedRom.IsCgbOnly()) {
            return true;
        }
        if (loadedRom.IsCgbCompatible()) {
            return !Settings.preferDmgModeForGbcCompatibleGames;
        }
        return Settings.nonGbcColourMode == NonGbcColourMode.GB_ORIGINAL;
    }

    private int StepHardware(int tCycles) {
        int masterCycles = memory.IsDoubleSpeedMode() ? Math.max(1, tCycles / 2) : tCycles;

        for (int index = 0; index < tCycles; index++) {
            timer.Tick();
            memory.TickDma();
            HandleSerial();
        }

        for (int index = 0; index < masterCycles; index++) {
            ppu.Step();
            memory.TickHdma(ppu.IsHblankTransferWindowOpen());
            apu.Tick();
        }

        ppu.ConsumeCompletedFrames();
        return masterCycles;
    }

    private void HandleSerial() {
        if (memory.IsSerialTransferInProgress()) {
            DebugLogger.SerialOutput(memory.ReadSerialDataRegister());
            memory.CompleteSerialTransfer();
            cpu.RequestInterrupt(GBProcessor.Interrupt.SERIAL);
        }
    }

    private QuickStateManager.QuickStateData CaptureQuickState() {
        return new QuickStateManager.QuickStateData(
                cpu.CaptureState(),
                memory.CaptureState(),
                timer.CaptureState(),
                ppu.CaptureState(),
                joypad.CaptureState(),
                apu.CaptureState(),
                display.SnapshotFrameState(),
                ppu.GetCurrentScanline());
    }

    private void RestoreQuickState(QuickStateManager.QuickStateData quickState) {
        if (quickState == null) {
            throw new IllegalArgumentException("The quick save file is empty.");
        }
        if (quickState.memoryState() == null || quickState.memoryState().cgbMode() != memory.IsCgbMode()) {
            throw new IllegalArgumentException(
                    "The quick save was created for a different hardware mode. Restart the ROM with matching DMG/CGB settings first.");
        }

        memory.RestoreState(quickState.memoryState());
        cpu.RestoreState(quickState.cpuState());
        timer.RestoreState(quickState.timerState());
        joypad.RestoreState(quickState.joypadState());
        memory.WriteDirect(GBMemAddresses.JOYPAD, joypad.ReadRegister());
        apu.RestoreState(quickState.apuState());
        ppu.RestoreState(quickState.ppuState());
        display.RestoreFrameState(quickState.displayState());
        ApplyLiveCheats();
    }

    private void SetRuntimeStatus(String statusText) {
        host.SetSubtitle(romName, statusText);
    }

    private void ApplyLiveCheats() {
        if (memory == null || !cheatEngine.HasLiveWriteCheats()) {
            return;
        }
        cheatEngine.ApplyWriteCheats(memory);
    }
}
