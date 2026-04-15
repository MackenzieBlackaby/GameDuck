package com.blackaby.Frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.blackaby.Misc.Settings;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.junit.jupiter.api.Test;

class MainWindowUiSuppressionTest {

    static {
        System.setProperty("gameduck.disableOpenGlDisplay", "true");
    }

    @Test
    void fullViewSkipsHiddenSerialTextAreaUpdates() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Requires a graphical desktop.");

        boolean originalFillWindow = Settings.fillWindowOutput;
        boolean originalShowSerialOutput = Settings.showSerialOutput;
        MainWindow window = null;
        try {
            Settings.fillWindowOutput = true;
            Settings.showSerialOutput = true;

            window = createWindow();
            JTextArea serialOutputArea = extractSerialOutputArea(window);

            invokeAppendSerialOutput(window, "serial-spam");
            SwingUtilities.invokeAndWait(() -> {
            });

            assertEquals("", serialOutputArea.getText());
        } finally {
            Settings.fillWindowOutput = originalFillWindow;
            Settings.showSerialOutput = originalShowSerialOutput;
            shutdownWindow(window);
        }
    }

    @Test
    void fullViewKeepsDisplayOnEmbeddedSurface() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Requires a graphical desktop.");

        boolean originalFillWindow = Settings.fillWindowOutput;
        MainWindow window = null;
        try {
            Settings.fillWindowOutput = true;

            window = createWindow();
            DuckDisplay display = extractDisplay(window);
            JPanel embeddedDisplaySurface = extractPanel(window, "embeddedDisplaySurface");

            assertSame(embeddedDisplaySurface, display.getParent());
            assertEquals(1, embeddedDisplaySurface.getComponentCount());
        } finally {
            Settings.fillWindowOutput = originalFillWindow;
            shutdownWindow(window);
        }
    }

    private static MainWindow createWindow() throws Exception {
        final MainWindow[] holder = new MainWindow[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new MainWindow());
        return holder[0];
    }

    private static JTextArea extractSerialOutputArea(MainWindow window) throws Exception {
        Field field = MainWindow.class.getDeclaredField("serialOutputArea");
        field.setAccessible(true);
        return (JTextArea) field.get(window);
    }

    private static DuckDisplay extractDisplay(MainWindow window) throws Exception {
        Field field = MainWindow.class.getDeclaredField("display");
        field.setAccessible(true);
        return (DuckDisplay) field.get(window);
    }

    private static JPanel extractPanel(MainWindow window, String fieldName) throws Exception {
        Field field = MainWindow.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (JPanel) field.get(window);
    }

    private static void invokeAppendSerialOutput(MainWindow window, String text) throws Exception {
        Method method = MainWindow.class.getDeclaredMethod("AppendSerialOutput", String.class);
        method.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                method.invoke(window, text);
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private static void shutdownWindow(MainWindow window) throws Exception {
        if (window == null) {
            return;
        }

        Field statsTimerField = MainWindow.class.getDeclaredField("displayStatsTimer");
        statsTimerField.setAccessible(true);
        Timer timer = (Timer) statsTimerField.get(window);
        if (timer != null) {
            timer.stop();
        }

        Field inputRouterField = MainWindow.class.getDeclaredField("inputRouter");
        inputRouterField.setAccessible(true);
        InputRouter inputRouter = (InputRouter) inputRouterField.get(window);
        if (inputRouter != null) {
            inputRouter.Uninstall();
        }

        DuckDisplay display = extractDisplay(window);
        if (display != null) {
            display.Shutdown();
        }

        SwingUtilities.invokeAndWait(window::dispose);
    }
}
