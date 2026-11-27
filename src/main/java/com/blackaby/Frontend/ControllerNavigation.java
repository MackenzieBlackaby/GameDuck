package com.blackaby.Frontend;

import com.blackaby.Misc.Settings;
import com.blackaby.Backend.Helpers.GUIActions;
import com.blackaby.Backend.Helpers.GUIActions.Action;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One menu input dispatcher for all GameDuck windows; Swing mutations stay on the EDT. */
final class ControllerNavigation {
    private static final ControllerNavigation INSTANCE = new ControllerNavigation();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gameduck-menu-controller"); t.setDaemon(true); return t;
    });
    private final AtomicBoolean pending = new AtomicBoolean();
    private Window active;
    private Set<String> previous = Set.of();
    private boolean armed;
    private JComponent selected;
    private JLayeredPane layer;
    private FocusOverlay overlay;
    private String repeat = "";
    private long repeatAt;
    private boolean started;

    static void install() { INSTANCE.start(); }
    private void start() {
        if (started) return;
        started = true;
        new javax.swing.Timer(50, e -> tick()).start();
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e instanceof MouseEvent m && m.getID() == MouseEvent.MOUSE_PRESSED) clearHighlight();
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    private boolean eligible(Window w) {
        if (!Settings.controllerInputEnabled || w == null || !w.isShowing()) return false;
        // Native dialogs and binding-capture dialogs retain their own input handling.
        if (w instanceof MainWindow main) return !main.GetEmulation().HasLoadedGame();
        return w instanceof DuckWindow || w instanceof MenuDialog;
    }

    private void tick() {
        Window w = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (w != active) { clearHighlight(); active = w; armed = false; previous = Set.of(); repeat = ""; }
        if (!eligible(w)) { clearHighlight(); armed = false; previous = Set.of(); return; }
        if (!pending.compareAndSet(false, true)) return;
        worker.execute(() -> {
            try {
                Set<String> buttons = ControllerInputService.Shared().PollBoundButtons();
                SwingUtilities.invokeLater(() -> {
                    try { if (active == w && eligible(w) && w.isActive()) accept(buttons); }
                    finally { pending.set(false); }
                });
            } catch (RuntimeException ex) { pending.set(false); }
        });
    }

    private void accept(Set<String> buttons) {
        if (!armed) { armed = buttons.isEmpty(); previous = buttons; return; }
        long now = System.nanoTime();
        String key = null;
        for (String b : List.of("B","A","START","SELECT","UP","DOWN","LEFT","RIGHT")) {
            if (buttons.contains(b) && !previous.contains(b)) { key = b; break; }
        }
        if (key == null && buttons.contains(repeat) && now >= repeatAt) {
            key = repeat; repeatAt = now + 110_000_000L;
        } else if (key != null) {
            repeat = isDirection(key) ? key : "";
            repeatAt = now + 380_000_000L;
        }
        previous = buttons;
        if (key != null) dispatch(key);
        if (overlay != null) { overlay.setBounds(0,0,layer.getWidth(),layer.getHeight()); overlay.repaint(); }
    }

    private static boolean isDirection(String key) {
        return List.of("UP","DOWN","LEFT","RIGHT").contains(key);
    }

    private void dispatch(String key) {
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
        if (path.length > 0) {
            if (key.equals("B")) { MenuSelectionManager.defaultManager().clearSelectedPath(); return; }
            int code = switch (key) {
                case "UP" -> KeyEvent.VK_UP; case "DOWN" -> KeyEvent.VK_DOWN;
                case "LEFT" -> KeyEvent.VK_LEFT; case "RIGHT" -> KeyEvent.VK_RIGHT;
                case "A","START" -> KeyEvent.VK_ENTER; default -> 0;
            };
            if (code != 0) {
                Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (c == null) c = active;
                MenuSelectionManager.defaultManager().processKeyEvent(new KeyEvent(c, KeyEvent.KEY_PRESSED,
                        System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED));
            }
            return;
        }
        if (key.equals("B")) {
            if (selected instanceof JComboBox<?> combo && combo.isPopupVisible()) { combo.hidePopup(); return; }
            if (active instanceof MainWindow) { clearHighlight(); return; }
            active.dispose(); return;
        }
        if (active instanceof LibraryWindow library && key.equals("SELECT")) {
            library.openControllerOptions(); return;
        }
        if (active instanceof MainWindow main && key.equals("SELECT")) {
            showMainMenu(main); return;
        }
        List<JComponent> candidates = new ArrayList<>();
        collect(((RootPaneContainer) active).getRootPane(), candidates);
        if (candidates.isEmpty()) return;
        if (selected == null || !candidates.contains(selected)) {
            JComponent preferred = active instanceof LibraryWindow library ? library.controllerGameSurface() : null;
            select(candidates.contains(preferred) ? preferred : candidates.getFirst());
            if (isDirection(key)) return;
        }
        if (key.equals("A") || key.equals("START")) { activate(selected); return; }
        if (isDirection(key)) {
            if (selected instanceof JSlider slider && (key.equals("LEFT") || key.equals("RIGHT"))) {
                int delta = slider.getMinorTickSpacing() > 0 ? slider.getMinorTickSpacing() : 1;
                slider.setValue(slider.getValue() + (key.equals("RIGHT") ? delta : -delta)); return;
            }
            if (selected instanceof JComboBox<?> combo && combo.isPopupVisible()) {
                if (key.equals("UP") || key.equals("DOWN")) {
                    int i = combo.getSelectedIndex() + (key.equals("DOWN") ? 1 : -1);
                    if (i >= 0 && i < combo.getItemCount()) combo.setSelectedIndex(i);
                }
                return;
            }
            if (selected instanceof JTabbedPane tabs && (key.equals("LEFT") || key.equals("RIGHT"))) {
                int i = tabs.getSelectedIndex() + (key.equals("RIGHT") ? 1 : -1);
                if (i >= 0 && i < tabs.getTabCount()) tabs.setSelectedIndex(i);
                return;
            }
            if (active instanceof LibraryWindow library && selected == library.controllerGameSurface()) {
                if (key.equals("LEFT") || key.equals("RIGHT") || key.equals("UP") || key.equals("DOWN")) {
                    if (library.moveControllerGameSelection(key)) return;
                }
            } else if (selected instanceof JList<?> list && (key.equals("UP") || key.equals("DOWN"))) {
                int i = list.getSelectedIndex() + (key.equals("DOWN") ? 1 : -1);
                if (i >= 0 && i < list.getModel().getSize()) { list.setSelectedIndex(i); list.ensureIndexIsVisible(i); return; }
            }
            JComponent next = directional(candidates, selected, key);
            if (next != null) select(next);
        }
    }

    private void collect(Component c, List<JComponent> out) {
        if (!c.isVisible() || !c.isEnabled()) return;
        if (c instanceof JComponent j && (j instanceof AbstractButton && !(j instanceof JMenuItem && !(j instanceof JMenu))
                || j instanceof JComboBox<?> || j instanceof JSlider || j instanceof JSpinner
                || j instanceof JList<?> || j instanceof JTable || j instanceof JTree
                || j instanceof JTabbedPane || j instanceof JTextComponent && ((JTextComponent)j).isEditable()
                || Boolean.TRUE.equals(j.getClientProperty("gameduck.controller.surface")))) {
            if (j.isShowing() && j.getWidth() > 0 && j.getHeight() > 0 && (j.isFocusable() || j instanceof JMenu)) out.add(j);
            if (!(j instanceof JTabbedPane)) return;
        }
        if (c instanceof Container container) for (Component child : container.getComponents()) collect(child, out);
    }

    private static Rectangle bounds(JComponent c, Component target) {
        if (c instanceof JTabbedPane tabs && tabs.getSelectedIndex() >= 0) {
            Rectangle r = tabs.getBoundsAt(tabs.getSelectedIndex());
            if (r != null) return SwingUtilities.convertRectangle(c, r, target);
        }
        return SwingUtilities.convertRectangle(c, new Rectangle(0,0,c.getWidth(),c.getHeight()),target);
    }

    private JComponent directional(List<JComponent> candidates, JComponent from, String key) {
        Rectangle a = bounds(from, ((RootPaneContainer)active).getRootPane());
        JComponent best = null; double score = Double.MAX_VALUE;
        for (JComponent c : candidates) {
            if (c == from) continue;
            Rectangle b = bounds(c, ((RootPaneContainer)active).getRootPane());
            double dx = b.getCenterX()-a.getCenterX(), dy=b.getCenterY()-a.getCenterY();
            double forward = switch(key) {case "LEFT" -> -dx; case "RIGHT" -> dx; case "UP" -> -dy; default -> dy;};
            if (forward <= 1) continue;
            double cross = key.equals("LEFT") || key.equals("RIGHT") ? Math.abs(dy) : Math.abs(dx);
            double cost = forward + cross*3;
            if (cost < score) { score=cost; best=c; }
        }
        return best;
    }

    private void select(JComponent c) {
        selected=c;
        c.requestFocusInWindow();
        if (!(c instanceof JTabbedPane)) c.scrollRectToVisible(new Rectangle(0,0,c.getWidth(),c.getHeight()));
        if (overlay == null) {
            layer=((RootPaneContainer)active).getLayeredPane();
            overlay=new FocusOverlay();
            layer.add(overlay, Integer.valueOf(JLayeredPane.DRAG_LAYER + 1));
        }
        overlay.setBounds(0,0,layer.getWidth(),layer.getHeight());
        overlay.repaint();
    }

    private void activate(JComponent c) {
        if (active instanceof LibraryWindow library && c == library.controllerGameSurface()) { library.launchControllerGame(); return; }
        if (c instanceof JMenu menu) {
            menu.doClick();
            MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{menu,menu.getPopupMenu()});
        } else if (c instanceof AbstractButton button) button.doClick();
        else if (c instanceof JComboBox<?> combo) combo.setPopupVisible(!combo.isPopupVisible());
        else if (c instanceof JSpinner spinner) { Object next=spinner.getNextValue(); if(next!=null) spinner.setValue(next); }
        else if (c instanceof JTextField field) field.postActionEvent();
        else if (c instanceof JTabbedPane tabs) {
            List<JComponent> children=new ArrayList<>();
            collect(tabs.getSelectedComponent(),children);
            if (!children.isEmpty()) select(children.getFirst());
        } else {
            javax.swing.Action action=c.getActionMap().get(c.getInputMap().get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0)));
            if(action!=null) action.actionPerformed(new ActionEvent(c,ActionEvent.ACTION_PERFORMED,"controller"));
        }
    }

    private void clearHighlight() {
        selected=null;
        if (overlay!=null) { layer.remove(overlay); layer.repaint(); overlay=null; layer=null; }
    }

    private final class FocusOverlay extends JComponent {
        @Override public boolean contains(int x,int y) { return false; }
        @Override protected void paintComponent(Graphics g) {
            if(selected==null || !selected.isShowing()) return;
            Graphics2D p=(Graphics2D)g.create();
            try {
                Rectangle r=bounds(selected,this);
                Rectangle visible=SwingUtilities.convertRectangle(selected, selected.getVisibleRect(), this);
                r=r.intersection(visible);
                p.setColor(Styling.accentColour);
                p.setStroke(new BasicStroke(3f));
                if (!r.isEmpty()) p.drawRoundRect(r.x+2,r.y+2,Math.max(0,r.width-5),Math.max(0,r.height-5),8,8);
                String hint=active instanceof LibraryWindow ? "A Select   B Back   Select Options" : "A Select   B Back   D-pad Navigate";
                p.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));
                int width=p.getFontMetrics().stringWidth(hint)+20;
                int x=Math.max(0,getWidth()-width-12), y=Math.max(0,getHeight()-32);
                p.setColor(new Color(0,0,0,220)); p.fillRoundRect(x,y,width,24,8,8);
                p.setColor(Color.WHITE); p.drawString(hint,x+10,y+16);
            } finally {p.dispose();}
        }
    }

    static void showMainMenu(MainWindow main) {
        if (!main.isActive()) return;
        MenuDialog dialog=new MenuDialog(main,"GameDuck controller menu");
        boolean loaded=main.GetEmulation().HasLoadedGame();
        dialog.addButton("Resume / Back",dialog::dispose);
        if(loaded) {
            dialog.addAction(main,"Pause / resume emulation",Action.PAUSEGAME);
            dialog.addAction(main,"Save state",Action.SAVESTATE);
            dialog.addAction(main,"Load state",Action.LOADSTATE);
        }
        dialog.addAction(main,"Game library",Action.LIBRARY);
        dialog.addAction(main,"Settings",Action.OPTIONS);
        dialog.addAction(main,"Toggle fullscreen",Action.FULLSCREEN);
        if(loaded) dialog.addAction(main,"Close game",Action.CLOSEGAME);
        dialog.open();
    }

    static final class MenuDialog extends JDialog {
        private final JPanel buttons=new JPanel();
        MenuDialog(Window owner,String title) {
            super(owner,title,ModalityType.MODELESS);
            buttons.setLayout(new BoxLayout(buttons,BoxLayout.Y_AXIS));
            buttons.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
            buttons.setBackground(Styling.surfaceColour);
            setContentPane(buttons); setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        }
        void addButton(String name,Runnable action) {
            JButton b=WindowUiSupport.createSecondaryButton(name,Styling.accentColour,Styling.surfaceBorderColour);
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(320,40));
            b.addActionListener(e->action.run()); buttons.add(b); buttons.add(Box.createVerticalStrut(6));
        }
        void addAction(MainWindow main,String title,Action action) {
            addButton(title,()->{dispose(); new GUIActions(main,action).actionPerformed(new ActionEvent(main,ActionEvent.ACTION_PERFORMED,title));});
        }
        void open() { pack(); setMinimumSize(new Dimension(320,getHeight())); setLocationRelativeTo(getOwner()); setVisible(true); }
    }
}
