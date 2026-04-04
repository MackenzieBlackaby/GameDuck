package com.blackaby.Frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.blackaby.Frontend.Borders.DisplayBorderRenderer;
import com.blackaby.Frontend.Shaders.DisplayShader;
import com.blackaby.Frontend.Shaders.LoadedDisplayShader;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.blackaby.Misc.Settings;

class DuckDisplayTest {

    @Test
    void snapshotAndRestoreRoundTripFrontAndBackBuffers() {
        DuckDisplay display = new DuckDisplay();
        display.setPixel(0, 0, Color.RED.getRGB(), false);
        display.setPixel(1, 0, Color.GREEN.getRGB(), true);

        DuckDisplay.FrameState snapshot = display.SnapshotFrameState();

        display.clear();
        display.RestoreFrameState(snapshot);
        DuckDisplay.FrameState restored = display.SnapshotFrameState();

        assertEquals(Color.GREEN.getRGB(), restored.frontBuffer()[1]);
        assertEquals(Color.RED.getRGB(), restored.backBuffer()[0]);
    }

    @Test
    void clearResetsBuffersToBlack() {
        DuckDisplay display = new DuckDisplay();
        display.setPixel(10, 10, Color.WHITE.getRGB(), true);

        display.clear();
        DuckDisplay.FrameState snapshot = display.SnapshotFrameState();

        assertEquals(Color.BLACK.getRGB(), snapshot.frontBuffer()[(10 * 160) + 10]);
        assertEquals(Color.BLACK.getRGB(), snapshot.backBuffer()[(10 * 160) + 10]);
    }

    @Test
    void presentFrameCanBlendWithPreviousFrameForGhosting() {
        DuckDisplay display = new DuckDisplay();
        display.setPixel(0, 0, Color.BLACK.getRGB(), false);
        display.presentFrame();
        display.setPixel(0, 0, Color.WHITE.getRGB(), false);

        display.presentFrame(true);

        DuckDisplay.FrameState snapshot = display.SnapshotFrameState();
        assertEquals(0x9F9F9F, snapshot.frontBuffer()[0] & 0xFFFFFF);
        assertEquals(Color.WHITE.getRGB(), snapshot.backBuffer()[0]);
    }

    @Test
    void paintUsesConfiguredShaderWhenRenderingImageBuffer() {
        String originalShaderId = Settings.displayShaderId;
        try {
            Settings.displayShaderId = "amber_monitor";
            DuckDisplay display = new DuckDisplay(null, false);
            display.setSize(160, 144);
            int rawRgb = new Color(92, 140, 212).getRGB();
            display.setPixel(0, 0, rawRgb, false);
            display.presentFrame();

            BufferedImage canvas = new BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = canvas.createGraphics();
            display.paint(graphics);
            graphics.dispose();

            assertNotEquals(rawRgb & 0xFFFFFF, canvas.getRGB(0, 0) & 0xFFFFFF);
        } finally {
            Settings.displayShaderId = originalShaderId;
        }
    }

    @Test
    void paintUsesConfiguredBorderWhenRenderingImageBuffer() {
        String originalShaderId = Settings.displayShaderId;
        String originalBorderId = Settings.displayBorderId;
        try {
            Settings.displayShaderId = "none";
            Settings.displayBorderId = "studio_black";
            DuckDisplay display = new DuckDisplay();
            display.setSize(560, 432);
            int rawRgb = new Color(92, 140, 212).getRGB();
            display.setPixel(0, 0, rawRgb, false);
            display.presentFrame();

            BufferedImage canvas = new BufferedImage(560, 432, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = canvas.createGraphics();
            display.paint(graphics);
            graphics.dispose();

            Rectangle gameplayRect = DisplayBorderRenderer.calculateGameplayRect(560, 432);
            assertEquals(0x162030, canvas.getRGB(0, 0) & 0xFFFFFF);
            assertEquals(rawRgb & 0xFFFFFF, canvas.getRGB(gameplayRect.x + 1, gameplayRect.y + 1) & 0xFFFFFF);
        } finally {
            Settings.displayShaderId = originalShaderId;
            Settings.displayBorderId = originalBorderId;
        }
    }

    @Test
    void asyncShaderKeepsSeededShadedFrameWhileWorkerRendersNextFrame() throws Exception {
        ControlledAsyncShader shader = new ControlledAsyncShader();
        DuckDisplay display = new DuckDisplay(null, true);
        installShader(display, shader.loadedShader(2));

        display.setPixel(0, 0, Color.RED.getRGB(), false);
        display.presentFrame();

        assertEquals(Color.GREEN.getRGB(), paintBuffer(display)[0]);
        display.setPixel(0, 0, Color.BLUE.getRGB(), false);
        display.presentFrame();

        waitForLatch(shader.secondApplyStarted);
        assertEquals(Color.GREEN.getRGB(), paintBuffer(display)[0]);
        shader.releaseSecondApply.countDown();

        waitForPaint(display, Color.YELLOW.getRGB());
        assertEquals(Color.YELLOW.getRGB(), paintBuffer(display)[0]);
    }

    @Test
    void asyncShaderPublishesCompletedFrameEvenWhenNewerFrameIsQueued() throws Exception {
        ControlledAsyncShader shader = new ControlledAsyncShader();
        DuckDisplay display = new DuckDisplay(null, true);
        installShader(display, shader.loadedShader(2));

        display.setPixel(0, 0, Color.RED.getRGB(), false);
        display.presentFrame();
        assertEquals(Color.GREEN.getRGB(), paintBuffer(display)[0]);

        display.setPixel(0, 0, Color.BLUE.getRGB(), false);
        display.presentFrame();
        waitForLatch(shader.secondApplyStarted);
        assertEquals(Color.GREEN.getRGB(), paintBuffer(display)[0]);

        display.setPixel(0, 0, Color.WHITE.getRGB(), false);
        display.presentFrame();

        shader.releaseSecondApply.countDown();
        waitForPaint(display, Color.YELLOW.getRGB());
        waitForLatch(shader.thirdApplyStarted);

        shader.releaseThirdApply.countDown();
        waitForPaint(display, Color.MAGENTA.getRGB());
        assertEquals(Color.MAGENTA.getRGB(), paintBuffer(display)[0]);
    }

    @Test
    void nonAsyncShaderRendersInlineEvenWhenAsyncDisplaySupportIsEnabled() throws Exception {
        DuckDisplay display = new DuckDisplay(null, true);
        installShader(display, new InlineShader().loadedShader(1));

        display.setPixel(0, 0, Color.RED.getRGB(), false);
        display.presentFrame();

        assertEquals(Color.CYAN.getRGB(), paintBuffer(display)[0]);
    }

    @Test
    void shutdownStopsShaderExecutor() throws Exception {
        DuckDisplay display = new DuckDisplay(null, true);

        display.Shutdown();

        Field executorField = DuckDisplay.class.getDeclaredField("shaderRenderExecutor");
        executorField.setAccessible(true);
        ExecutorService executor = (ExecutorService) executorField.get(display);
        assertTrue(executor.isShutdown());
    }

    private static void installShader(DuckDisplay display, LoadedDisplayShader shader) throws Exception {
        Field activeShaderField = DuckDisplay.class.getDeclaredField("activeShader");
        activeShaderField.setAccessible(true);
        activeShaderField.set(display, shader);
    }

    private static int[] paintBuffer(DuckDisplay display) throws Exception {
        Field paintBufferField = DuckDisplay.class.getDeclaredField("paintBuffer");
        paintBufferField.setAccessible(true);
        return (int[]) paintBufferField.get(display);
    }

    private static void waitForLatch(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

    private static void waitForPaint(DuckDisplay display, int expectedRgb) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            if (paintBuffer(display)[0] == expectedRgb) {
                return;
            }
            Thread.sleep(10L);
        }
        assertEquals(expectedRgb, paintBuffer(display)[0]);
    }

    private static final class ControlledAsyncShader implements DisplayShader {
        private final AtomicInteger applyCount = new AtomicInteger();
        private final CountDownLatch secondApplyStarted = new CountDownLatch(1);
        private final CountDownLatch thirdApplyStarted = new CountDownLatch(1);
        private final CountDownLatch releaseSecondApply = new CountDownLatch(1);
        private final CountDownLatch releaseThirdApply = new CountDownLatch(1);

        LoadedDisplayShader loadedShader(int renderScale) {
            return new LoadedDisplayShader(this, "test", null, renderScale);
        }

        @Override
        public String Id() {
            return "controlled_async";
        }

        @Override
        public String DisplayName() {
            return "Controlled Async";
        }

        @Override
        public String Description() {
            return "Test shader";
        }

        @Override
        public int RenderScale() {
            return 2;
        }

        @Override
        public boolean PreferAsyncRendering() {
            return true;
        }

        @Override
        public void Apply(int[] source, int[] target, int[] scratch, int width, int height) {
            int invocation = applyCount.incrementAndGet();
            if (invocation == 1) {
                Arrays.fill(target, Color.GREEN.getRGB());
                return;
            }
            if (invocation == 2) {
                secondApplyStarted.countDown();
                await(releaseSecondApply);
                Arrays.fill(target, Color.YELLOW.getRGB());
                return;
            }
            if (invocation == 3) {
                thirdApplyStarted.countDown();
                await(releaseThirdApply);
                Arrays.fill(target, Color.MAGENTA.getRGB());
                return;
            }
            Arrays.fill(target, Color.MAGENTA.getRGB());
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting for test shader latch.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for test shader latch.", exception);
            }
        }
    }

    private static final class InlineShader implements DisplayShader {
        LoadedDisplayShader loadedShader(int renderScale) {
            return new LoadedDisplayShader(this, "test", null, renderScale);
        }

        @Override
        public String Id() {
            return "inline_test";
        }

        @Override
        public String DisplayName() {
            return "Inline Test";
        }

        @Override
        public String Description() {
            return "Inline shader";
        }

        @Override
        public boolean PreferAsyncRendering() {
            return false;
        }

        @Override
        public void Apply(int[] source, int[] target, int[] scratch, int width, int height) {
            Arrays.fill(target, Color.CYAN.getRGB());
        }
    }
}
