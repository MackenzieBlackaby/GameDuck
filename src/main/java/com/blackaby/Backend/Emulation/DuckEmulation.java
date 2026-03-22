package com.blackaby.Backend.Emulation;

import com.blackaby.Backend.Emulation.CPU.DuckCPU;
import com.blackaby.Backend.Emulation.CPU.InstructionLogic;
import com.blackaby.Backend.Emulation.Graphics.DuckPPU;
import com.blackaby.Backend.Emulation.Memory.DuckAddresses;
import com.blackaby.Backend.Emulation.Memory.DuckMemory;
import com.blackaby.Backend.Emulation.Misc.ROM;
import com.blackaby.Backend.Emulation.Misc.Specifics;
import com.blackaby.Backend.Emulation.Peripherals.DuckAPU;
import com.blackaby.Backend.Emulation.Peripherals.DuckJoypad;
import com.blackaby.Backend.Emulation.Peripherals.DuckTimer;
import com.blackaby.Frontend.DebugLogger;
import com.blackaby.Frontend.DuckDisplay;
import com.blackaby.Frontend.MainWindow;
import com.blackaby.Backend.Helpers.SaveFileManager;
import com.blackaby.Backend.Helpers.ManagedGameRegistry;
import com.blackaby.Backend.Helpers.GameLibraryStore;
import com.blackaby.Backend.Helpers.QuickStateManager;
import com.blackaby.Misc.BootRomManager;
import com.blackaby.Misc.Settings;
import com.blackaby.Misc.UiText;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;

/**
 * Coordinates the main emulator runtime.
 * <p>
 * The class owns the active hardware blocks, loads cartridges, applies the DMG
 * boot state when the boot ROM is skipped, and runs the timed execution loop
 * used by the desktop front end.
 */
public class DuckEmulation implements Runnable {

    private static final double maxTimeAccumulator = 50_000_000.0;
    private static final long minParkNanos = 100_000L;

    private DuckCPU cpu;
    private DuckMemory memory;
    private DuckTimer timer;
    private DuckPPU ppu;
    private DuckJoypad joypad;
    private DuckAPU apu;
    private final DuckDisplay display;
    private ROM rom;
    private final MainWindow mainWindow;
    private Thread emulationThread;
    private volatile boolean running;
    private volatile boolean paused;
    private int frames;
    private int previousLy;
    private final String defaultRomName = "NO ROM LOADED";
    private String romName = defaultRomName;
    private final Object stateLock = new Object();

    /**
     * Creates an emulator controller for the main window and display.
     *
     * @param window owning main window
     * @param display display surface for rendered frames
     */
    public DuckEmulation(MainWindow window, DuckDisplay display) {
        this.display = display;
        mainWindow = window;
    }

    /**
     * Loads a ROM and starts the execution thread.
     *
     * @param romFile path to the ROM file
     */
    public void StartEmulation(String romFile) {
        StartEmulation(new ROM(romFile));
    }

    /**
     * Loads a ROM image and starts the execution thread.
     *
     * @param rom ROM image to start
     */
    public void StartEmulation(ROM rom) {
        if (running) {
            StopEmulation();
        }

        DebugLogger.ClearSerialOutput();
        running = true;
        paused = false;
        this.rom = rom;
        romName = rom.GetName();

        memory = new DuckMemory();
        apu = new DuckAPU(memory);
        cpu = new DuckCPU(memory, this, this.rom);
        joypad = new DuckJoypad(cpu);
        timer = new DuckTimer(cpu, memory);
        ppu = new DuckPPU(cpu, memory, display);

        memory.SetTimer(timer);
        memory.SetCpu(cpu);
        memory.SetJoypad(joypad);
        memory.SetApu(apu);
        memory.LoadRom(this.rom, ShouldUseCgbHardware(this.rom));
        SaveFileManager.LoadSave(this.rom).ifPresent(memory::LoadSaveData);
        ManagedGameRegistry.RememberGame(this.rom);
        try {
            GameLibraryStore.RememberGame(this.rom);
        } catch (IllegalStateException exception) {
            exception.printStackTrace();
        }

        InstructionLogic.Initialise(cpu, memory);
        mainWindow.SetSubtitle(romName, "[0 FPS]");
        mainWindow.SetLoadedRom(this.rom, false);
        mainWindow.LoadGameArt(this.rom);

        emulationThread = new Thread(this);
        emulationThread.start();
    }

    /**
     * Toggles the paused state of the emulator.
     */
    public void PauseEmulation() {
        paused = !paused;
        if (paused) {
            mainWindow.SetSubtitle(romName, ": Paused");
        } else {
            mainWindow.SetSubtitle(romName, "[" + frames + " FPS]");
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
        mainWindow.SetSubtitle(romName);
        mainWindow.SetLoadedRom(null);
        mainWindow.ClearGameArt();
        display.clear();

        if (rom != null && memory != null && memory.HasSaveData()) {
            SaveFileManager.Save(rom, memory.ExportSaveData());
        }

        if (apu != null) {
            apu.Shutdown();
        }

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
        StartFrameCounter();

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
            int availableCycles = (int) (timeAccumulator / Specifics.nanosecondsPerCycle);
            if (availableCycles <= 0) {
                long parkNanos = (long) (Specifics.nanosecondsPerCycle - timeAccumulator);
                if (parkNanos >= minParkNanos) {
                    LockSupport.parkNanos(parkNanos);
                } else {
                    Thread.onSpinWait();
                }
                continue;
            }

            int executedCycles = 0;
            synchronized (stateLock) {
                while (running && !paused && availableCycles > 0) {
                    if (!cpu.IsHalted()) {
                        cpu.Fetch();
                        cpu.Decode();
                    }

                    int tCycles = cpu.Execute();
                    StepHardware(tCycles);

                    executedCycles += tCycles;
                    availableCycles -= tCycles;
                    TrackFrameBoundary();
                }
            }

            timeAccumulator -= executedCycles * Specifics.nanosecondsPerCycle;
        }
    }

    /**
     * Updates the joypad state from host input.
     *
     * @param button joypad button to update
     * @param pressed whether the button is pressed
     */
    public void SetButtonPressed(DuckJoypad.Button button, boolean pressed) {
        synchronized (stateLock) {
            if (joypad != null) {
                joypad.SetButtonPressed(button, pressed);
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

    /**
     * Returns the active ROM image when one is loaded.
     *
     * @return loaded ROM or {@code null}
     */
    public ROM GetLoadedRom() {
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
        SaveFileManager.ExportSave(SnapshotSaveData(), destinationPath);
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
            byte[] saveData = SaveFileManager.ImportSave(rom, sourcePath);
            memory.LoadSaveData(saveData);
            SaveFileManager.Save(rom, memory.ExportSaveData());
            return saveData.length;
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
        StartEmulation(ROM.LoadPatched(rom, patchFilename));
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
        memory.WriteDirect(DuckAddresses.STAT, 0x82);
    }

    private void InitialiseCgbBootStateWithoutBootRom() {
        cpu.SetPC(0x0100);
        cpu.SetAF(0x1180);
        cpu.SetBC(0x0000);
        cpu.SetDE(0xFF56);
        cpu.SetHL(0x000D);
        cpu.SetSP(0xFFFE);

        memory.InitialiseCgbBootState();
        timer.InitialiseDmgBootState();
        memory.WriteDirect(DuckAddresses.STAT, 0x82);
    }

    private boolean ShouldUseCgbHardware() {
        return ShouldUseCgbHardware(rom);
    }

    private boolean ShouldUseCgbHardware(ROM loadedRom) {
        if (loadedRom == null || !loadedRom.IsCgbCompatible()) {
            return false;
        }
        if (loadedRom.IsCgbOnly()) {
            return true;
        }
        return !Settings.preferDmgModeForGbcCompatibleGames;
    }

    private void StepHardware(int tCycles) {
        for (int index = 0; index < tCycles; index++) {
            timer.Tick();
            memory.TickDma();
            ppu.Step();
            apu.Tick();
            HandleSerial();
        }
    }

    private void TrackFrameBoundary() {
        int currentLy = memory.Read(DuckAddresses.LY);
        if (currentLy < previousLy) {
            frames++;
        }
        previousLy = currentLy;
    }

    private void HandleSerial() {
        int serialControl = memory.Read(DuckAddresses.SERIAL_CONTROL);
        int serialData = memory.Read(DuckAddresses.SERIAL_DATA);

        if ((serialControl & 0x81) == 0x81) {
            DebugLogger.SerialOutput(serialData);
            memory.Write(DuckAddresses.SERIAL_DATA, 0xFF);
            memory.Write(DuckAddresses.SERIAL_CONTROL, serialControl & ~0x80);
            cpu.RequestInterrupt(DuckCPU.Interrupt.SERIAL);
        }
    }

    private void StartFrameCounter() {
        Thread frameCounterThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(1000);
                    if (paused || !running) {
                        continue;
                    }
                    mainWindow.UpdateFrameCounter(frames);
                    mainWindow.SetSubtitle(romName, "[" + frames + " FPS]");
                    frames = 0;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        frameCounterThread.setDaemon(true);
        frameCounterThread.start();
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
                frames,
                previousLy);
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
        memory.WriteDirect(DuckAddresses.JOYPAD, joypad.ReadRegister());
        apu.RestoreState(quickState.apuState());
        ppu.RestoreState(quickState.ppuState());
        display.RestoreFrameState(quickState.displayState());
        frames = Math.max(0, quickState.frames());
        previousLy = quickState.previousLy();
    }

    private void SetRuntimeStatus(String statusText) {
        mainWindow.SetSubtitle(romName, statusText);
    }

    @Deprecated public void startEmulation(String romFile) { StartEmulation(romFile); }
    @Deprecated public void pauseEmulation() { PauseEmulation(); }
    @Deprecated public void stopEmulation() { StopEmulation(); }
    @Deprecated public void restartEmulation() { RestartEmulation(); }
    @Deprecated public void setButtonPressed(DuckJoypad.Button button, boolean pressed) { SetButtonPressed(button, pressed); }
    @Deprecated public boolean hasLoadedRom() { return HasLoadedRom(); }
    @Deprecated public ROM getLoadedRom() { return GetLoadedRom(); }
    @Deprecated public boolean canManageSaveData() { return CanManageSaveData(); }
    @Deprecated public byte[] snapshotSaveData() { return SnapshotSaveData(); }
    @Deprecated public void exportSaveData(Path destinationPath) throws IOException { ExportSaveData(destinationPath); }
    @Deprecated public int importSaveData(Path sourcePath) throws IOException { return ImportSaveData(sourcePath); }
    @Deprecated public void deleteSaveData() throws IOException { DeleteSaveData(); }
    @Deprecated public void applyIpsPatch(String patchFilename) throws IOException { ApplyIpsPatch(patchFilename); }
}
