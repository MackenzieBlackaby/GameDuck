package com.blackaby.Frontend;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Small debug logger used for serial output and ad hoc tracing.
 */
public final class DebugLogger {

    /**
     * Listener for live serial output updates.
     */
    public interface SerialListener {
        void SerialOutputAppended(String text);
        void SerialOutputCleared();
    }

    public static final String logFileName = "debugoutput.txt";
    public static final String serialFileName = "serialoutput.txt";
    @Deprecated public static final String LOG_FILE = logFileName;
    @Deprecated public static final String SERIAL_FILE = serialFileName;

    private static final StringBuilder serialBuffer = new StringBuilder();
    private static final List<SerialListener> serialListeners = new ArrayList<>();

    private DebugLogger() {
    }

    /**
     * Writes a message to standard output.
     *
     * @param message text to write
     */
    public static void Log(String message) {
        System.out.print(message);
    }

    /**
     * Appends a message to a file.
     *
     * @param message text to write
     * @param filePath output file path
     */
    public static void LogFile(String message, String filePath) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath, true))) {
            writer.write(message + "\r\n");
            writer.flush();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Writes a message followed by a line break.
     *
     * @param message text to write
     */
    public static void LogLine(String message) {
        Log(message + "\n");
    }

    /**
     * Appends a single serial byte as a character to the serial log.
     *
     * @param byteToPrint serial byte to record
     */
    public static void SerialOutput(int byteToPrint) {
        String text = Character.toString((char) (byteToPrint & 0xFF));
        List<SerialListener> listeners;

        synchronized (DebugLogger.class) {
            serialBuffer.append(text);
            listeners = new ArrayList<>(serialListeners);
        }

        LogFile(text, serialFileName);
        for (SerialListener listener : listeners) {
            listener.SerialOutputAppended(text);
        }
    }

    /**
     * Returns the in-memory serial output captured for the current session.
     *
     * @return captured serial output text
     */
    public static synchronized String GetSerialOutput() {
        return serialBuffer.toString();
    }

    /**
     * Clears the serial output buffer and truncates the serial log file.
     */
    public static void ClearSerialOutput() {
        List<SerialListener> listeners;
        synchronized (DebugLogger.class) {
            serialBuffer.setLength(0);
            listeners = new ArrayList<>(serialListeners);
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(serialFileName, false))) {
            writer.flush();
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        for (SerialListener listener : listeners) {
            listener.SerialOutputCleared();
        }
    }

    /**
     * Registers a live serial output listener.
     *
     * @param listener listener to register
     */
    public static synchronized void AddSerialListener(SerialListener listener) {
        if (listener != null) {
            serialListeners.add(listener);
        }
    }

    /**
     * Removes a previously registered live serial output listener.
     *
     * @param listener listener to remove
     */
    public static synchronized void RemoveSerialListener(SerialListener listener) {
        serialListeners.remove(listener);
    }

    @Deprecated public static void log(String message) { Log(message); }
    @Deprecated public static void logFile(String message, String file) { LogFile(message, file); }
    @Deprecated public static void logn(String message) { LogLine(message); }
    @Deprecated public static void serialOutput(int byteToPrint) { SerialOutput(byteToPrint); }
    @Deprecated public static String getSerialOutput() { return GetSerialOutput(); }
    @Deprecated public static void clearSerialOutput() { ClearSerialOutput(); }
    @Deprecated public static void addSerialListener(SerialListener listener) { AddSerialListener(listener); }
    @Deprecated public static void removeSerialListener(SerialListener listener) { RemoveSerialListener(listener); }
}
