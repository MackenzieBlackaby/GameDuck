package com.blackaby.Frontend.Borders;

import com.blackaby.Misc.Settings;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Renders gameplay with an optional border frame around the maximized Game Boy
 * viewport.
 */
public final class DisplayBorderRenderer {

    private static final int LOGICAL_WIDTH = 160;
    private static final int LOGICAL_HEIGHT = 144;

    private DisplayBorderRenderer() {
    }

    public record PreparedBorderFrame(BufferedImage overlayImage, Rectangle gameplayRect) {
        public PreparedBorderFrame {
            gameplayRect = gameplayRect == null ? new Rectangle() : new Rectangle(gameplayRect);
        }
    }

    public static Rectangle calculateGameplayRect(int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            return new Rectangle();
        }

        if (Settings.integerScaleWindowOutput) {
            int integerScale = Math.min(targetWidth / LOGICAL_WIDTH, targetHeight / LOGICAL_HEIGHT);
            if (integerScale >= 1) {
                int width = LOGICAL_WIDTH * integerScale;
                int height = LOGICAL_HEIGHT * integerScale;
                int x = (targetWidth - width) / 2;
                int y = (targetHeight - height) / 2;
                return new Rectangle(x, y, width, height);
            }
        }

        double scale = Math.min(
                targetWidth / (double) LOGICAL_WIDTH,
                targetHeight / (double) LOGICAL_HEIGHT);
        int width = Math.max(1, (int) Math.round(LOGICAL_WIDTH * scale));
        int height = Math.max(1, (int) Math.round(LOGICAL_HEIGHT * scale));
        int x = (targetWidth - width) / 2;
        int y = (targetHeight - height) / 2;
        return new Rectangle(x, y, width, height);
    }

    public static PreparedBorderFrame prepare(LoadedDisplayBorder border, int targetWidth, int targetHeight) {
        Rectangle gameplayRect = calculateGameplayRect(targetWidth, targetHeight);
        if (border == null || border.isNone() || border.image() == null || border.screenRect() == null
                || targetWidth <= 0 || targetHeight <= 0) {
            return new PreparedBorderFrame(null, gameplayRect);
        }

        BufferedImage overlayImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = overlayImage.createGraphics();
        paintBorder(graphics, border.image(), border.screenRect(), gameplayRect, targetWidth, targetHeight);
        graphics.dispose();
        return new PreparedBorderFrame(overlayImage, gameplayRect);
    }

    public static void paint(Graphics2D graphics, BufferedImage gameplayImage, LoadedDisplayBorder border,
            int targetWidth, int targetHeight) {
        if (graphics == null || gameplayImage == null || targetWidth <= 0 || targetHeight <= 0) {
            return;
        }

        paint(graphics, gameplayImage, prepare(border, targetWidth, targetHeight));
    }

    public static void paint(Graphics2D graphics, BufferedImage gameplayImage, PreparedBorderFrame preparedFrame) {
        if (graphics == null || gameplayImage == null || preparedFrame == null) {
            return;
        }

        paintGameplay(graphics, gameplayImage, preparedFrame.gameplayRect());
        if (preparedFrame.overlayImage() != null) {
            graphics.drawImage(preparedFrame.overlayImage(), 0, 0, null);
        }
    }

    private static void paintGameplay(Graphics2D graphics, BufferedImage gameplayImage, Rectangle gameplayRect) {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        graphics.drawImage(
                gameplayImage,
                gameplayRect.x,
                gameplayRect.y,
                gameplayRect.width,
                gameplayRect.height,
                null);
    }

    private static void paintBorder(Graphics2D graphics, BufferedImage borderImage, Rectangle sourceScreenRect,
            Rectangle targetGameplayRect, int targetWidth, int targetHeight) {
        int sourceLeft = sourceScreenRect.x;
        int sourceTop = sourceScreenRect.y;
        int sourceCenterWidth = sourceScreenRect.width;
        int sourceCenterHeight = sourceScreenRect.height;
        int sourceRight = borderImage.getWidth() - (sourceScreenRect.x + sourceScreenRect.width);
        int sourceBottom = borderImage.getHeight() - (sourceScreenRect.y + sourceScreenRect.height);

        int targetLeft = targetGameplayRect.x;
        int targetTop = targetGameplayRect.y;
        int targetCenterWidth = targetGameplayRect.width;
        int targetCenterHeight = targetGameplayRect.height;
        int targetRight = targetWidth - (targetGameplayRect.x + targetGameplayRect.width);
        int targetBottom = targetHeight - (targetGameplayRect.y + targetGameplayRect.height);

        Graphics2D borderGraphics = (Graphics2D) graphics.create();
        borderGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        borderGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        borderGraphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);

        drawSlice(borderGraphics, borderImage,
                0, 0, sourceLeft, sourceTop,
                0, 0, targetLeft, targetTop);
        drawSlice(borderGraphics, borderImage,
                sourceLeft, 0, sourceCenterWidth, sourceTop,
                targetLeft, 0, targetCenterWidth, targetTop);
        drawSlice(borderGraphics, borderImage,
                sourceLeft + sourceCenterWidth, 0, sourceRight, sourceTop,
                targetLeft + targetCenterWidth, 0, targetRight, targetTop);

        drawSlice(borderGraphics, borderImage,
                0, sourceTop, sourceLeft, sourceCenterHeight,
                0, targetTop, targetLeft, targetCenterHeight);
        drawSlice(borderGraphics, borderImage,
                sourceLeft + sourceCenterWidth, sourceTop, sourceRight, sourceCenterHeight,
                targetLeft + targetCenterWidth, targetTop, targetRight, targetCenterHeight);

        drawSlice(borderGraphics, borderImage,
                0, sourceTop + sourceCenterHeight, sourceLeft, sourceBottom,
                0, targetTop + targetCenterHeight, targetLeft, targetBottom);
        drawSlice(borderGraphics, borderImage,
                sourceLeft, sourceTop + sourceCenterHeight, sourceCenterWidth, sourceBottom,
                targetLeft, targetTop + targetCenterHeight, targetCenterWidth, targetBottom);
        drawSlice(borderGraphics, borderImage,
                sourceLeft + sourceCenterWidth, sourceTop + sourceCenterHeight, sourceRight, sourceBottom,
                targetLeft + targetCenterWidth, targetTop + targetCenterHeight, targetRight, targetBottom);

        borderGraphics.dispose();
    }

    private static void drawSlice(Graphics2D graphics, BufferedImage source,
            int sourceX, int sourceY, int sourceWidth, int sourceHeight,
            int targetX, int targetY, int targetWidth, int targetHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return;
        }

        graphics.drawImage(source,
                targetX, targetY, targetX + targetWidth, targetY + targetHeight,
                sourceX, sourceY, sourceX + sourceWidth, sourceY + sourceHeight,
                null);
    }
}
