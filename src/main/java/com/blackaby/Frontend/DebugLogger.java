package com.blackaby.Frontend;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Small debug logger used for serial output and ad hoc tracing.
 */
public final class DebugLogger {

    private static final int serialDispatchChunkSize = 32;
    private static final long serialDispatchDelayMillis = 8L;

    /**
     * Listener for live serial output updates.
     */
    public interface SerialListener {
        void SerialOutputAppended(String text);
        void SerialOutputCleared();
    }

    public static final String logFileName = "debugoutput.txt";
    public static final String serialFileName = "serialoutput.txt";

    private static final StringBuilder serialBuffer = new StringBuilder();
    private static final StringBuilder pendingSerialDispatch = new StringBuilder();
    private static final List<SerialListener> serialListeners = new ArrayList<>();
    private static final ScheduledExecutorService serialDispatchExecutor = Executors.newSingleThreadScheduledExecutor(run -> {
        Thread thread = new Thread(run, "gameduck-serial-dispatch");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean serialDispatchScheduled = new AtomicBoolean();
    private static PrintWriter serialWriter;

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
        synchronized (DebugLogger.class) {
            serialBuffer.append(text);
            pendingSerialDispatch.append(text);
            if (pendingSerialDispatch.length() >= serialDispatchChunkSize || text.charAt(0) == '\n') {
                ScheduleSerialDispatch(0L);
            } else {
                ScheduleSerialDispatch(serialDispatchDelayMillis);
            }
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
            pendingSerialDispatch.setLength(0);
            CloseSerialWriter();
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

    private static void EnsureSerialWriter(boolean append) {
        if (serialWriter != null) {
            return;
        }

        try {
            serialWriter = new PrintWriter(new BufferedWriter(new FileWriter(serialFileName, append)));
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private static void CloseSerialWriter() {
        if (serialWriter == null) {
            return;
        }

        serialWriter.flush();
        serialWriter.close();
        serialWriter = null;
    }

    private static void ScheduleSerialDispatch(long delayMillis) {
        if (!serialDispatchScheduled.compareAndSet(false, true)) {
            return;
        }

        serialDispatchExecutor.schedule(DebugLogger::FlushPendingSerialDispatch, delayMillis, TimeUnit.MILLISECONDS);
    }

    private static void FlushPendingSerialDispatch() {
        String text;
        List<SerialListener> listeners;

        synchronized (DebugLogger.class) {
            serialDispatchScheduled.set(false);
            if (pendingSerialDispatch.length() == 0) {
                return;
            }

            text = pendingSerialDispatch.toString();
            pendingSerialDispatch.setLength(0);
            EnsureSerialWriter(true);
            if (serialWriter != null) {
                serialWriter.write(text);
                serialWriter.flush();
            }
            listeners = new ArrayList<>(serialListeners);
        }

        for (SerialListener listener : listeners) {
            listener.SerialOutputAppended(text);
        }

        synchronized (DebugLogger.class) {
            if (pendingSerialDispatch.length() > 0) {
                ScheduleSerialDispatch(0L);
            }
        }
    }

}
