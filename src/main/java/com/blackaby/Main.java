package com.blackaby;

import com.blackaby.Backend.Helpers.GameLibraryStore;
import com.blackaby.Frontend.MainWindow;
import com.blackaby.Misc.Config;

import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import java.awt.Color;

/**
 * Launches the GameDuck desktop application.
 */
public class Main {
    public static void main(String[] args) {
        configureJava2dPipeline();
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("Button.focus", new ColorUIResource(new Color(0, 0, 0, 0)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        Config.Load();
        GameLibraryStore.RecoverLibrary();
        new MainWindow();
    }

    private static void configureJava2dPipeline() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            // Prefer OpenGL: the Direct3D pipeline can halve presentation FPS at large window sizes.
            // Explicit backend options take precedence; disabling OpenGL alone restores the Windows default.
            String d3d = System.getProperty("sun.java2d.d3d");
            String opengl = System.getProperty("sun.java2d.opengl");
            if (d3d == null && opengl == null) {
                System.setProperty("sun.java2d.d3d", "false");
                System.setProperty("sun.java2d.opengl", "true");
            } else if (d3d == null && "true".equalsIgnoreCase(opengl)) {
                System.setProperty("sun.java2d.d3d", "false");
            }
            return;
        }

        setJava2dPropertyIfAbsent("sun.java2d.opengl", "true");
    }

    private static void setJava2dPropertyIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
