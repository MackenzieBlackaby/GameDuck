package com.blackaby.Backend.Helpers;

import java.awt.event.ActionListener;
import java.io.File;

import com.blackaby.Frontend.AboutWindow;
import com.blackaby.Frontend.MainWindow;
import com.blackaby.Frontend.OptionsWindow;
import com.blackaby.OldBackEnd.Emulation.DuckEmulation;

import java.awt.FileDialog;
import java.awt.event.ActionEvent;
import javax.swing.JOptionPane;

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
        MAXIMISE,
        TUTORIAL,
        ABOUT,
        FRAMECOUNTER,
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

    /**
     * This method is called when the action is performed.
     * It performs the action based on the action enum value.
     * 
     * @param e The event that triggered the action
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        switch (action) {
            case DEBUG:
                // DebugLogger.logn("Debugging");
                break;
            case LOADROM: {
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
            }
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
                new OptionsWindow();
                break;
            case EXIT: {
                int result = JOptionPane.showConfirmDialog(mainWindow, "Are you sure you want to exit?", "Exit",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    System.exit(0);
                }
            }
                break;
            case FULLSCREEN:
                mainWindow.toggleFullScreen();
                break;
            case MAXIMISE:
                mainWindow.toggleMaximise();
                break;
            case FRAMECOUNTER:
                mainWindow.toggleFrameCounter();
                break;
            case TUTORIAL:
                break;
            case ABOUT: {
                new AboutWindow();
            }
                break;
            default:
                // DebugLogger.logn("Invalid action");
                break;
        }
    }
}
