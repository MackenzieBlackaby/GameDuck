package com.blackaby.Frontend;

import com.blackaby.Misc.UiText;

import javax.swing.JFrame;
import javax.swing.JLabel;
import java.awt.BorderLayout;

/**
 * Small information window describing the project.
 */
public final class AboutWindow extends DuckWindow {

    /**
     * Creates the about window and fills it with the current project details.
     */
    public AboutWindow() {
        super(UiText.AboutWindow.WINDOW_TITLE, 400, 300, false);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JLabel mainLabel = new JLabel(UiText.AboutWindow.HtmlBody());
        mainLabel.setVerticalAlignment(JLabel.CENTER);
        mainLabel.setHorizontalAlignment(JLabel.CENTER);
        mainLabel.setFont(Styling.menuFont);
        mainLabel.setForeground(Styling.menuForegroundColour);
        add(mainLabel, BorderLayout.CENTER);

        setVisible(true);
    }
}
