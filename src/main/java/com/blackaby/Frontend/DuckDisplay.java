package com.blackaby.Frontend;

import com.blackaby.Backend.GB.GBBackendManager;
import com.blackaby.Backend.Platform.EmulatorDisplaySpec;
import com.blackaby.Frontend.Borders.DisplayBorderManager;
import com.blackaby.Frontend.Borders.DisplayBorderRenderer;
import com.blackaby.Frontend.Borders.LoadedDisplayBorder;
import com.blackaby.Frontend.Shaders.DisplayShaderManager;
import com.blackaby.Frontend.Shaders.LoadedDisplayShader;
import com.blackaby.Misc.Settings;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * A custom JPanel for rendering Game Boy display output.
 * Handles pixel manipulation, image scaling, and drawing logic.
 */
public class DuckDisplay extends JPanel {
    private static final int dmgPreviousFrameBlendWeight = 3;
    private static final int dmgCurrentFrameBlendWeight = 5;
    private static final int cgbPreviousFrameBlendWeight = 1;
    private static final int cgbCurrentFrameBlendWeight = 7;

    public record FrameState(int[] frontBuffer, int[] backBuffer) implements java.io.Serializable {
    }

    public record PresentationStats(double paintedFps, double averageFrameTimeMs) {
    }

    private final EmulatorDisplaySpec displaySpec;
    private final Object frameLock = new Object();
    private final Object shaderQueueLock = new Object();
    private final AtomicBoolean repaintQueued = new AtomicBoolean();
    private final AtomicBoolean shaderRenderQueued = new AtomicBoolean();
    private final boolean asyncShaderRenderingEnabled;
    private final ExecutorService shaderRenderExecutor = Executors.newSingleThreadExecutor(run -> {
        Thread thread = new Thread(run, "gameduck-display-shader");
        thread.setDaemon(true);
        return thread;
    });
    private BufferedImage image;
    private int[] frontBuffer;
    private int[] backBuffer;
    private int[] imageBuffer;
    private int[] paintBuffer;
    private int[] shaderSourceBuffer;
    private int[] shaderScratchBuffer;
    private int[] pendingFrameBuffer;
    private int[] workerFrameBuffer;
    private int[] workerShaderSourceBuffer;
    private int[] workerShaderTargetBuffer;
    private int[] workerShaderScratchBuffer;
    private volatile LoadedDisplayShader activeShader;
    private volatile LoadedDisplayBorder activeBorder;
    private transient DisplayBorderRenderer.PreparedBorderFrame preparedBorderFrame;
    private transient String preparedBorderId;
    private transient int preparedBorderWidth = -1;
    private transient int preparedBorderHeight = -1;
    private transient long statsWindowStartNanos;
    private transient int statsWindowPaintCount;
    private transient long lastPaintNanos;
    private transient double smoothedFrameIntervalNanos;
    private volatile PresentationStats presentationStats = new PresentationStats(0.0, 0.0);
    private int pendingShaderFrameVersion;
    private int displayedShaderFrameVersion;
    private int shaderRenderEpoch;
    private int renderScale = 1;
    private int[] mainScaleRowBuffer = new int[0];
    private int[] workerScaleRowBuffer = new int[0];
    private volatile boolean shutdown;

    /**
     * Constructs a DuckDisplay with a black background and
     * initialises the image buffer to the standard Game Boy resolution.
     */
    public DuckDisplay() {
        this(GBBackendManager.Current().Profile().displaySpec());
    }

    /**
     * Constructs a display surface for the supplied backend display spec.
     *
     * @param displaySpec backend display geometry
     */
    public DuckDisplay(EmulatorDisplaySpec displaySpec) {
        this(displaySpec, !GraphicsEnvironment.isHeadless());
    }

    DuckDisplay(EmulatorDisplaySpec displaySpec, boolean asyncShaderRenderingEnabled) {
        super();
        this.displaySpec = displaySpec;
        this.asyncShaderRenderingEnabled = asyncShaderRenderingEnabled;
        setBackground(displaySpec == null ? Color.BLACK : displaySpec.backgroundColour());
        setOpaque(true);
        setDoubleBuffered(false);
        initializeFrameBuffers();
        initializeRenderBuffers(1);
        RefreshShader();
        RefreshBorder();
    }

    /**
     * Sets the colour of a pixel at the specified coordinates.
     * Optionally triggers a repaint of the component.
     *
     * @param x       X coordinate of the pixel
     * @param y       Y coordinate of the pixel
     * @param color   Colour to apply
     * @param repaint Whether to repaint the component afterwards
     */
    public void setPixel(int x, int y, Color color, boolean repaint) {
        if (color != null) {
            setPixel(x, y, color.getRGB(), repaint);
        }
    }

    /**
     * Sets the colour of a pixel using a packed RGB value.
     *
     * @param x       X coordinate of the pixel
     * @param y       Y coordinate of the pixel
     * @param rgb     Packed RGB value
     * @param repaint Whether to repaint the component afterwards
     */
    public void setPixel(int x, int y, int rgb, boolean repaint) {
        int frameWidth = frameWidth();
        int frameHeight = frameHeight();
        if (backBuffer != null && x >= 0 && x < frameWidth && y >= 0 && y < frameHeight) {
            backBuffer[(y * frameWidth) + x] = rgb;
            if (repaint) {
                presentFrame();
            }
        }
    }

    /**
     * Sets the colour of a pixel and repaints the component.
     *
     * @param x     X coordinate of the pixel
     * @param y     Y coordinate of the pixel
     * @param color Colour to apply
     */
    public void setPixel(int x, int y, Color color) {
        setPixel(x, y, color, true);
    }

    /**
     * Sets the colour of a pixel and repaints the component.
     *
     * @param x   X coordinate of the pixel
     * @param y   Y coordinate of the pixel
     * @param rgb Packed RGB value
     */
    public void setPixel(int x, int y, int rgb) {
        setPixel(x, y, rgb, true);
    }

    /**
     * Sets the colour of a pixel using a hexadecimal string.
     * Optionally triggers a repaint of the component.
     *
     * @param x        X coordinate of the pixel
     * @param y        Y coordinate of the pixel
     * @param hexColor Colour in hexadecimal format (e.g., "#FFFFFF")
     * @param repaint  Whether to repaint the component afterwards
     */
    public void setPixel(int x, int y, String hexColor, boolean repaint) {
        setPixel(x, y, Color.decode(hexColor), repaint);
    }

    /**
     * Sets the colour of a pixel using a hexadecimal string
     * and repaints the component.
     *
     * @param x        X coordinate of the pixel
     * @param y        Y coordinate of the pixel
     * @param hexColor Colour in hexadecimal format (e.g., "#FFFFFF")
     */
    public void setPixel(int x, int y, String hexColor) {
        setPixel(x, y, hexColor, true);
    }

    /**
     * Clears the display by setting all pixels to black,
     * then repaints the component.
     */
    public void clear() {
        if (backBuffer == null || frontBuffer == null || imageBuffer == null) {
            return;
        }

        Arrays.fill(backBuffer, Color.BLACK.getRGB());
        synchronized (frameLock) {
            Arrays.fill(frontBuffer, Color.BLACK.getRGB());
            FillDisplayBuffersLocked(Color.BLACK.getRGB());
            InvalidateAsyncShaderFrames();
        }

        RequestRepaint();
    }

    /**
     * Copies the completed emulation back buffer to the image presented on the EDT.
     */
    public void presentFrame() {
        presentFrame(0, 1);
    }

    /**
     * Copies the completed emulation back buffer to the image presented on the EDT.
     *
     * @param blendWithPreviousFrame whether to blend the new frame with the
     *                               previously displayed image to approximate LCD
     *                               persistence
     */
    public void presentFrame(boolean blendWithPreviousFrame) {
        presentFrame(
                blendWithPreviousFrame ? dmgPreviousFrameBlendWeight : 0,
                blendWithPreviousFrame ? dmgCurrentFrameBlendWeight : 1);
    }

    /**
     * Copies the completed emulation back buffer using weighted blending with the
     * previously displayed image.
     *
     * @param previousFrameWeight blend weight for the previous frame
     * @param currentFrameWeight  blend weight for the current frame
     */
    public void presentFrame(int previousFrameWeight, int currentFrameWeight) {
        if (backBuffer == null || frontBuffer == null) {
            return;
        }

        boolean frameUpdated;
        synchronized (frameLock) {
            if (previousFrameWeight <= 0 || currentFrameWeight <= 0) {
                System.arraycopy(backBuffer, 0, frontBuffer, 0, backBuffer.length);
            } else {
                for (int index = 0; index < backBuffer.length; index++) {
                    frontBuffer[index] = BlendRgb(frontBuffer[index], backBuffer[index],
                            previousFrameWeight, currentFrameWeight);
                }
            }
            frameUpdated = RenderImageBufferLocked();
        }

        if (frameUpdated) {
            RequestRepaint();
        }
    }

    /**
     * Returns a copy of the currently visible and in-progress frame buffers.
     *
     * @return frame snapshot
     */
    public FrameState SnapshotFrameState() {
        if (backBuffer == null || frontBuffer == null) {
            return new FrameState(new int[0], new int[0]);
        }

        synchronized (frameLock) {
            return new FrameState(
                    Arrays.copyOf(frontBuffer, frontBuffer.length),
                    Arrays.copyOf(backBuffer, backBuffer.length));
        }
    }

    /**
     * Restores the currently visible and in-progress frame buffers.
     *
     * @param frameState frame snapshot to restore
     */
    public void RestoreFrameState(FrameState frameState) {
        if (frameState == null || frontBuffer == null || backBuffer == null) {
            return;
        }
        if (frameState.frontBuffer() == null || frameState.backBuffer() == null
                || frameState.frontBuffer().length != frontBuffer.length
                || frameState.backBuffer().length != backBuffer.length) {
            throw new IllegalArgumentException("Quick state frame data is invalid for this display.");
        }

        synchronized (frameLock) {
            System.arraycopy(frameState.frontBuffer(), 0, frontBuffer, 0, frontBuffer.length);
            System.arraycopy(frameState.backBuffer(), 0, backBuffer, 0, backBuffer.length);
            if (RenderImageBufferLocked()) {
                RequestRepaint();
            }
        }
    }

    /**
     * Repaints the component with the current image,
     * scaling it to fit the component while maintaining aspect ratio.
     *
     * @param g Graphics context used for rendering
     */
    @Override
    protected void paintComponent(Graphics g) {
        if (image == null) {
            if (isOpaque()) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            return;
        }

        if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }

        Graphics2D g2d = (Graphics2D) g;
        LoadedDisplayBorder border = activeBorder != null ? activeBorder
                : DisplayBorderManager.Resolve(Settings.displayBorderId);
        DisplayBorderRenderer.paint(g2d, image, prepareBorderFrame(border, getWidth(), getHeight()));
        recordPaintPresentation();
    }

    public PresentationStats SnapshotPresentationStats() {
        return presentationStats;
    }

    /**
     * Resizes the internal image buffer to match the
     * standard Game Boy resolution, preserving existing content if possible.
     */
    public void resizeImage() {
        initializeFrameBuffers();
        initializeRenderBuffers(activeShader == null ? 1 : activeShader.renderScale());
        RefreshShader();
    }

    /**
     * Re-resolves the selected shader and reapplies it to the current frame.
     */
    public void RefreshShader() {
        activeShader = DisplayShaderManager.Resolve(Settings.displayShaderId);
        InvalidateAsyncShaderFrames();
        boolean repaintNow = false;
        synchronized (frameLock) {
            if (frontBuffer != null && imageBuffer != null && shaderScratchBuffer != null) {
                EnsureRenderBuffersForShader(activeShader);
                repaintNow = RenderImageBufferLocked();
            }
        }
        if (repaintNow) {
            RequestRepaint();
        }
    }

    /**
     * Re-resolves the selected display border and repaints the display.
     */
    public void RefreshBorder() {
        activeBorder = DisplayBorderManager.Resolve(Settings.displayBorderId);
        invalidatePreparedBorderFrame();
        RequestRepaint();
    }

    /**
     * Stops background display work so the surface can be disposed cleanly.
     */
    public void Shutdown() {
        shutdown = true;
        InvalidateAsyncShaderFrames();
        shaderRenderExecutor.shutdownNow();
        try {
            shaderRenderExecutor.awaitTermination(250, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the minimum size for this component.
     *
     * @return Minimum dimension (100x100)
     */
    @Override
    public Dimension getMinimumSize() {
        return displaySpec == null ? new Dimension(160, 144) : displaySpec.minimumSize();
    }

    /**
     * Returns the preferred size of this component.
     * Based on the size of the parent container, maintaining a square shape.
     *
     * @return Preferred dimension
     */
    @Override
    public Dimension getPreferredSize() {
        return displaySpec == null ? new Dimension(640, 576) : displaySpec.preferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private void initializeFrameBuffers() {
        frontBuffer = new int[frameWidth() * frameHeight()];
        backBuffer = new int[frameWidth() * frameHeight()];
        Arrays.fill(frontBuffer, Color.BLACK.getRGB());
        Arrays.fill(backBuffer, Color.BLACK.getRGB());
        pendingFrameBuffer = new int[frameWidth() * frameHeight()];
        workerFrameBuffer = new int[frameWidth() * frameHeight()];
        Arrays.fill(pendingFrameBuffer, Color.BLACK.getRGB());
        Arrays.fill(workerFrameBuffer, Color.BLACK.getRGB());
    }

    private void initializeRenderBuffers(int nextRenderScale) {
        int clampedRenderScale = Math.max(1, nextRenderScale);
        synchronized (shaderQueueLock) {
            renderScale = clampedRenderScale;
            image = new BufferedImage(renderWidth(), renderHeight(), BufferedImage.TYPE_INT_RGB);
            paintBuffer = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            imageBuffer = paintBuffer;
            shaderSourceBuffer = new int[renderWidth() * renderHeight()];
            shaderScratchBuffer = new int[renderWidth() * renderHeight()];
            workerShaderSourceBuffer = new int[renderWidth() * renderHeight()];
            workerShaderTargetBuffer = new int[renderWidth() * renderHeight()];
            workerShaderScratchBuffer = new int[renderWidth() * renderHeight()];
            Arrays.fill(paintBuffer, Color.BLACK.getRGB());
            Arrays.fill(shaderSourceBuffer, Color.BLACK.getRGB());
            Arrays.fill(shaderScratchBuffer, Color.BLACK.getRGB());
            Arrays.fill(workerShaderSourceBuffer, Color.BLACK.getRGB());
            Arrays.fill(workerShaderTargetBuffer, Color.BLACK.getRGB());
            Arrays.fill(workerShaderScratchBuffer, Color.BLACK.getRGB());
            mainScaleRowBuffer = new int[renderWidth()];
            workerScaleRowBuffer = new int[renderWidth()];
            InvalidateAsyncShaderFramesLocked();
        }
    }

    private int frameWidth() {
        return displaySpec == null ? 160 : displaySpec.frameWidth();
    }

    private int frameHeight() {
        return displaySpec == null ? 144 : displaySpec.frameHeight();
    }

    private int renderWidth() {
        return frameWidth() * renderScale;
    }

    private int renderHeight() {
        return frameHeight() * renderScale;
    }

    private void RequestRepaint() {
        if (shutdown) {
            return;
        }
        if (!repaintQueued.compareAndSet(false, true)) {
            return;
        }

        if (SwingUtilities.isEventDispatchThread()) {
            repaintQueued.set(false);
            repaint();
            return;
        }

        SwingUtilities.invokeLater(() -> {
            repaintQueued.set(false);
            repaint();
        });
    }

    public static int DefaultDmgPreviousFrameBlendWeight() {
        return dmgPreviousFrameBlendWeight;
    }

    public static int DefaultDmgCurrentFrameBlendWeight() {
        return dmgCurrentFrameBlendWeight;
    }

    public static int DefaultCgbPreviousFrameBlendWeight() {
        return cgbPreviousFrameBlendWeight;
    }

    public static int DefaultCgbCurrentFrameBlendWeight() {
        return cgbCurrentFrameBlendWeight;
    }

    private int BlendRgb(int previousRgb, int currentRgb, int previousWeight, int currentWeight) {
        int previousRed = (previousRgb >> 16) & 0xFF;
        int previousGreen = (previousRgb >> 8) & 0xFF;
        int previousBlue = previousRgb & 0xFF;

        int currentRed = (currentRgb >> 16) & 0xFF;
        int currentGreen = (currentRgb >> 8) & 0xFF;
        int currentBlue = currentRgb & 0xFF;

        int totalWeight = Math.max(1, previousWeight + currentWeight);
        int blendedRed = ((previousRed * previousWeight) + (currentRed * currentWeight)) / totalWeight;
        int blendedGreen = ((previousGreen * previousWeight) + (currentGreen * currentWeight)) / totalWeight;
        int blendedBlue = ((previousBlue * previousWeight) + (currentBlue * currentWeight)) / totalWeight;
        return (blendedRed << 16) | (blendedGreen << 8) | blendedBlue;
    }

    private boolean RenderImageBufferLocked() {
        if (frontBuffer == null || imageBuffer == null || shaderScratchBuffer == null) {
            return false;
        }

        LoadedDisplayShader shader = ResolveActiveShader();
        EnsureRenderBuffersForShader(shader);
        if (ShouldRenderShaderAsync(shader)) {
            QueueAsyncShaderRenderLocked();
            synchronized (shaderQueueLock) {
                if (displayedShaderFrameVersion > 0) {
                    return false;
                }
            }
            prepareShaderSource(frontBuffer, paintBuffer);
            return true;
        }

        try {
            if (shader == null || "none".equals(shader.id())) {
                prepareShaderSource(frontBuffer, paintBuffer);
            } else {
                prepareShaderSource(frontBuffer, shaderSourceBuffer);
                shader.apply(shaderSourceBuffer, paintBuffer, shaderScratchBuffer, renderWidth(), renderHeight());
            }
        } catch (RuntimeException exception) {
            exception.printStackTrace();
            prepareShaderSource(frontBuffer, paintBuffer);
        }
        return true;
    }

    private boolean ShouldRenderShaderAsync(LoadedDisplayShader shader) {
        return asyncShaderRenderingEnabled
                && !shutdown
                && shader != null
                && shader.prefersAsyncRendering()
                && !"none".equals(shader.id());
    }

    private void QueueAsyncShaderRenderLocked() {
        if (pendingFrameBuffer == null || frontBuffer == null || pendingFrameBuffer.length != frontBuffer.length) {
            return;
        }

        synchronized (shaderQueueLock) {
            System.arraycopy(frontBuffer, 0, pendingFrameBuffer, 0, frontBuffer.length);
            pendingShaderFrameVersion++;
        }
        ScheduleAsyncShaderRender();
    }

    private void ScheduleAsyncShaderRender() {
        if (shutdown) {
            return;
        }
        if (!shaderRenderQueued.compareAndSet(false, true)) {
            return;
        }

        shaderRenderExecutor.execute(this::RunAsyncShaderRenderLoop);
    }

    private void RunAsyncShaderRenderLoop() {
        try {
            while (true) {
                LoadedDisplayShader shader;
                int[] workerFrame;
                int frameVersion;
                int epoch;
                int queuedRenderScale;
                synchronized (shaderQueueLock) {
                    if (shutdown
                            || pendingShaderFrameVersion == displayedShaderFrameVersion
                            || pendingFrameBuffer == null
                            || workerFrameBuffer == null
                            || workerShaderSourceBuffer == null
                            || workerShaderTargetBuffer == null
                            || workerShaderScratchBuffer == null) {
                        return;
                    }

                    workerFrame = pendingFrameBuffer;
                    pendingFrameBuffer = workerFrameBuffer;
                    workerFrameBuffer = workerFrame;
                    frameVersion = pendingShaderFrameVersion;
                    epoch = shaderRenderEpoch;
                    queuedRenderScale = renderScale;
                    shader = ResolveActiveShader();
                }

                try {
                    if (shader == null || "none".equals(shader.id())) {
                        prepareWorkerShaderSource(workerFrame, workerShaderTargetBuffer, queuedRenderScale);
                    } else {
                        prepareWorkerShaderSource(workerFrame, workerShaderSourceBuffer, queuedRenderScale);
                        shader.apply(workerShaderSourceBuffer, workerShaderTargetBuffer,
                                workerShaderScratchBuffer, frameWidth() * queuedRenderScale,
                                frameHeight() * queuedRenderScale);
                    }
                } catch (RuntimeException exception) {
                    exception.printStackTrace();
                    prepareWorkerShaderSource(workerFrame, workerShaderTargetBuffer, queuedRenderScale);
                }

                boolean repaintNow = false;
                synchronized (shaderQueueLock) {
                    if (!shutdown
                            && epoch == shaderRenderEpoch
                            && frameVersion == pendingShaderFrameVersion
                            && frameVersion > displayedShaderFrameVersion
                            && paintBuffer != null
                            && workerShaderTargetBuffer.length == paintBuffer.length) {
                        System.arraycopy(workerShaderTargetBuffer, 0, paintBuffer, 0, workerShaderTargetBuffer.length);
                        displayedShaderFrameVersion = frameVersion;
                        repaintNow = true;
                    }
                }

                if (repaintNow) {
                    RequestRepaint();
                }
            }
        } finally {
            shaderRenderQueued.set(false);
            synchronized (shaderQueueLock) {
                if (!shutdown
                        && pendingShaderFrameVersion != displayedShaderFrameVersion
                        && ShouldRenderShaderAsync(activeShader)) {
                    ScheduleAsyncShaderRender();
                }
            }
        }
    }

    private void FillDisplayBuffersLocked(int rgb) {
        if (paintBuffer != null) {
            Arrays.fill(paintBuffer, rgb);
        }
    }

    private void EnsureRenderBuffersForShader(LoadedDisplayShader shader) {
        int desiredScale = shader == null ? 1 : Math.max(1, shader.renderScale());
        int desiredLength = frameWidth() * frameHeight() * desiredScale * desiredScale;
        if (renderScale != desiredScale || imageBuffer == null || imageBuffer.length != desiredLength) {
            initializeRenderBuffers(desiredScale);
        }
    }

    private void prepareShaderSource(int[] logicalSource, int[] renderTarget) {
        mainScaleRowBuffer = prepareShaderSource(logicalSource, renderTarget, renderScale, mainScaleRowBuffer);
    }

    private void prepareShaderSource(int[] logicalSource, int[] renderTarget, int targetRenderScale) {
        mainScaleRowBuffer = prepareShaderSource(logicalSource, renderTarget, targetRenderScale, mainScaleRowBuffer);
    }

    private void prepareWorkerShaderSource(int[] logicalSource, int[] renderTarget, int targetRenderScale) {
        workerScaleRowBuffer = prepareShaderSource(logicalSource, renderTarget, targetRenderScale, workerScaleRowBuffer);
    }

    private int[] prepareShaderSource(int[] logicalSource, int[] renderTarget, int targetRenderScale,
            int[] scaleRowBuffer) {
        if (logicalSource == null || renderTarget == null) {
            return scaleRowBuffer;
        }
        int logicalWidth = frameWidth();
        int logicalHeight = frameHeight();
        if (targetRenderScale <= 1) {
            System.arraycopy(logicalSource, 0, renderTarget, 0, Math.min(logicalSource.length, renderTarget.length));
            return scaleRowBuffer;
        }

        int renderWidth = logicalWidth * targetRenderScale;
        if (scaleRowBuffer == null || scaleRowBuffer.length != renderWidth) {
            scaleRowBuffer = new int[renderWidth];
        }

        for (int y = 0; y < logicalHeight; y++) {
            int sourceRowOffset = y * logicalWidth;
            int renderRowBase = y * targetRenderScale * renderWidth;
            int destinationOffset = 0;
            for (int x = 0; x < logicalWidth; x++) {
                int rgb = logicalSource[sourceRowOffset + x];
                for (int subX = 0; subX < targetRenderScale; subX++) {
                    scaleRowBuffer[destinationOffset + subX] = rgb;
                }
                destinationOffset += targetRenderScale;
            }
            System.arraycopy(scaleRowBuffer, 0, renderTarget, renderRowBase, renderWidth);
            for (int subY = 1; subY < targetRenderScale; subY++) {
                System.arraycopy(scaleRowBuffer, 0, renderTarget,
                        renderRowBase + (subY * renderWidth), renderWidth);
            }
        }
        return scaleRowBuffer;
    }

    private LoadedDisplayShader ResolveActiveShader() {
        LoadedDisplayShader shader = activeShader;
        if (shader != null) {
            return shader;
        }
        shader = DisplayShaderManager.Resolve(Settings.displayShaderId);
        activeShader = shader;
        return shader;
    }

    private void InvalidateAsyncShaderFrames() {
        synchronized (shaderQueueLock) {
            InvalidateAsyncShaderFramesLocked();
        }
    }

    private void InvalidateAsyncShaderFramesLocked() {
        shaderRenderEpoch++;
        pendingShaderFrameVersion = 0;
        displayedShaderFrameVersion = 0;
    }

    private DisplayBorderRenderer.PreparedBorderFrame prepareBorderFrame(LoadedDisplayBorder border, int width,
            int height) {
        String borderId = border == null ? "none" : border.id();
        if (preparedBorderFrame != null
                && preparedBorderWidth == width
                && preparedBorderHeight == height
                && ((preparedBorderId == null && borderId == null)
                        || (preparedBorderId != null && preparedBorderId.equalsIgnoreCase(borderId)))) {
            return preparedBorderFrame;
        }

        preparedBorderFrame = DisplayBorderRenderer.prepare(border, width, height);
        preparedBorderId = borderId;
        preparedBorderWidth = width;
        preparedBorderHeight = height;
        return preparedBorderFrame;
    }

    private void invalidatePreparedBorderFrame() {
        preparedBorderFrame = null;
        preparedBorderId = null;
        preparedBorderWidth = -1;
        preparedBorderHeight = -1;
    }

    private void recordPaintPresentation() {
        long now = System.nanoTime();
        if (lastPaintNanos > 0L) {
            long frameIntervalNanos = now - lastPaintNanos;
            smoothedFrameIntervalNanos = smoothedFrameIntervalNanos <= 0.0
                    ? frameIntervalNanos
                    : (smoothedFrameIntervalNanos * 0.85) + (frameIntervalNanos * 0.15);
        }
        lastPaintNanos = now;

        if (statsWindowStartNanos == 0L) {
            statsWindowStartNanos = now;
        }

        statsWindowPaintCount++;
        long elapsedNanos = now - statsWindowStartNanos;
        if (elapsedNanos < 250_000_000L) {
            return;
        }

        double fps = (statsWindowPaintCount * 1_000_000_000.0) / elapsedNanos;
        double averageFrameTimeMs = smoothedFrameIntervalNanos / 1_000_000.0;
        presentationStats = new PresentationStats(fps, averageFrameTimeMs);
        statsWindowStartNanos = now;
        statsWindowPaintCount = 0;
    }
}
