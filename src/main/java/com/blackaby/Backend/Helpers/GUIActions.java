package com.blackaby.Backend.Helpers;

import java.awt.event.ActionListener;

import javax.swing.JFileChooser;

import com.blackaby.Backend.Emulation.DuckEmulation;

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

    /**
     * This constructor creates a new GUIActions with the given action.
     * 
     * @param action The action to be performed
     */
    public GUIActions(Action action, DuckEmulation attachedEmulation) {
        super();
        this.action = action;
        this.attachedEmulation = attachedEmulation;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        switch (action) {
            case DEBUG:
                System.out.println("Debugging");
                break;
            case LOADROM:
                // Open a file chooser dialog
                JFileChooser fileChooser = new JFileChooser();
                int returnValue = fileChooser.showOpenDialog(null);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    attachedEmulation.startEmulation(fileChooser.getSelectedFile().getAbsolutePath());
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
                System.out.println("Invalid action");
                break;
        }
    }
}
