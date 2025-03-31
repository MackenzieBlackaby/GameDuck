package com.blackaby.Backend.Helpers;

import java.awt.event.ActionListener;
import java.io.File;

import com.blackaby.Backend.Emulation.DuckEmulation;
import com.blackaby.Frontend.MainWindow;

import java.awt.FileDialog;
import java.awt.event.ActionEvent;

/**
 * This class represents the different actions that can be performed in the GUI.
 * Each action has a corresponding enum value and a method that is called when
 * the action is performed.
 */
public class GUIActions implements ActionListener {

    /**
     * This enum represents the different actions that can be performed in the GUI.
     */
    public enum Action {
        DEBUG,
        LOADROM,
        PAUSEGAME,
        CLOSEGAME,
        SAVESTATE,
        LOADSTATE,
        OPTIONS,
        EXIT,
        FULLSCREEN,
        WINDOWED,
        TUTORIAL,
        ABOUT
    }

    private Action action;
    private DuckEmulation attachedEmulation;
    private MainWindow mainWindow;

    /**
     * This constructor creates a new GUIActions with the given action.
     * 
     * @param action The action to be performed
     */
    public GUIActions(MainWindow mainWindow, Action action, DuckEmulation attachedEmulation) {
        super();
        this.mainWindow = mainWindow;
        this.action = action;
        this.attachedEmulation = attachedEmulation;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        switch (action) {
            case DEBUG:
                // DebugLogger.logn("Debugging");
                break;
            case LOADROM:
                // Open a file chooser dialog
                FileDialog fileDialog = new FileDialog(mainWindow, "Select a ROM file", FileDialog.LOAD);

                fileDialog.setAlwaysOnTop(true);
                fileDialog.setFilenameFilter((_, name) -> name.endsWith(".gb") || name.endsWith(".gbc"));
                fileDialog.setVisible(true);
                if (fileDialog.getFiles().length != 0) {
                    File file = fileDialog.getFiles()[0];
                    if (file != null) {
                        attachedEmulation.startEmulation(file.getAbsolutePath());
                    }
                }
                break;
            case PAUSEGAME:
                attachedEmulation.pauseEmulation();
                break;
            case CLOSEGAME:
                attachedEmulation.stopEmulation();
                break;
            case SAVESTATE:
                break;
            case LOADSTATE:
                break;
            case OPTIONS:
                break;
            case EXIT:
                break;
            case FULLSCREEN:
                break;
            case WINDOWED:
                break;
            case TUTORIAL:
                break;
            case ABOUT:
                break;
            default:
                // DebugLogger.logn("Invalid action");
                break;
        }
    }
}
