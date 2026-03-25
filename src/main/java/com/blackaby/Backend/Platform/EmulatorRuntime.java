package com.blackaby.Backend.Platform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Backend runtime contract consumed by the desktop frontend.
 */
public interface EmulatorRuntime {

    EmulatorProfile Profile();

    void StartEmulation(String gameFilePath);

    void StartEmulation(EmulatorMedia media);

    void PauseEmulation();

    void RestartEmulation();

    void StopEmulation();

    boolean HasLoadedGame();

    EmulatorGame GetLoadedGame();

    boolean CanManageSaveData();

    byte[] SnapshotSaveData();

    void ExportSaveData(Path destinationPath) throws IOException;

    int ImportSaveData(Path sourcePath) throws IOException;

    void DeleteSaveData() throws IOException;

    void SaveQuickState() throws IOException;

    void SaveStateSlot(int slot) throws IOException;

    void LoadQuickState() throws IOException;

    void LoadStateSlot(int slot) throws IOException;

    void ApplyPatch(String patchFilename) throws IOException;

    void SetButtonPressed(String buttonId, boolean pressed);

    List<EmulatorStateSlot> DescribeCurrentStateSlots();
}
