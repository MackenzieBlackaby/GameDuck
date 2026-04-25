package com.blackaby.Backend.Helpers;

import com.blackaby.Backend.Platform.BackendRegistry;
import com.blackaby.Backend.Platform.EmulatorRuntime;
import com.blackaby.Frontend.AboutWindow;
import com.blackaby.Frontend.LibraryWindow;
import com.blackaby.Frontend.MainWindow;
import com.blackaby.Frontend.OptionsWindow;
import com.blackaby.Misc.UiText;

import javax.swing.JOptionPane;
import java.awt.FileDialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Dispatches host UI actions from menus and buttons.
 */
public final class GUIActions implements ActionListener {

    /**
     * Actions available in the host UI.
     */
    public enum Action {
        DEBUG,
        LOADROM,
        RESETGAME,
        LIBRARY,
        LOADIPS,
        PAUSEGAME,
        CLOSEGAME,
        SAVESTATE,
        LOADSTATE,
        OPTIONS,
        EXIT,
        FULL_VIEW,
        FULLSCREEN,
        MAXIMISE,
        TUTORIAL,
        ABOUT,
    }

    private final Action action;
    private final MainWindow mainWindow;
    private final Integer stateSlot;

    /**
     * Creates an action dispatcher for one UI action.
     *
     * @param mainWindow owning main window
     * @param action action to dispatch
     */
    public GUIActions(MainWindow mainWindow, Action action) {
        this(mainWindow, action, null);
    }

    /**
     * Creates an action dispatcher for a state-slot menu item.
     *
     * @param mainWindow owning main window
     * @param action save or load action
     * @param stateSlot save-state slot from 0 to 9
     */
    public GUIActions(MainWindow mainWindow, Action action, Integer stateSlot) {
        this.mainWindow = mainWindow;
        this.action = action;
        this.stateSlot = stateSlot;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        switch (action) {
            case DEBUG:
                break;
            case LOADROM:
                HandleLoadRom();
                break;
            case RESETGAME:
                emulation().RestartEmulation();
                break;
            case LIBRARY:
                new LibraryWindow(mainWindow);
                break;
            case LOADIPS:
                HandleLoadIpsPatch();
                break;
            case PAUSEGAME:
                emulation().PauseEmulation();
                break;
            case CLOSEGAME:
                emulation().StopEmulation();
                break;
            case SAVESTATE:
                HandleSaveState();
                break;
            case LOADSTATE:
                HandleLoadState();
                break;
            case OPTIONS:
                new OptionsWindow(mainWindow);
                break;
            case EXIT:
                HandleExit();
                break;
            case FULL_VIEW:
                mainWindow.ToggleFullView();
                break;
            case FULLSCREEN:
                mainWindow.ToggleFullScreen();
                break;
            case MAXIMISE:
                mainWindow.ToggleMaximise();
                break;
            case TUTORIAL:
                break;
            case ABOUT:
                new AboutWindow();
                break;
            default:
                break;
        }
    }

    /**
     * Opens the ROM file picker and starts emulation if a file is chosen.
     */
    private void HandleLoadRom() {
        FileDialog fileDialog = new FileDialog(mainWindow, UiText.GuiActions.LOAD_ROM_DIALOG_TITLE, FileDialog.LOAD);
        fileDialog.setAlwaysOnTop(true);
        fileDialog.setFilenameFilter((directory, name) -> HasSupportedGameFileExtension(name));
        fileDialog.setVisible(true);

        if (fileDialog.getFiles().length == 0) {
            return;
        }

        File file = fileDialog.getFiles()[0];
        if (file == null) {
            return;
        }

        try {
            if (IsIpsPatch(file)) {
                OpenIpsPatch(file);
            } else {
                mainWindow.StartEmulation(file.toPath());
            }
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(mainWindow, exception.getMessage(), UiText.GuiActions.LOAD_ROM_ERROR_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Opens an IPS patch and applies it to the current ROM when possible.
     */
    private void HandleLoadIpsPatch() {
        FileDialog fileDialog = new FileDialog(mainWindow, UiText.GuiActions.LOAD_IPS_DIALOG_TITLE, FileDialog.LOAD);
        fileDialog.setAlwaysOnTop(true);
        fileDialog.setFilenameFilter((directory, name) -> name.toLowerCase().endsWith(".ips"));
        fileDialog.setVisible(true);

        if (fileDialog.getFiles().length == 0) {
            return;
        }

        File file = fileDialog.getFiles()[0];
        if (file == null) {
            return;
        }

        try {
            OpenIpsPatch(file);
        } catch (IOException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(mainWindow, exception.getMessage(), UiText.GuiActions.LOAD_IPS_ERROR_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void OpenIpsPatch(File patchFile) throws IOException {
        EmulatorRuntime emulation = emulation();
        if (emulation.HasLoadedGame()) {
            emulation.ApplyPatch(patchFile.getAbsolutePath());
            return;
        }

        File baseRom = PromptForBaseRom();
        if (baseRom == null) {
            return;
        }
        mainWindow.StartPatchedEmulation(baseRom.toPath(), patchFile.toPath());
    }

    private File PromptForBaseRom() {
        FileDialog fileDialog = new FileDialog(mainWindow, UiText.GuiActions.BASE_ROM_DIALOG_TITLE, FileDialog.LOAD);
        fileDialog.setAlwaysOnTop(true);
        fileDialog.setFilenameFilter((directory, name) -> HasRomExtension(name));
        fileDialog.setVisible(true);
        return fileDialog.getFiles().length == 0 ? null : fileDialog.getFiles()[0];
    }

    private boolean HasSupportedGameFileExtension(String name) {
        String lowerName = name.toLowerCase();
        return BackendRegistry.All().stream()
                .map(backend -> backend.Profile())
                .anyMatch(profile -> matchesExtension(lowerName, profile.supportedGameFileExtensions())
                        || matchesExtension(lowerName, profile.supportedPatchFileExtensions()));
    }

    private boolean HasRomExtension(String name) {
        String lowerName = name.toLowerCase();
        return BackendRegistry.All().stream()
                .map(backend -> backend.Profile())
                .anyMatch(profile -> matchesExtension(lowerName, profile.supportedGameFileExtensions()));
    }

    private boolean IsIpsPatch(File file) {
        if (file == null) {
            return false;
        }

        try {
            return BackendRegistry.ResolveBackendForPatch(Path.of(file.getAbsolutePath())) != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean matchesExtension(String fileName, List<String> extensions) {
        for (String extension : extensions) {
            if (fileName.endsWith(extension.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prompts before closing the application.
     */
    private void HandleExit() {
        int result = JOptionPane.showConfirmDialog(mainWindow, UiText.GuiActions.EXIT_CONFIRM_MESSAGE, UiText.GuiActions.EXIT_CONFIRM_TITLE,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (result == JOptionPane.YES_OPTION) {
            emulation().StopEmulation();
            System.exit(0);
        }
    }

    /**
     * Writes a managed save-state slot for the active ROM.
     */
    private void HandleSaveState() {
        try {
            if (stateSlot == null) {
                emulation().SaveQuickState();
            } else {
                emulation().SaveStateSlot(stateSlot);
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            JOptionPane.showMessageDialog(mainWindow, exception.getMessage(),
                    stateSlot == null ? UiText.GuiActions.QUICK_SAVE_ERROR_TITLE : UiText.GuiActions.SAVE_STATE_ERROR_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Restores a managed save-state slot for the active ROM.
     */
    private void HandleLoadState() {
        try {
            if (stateSlot == null) {
                emulation().LoadQuickState();
            } else {
                emulation().LoadStateSlot(stateSlot);
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            JOptionPane.showMessageDialog(mainWindow, exception.getMessage(),
                    stateSlot == null ? UiText.GuiActions.QUICK_LOAD_ERROR_TITLE : UiText.GuiActions.LOAD_STATE_ERROR_TITLE,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private EmulatorRuntime emulation() {
        return mainWindow.GetEmulation();
    }
}

