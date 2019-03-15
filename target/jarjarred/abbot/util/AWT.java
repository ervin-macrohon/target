package abbot.util;

import abbot.InterruptedAbbotException;

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.*;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.text.View;

import sun.awt.AppContext;
import abbot.Log;
import abbot.Platform;
import abbot.finder.*;
import abbot.finder.matchers.ClassMatcher;
import abbot.tester.*;
import abbot.tester.Robot;

/** Various AWT utilities to facilitate component-oriented operations. */

public class AWT {

    public static int POPUP_TIMEOUT = 5000;
        
    private static Hierarchy hierarchy = new AWTHierarchy();

    static {
        String to = System.getProperty("abbot.finder.popup_timeout");
        if (to != null) {
            try {
                POPUP_TIMEOUT = Integer.parseInt(to);
            }
            catch(Exception e) {
            }
        }
    }

    private static final Point RELATIVE_OFFSET = new Point(10, 10);

    /** Set this client property on components which contain a heavyweight 
     * component.  Use {@link Boolean#TRUE} as the value. 
     */
    public final static String CONTAINS_HEAVYWEIGHT_COMPONENT = "containsHeavyweightComponent";
    /** Offset from the position of the currently active window to the 
     * position of a new window.
     */
    public static final Point DEFAULT_CASCADE = new Point(15, 15);

    /** Return whether the given {@link Point} is visible on any screen. */
    public static boolean onScreen(Point p) {
        GraphicsEnvironment env = 
            GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = env.getScreenDevices();
        for (int i=0;i < gs.length;i++) {
            GraphicsConfiguration[] gc = gs[i].getConfigurations();
            for (int j=0;j < gc.length;j++) {
                Rectangle r = getVisibleBounds(gc[j]);
                if (r.contains(p))
                    return true;
            }
        }
        return false;
    }
    
    /** Returns whether one of the upper corners of the given window is 
     * accessible.
     */
    public static boolean onScreen(Window w) {
        return onScreen(w.getLocation())
            || onScreen(new Point(w.getX() + w.getWidth()-1, w.getY()));
    }

    /** Returns the GraphicsConfiguration which contains the given point,
     * or null if none. 
     */ 
    public static GraphicsConfiguration getGraphicsConfiguration(Point p) {
        GraphicsEnvironment env = 
            GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = env.getScreenDevices();
        for (int i=0;i < gs.length;i++) {
            GraphicsConfiguration[] gc = gs[i].getConfigurations();
            Rectangle bounds = getVisibleBounds(gc[i]);
            if (bounds.contains(p)) {
                return gc[i];
            }
        }
        return null;
    }

    /** Returns a Rectangle spanning all screens.  Note that not all pixels
     * within the rectangle are necessarily on a display.<p>  
     * Includes any toolbar/dashboard regions.
     */
    public static Rectangle getVirtualDisplayBounds(boolean includeInsets) {
        Rectangle bounds = null;
        GraphicsEnvironment env = 
            GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = env.getScreenDevices();
        for (int i=0;i < gs.length;i++) {
            GraphicsConfiguration[] gc = gs[i].getConfigurations();
            for (int j=0;j < gc.length;j++) {
                Rectangle r = includeInsets ? gc[j].getBounds() : getVisibleBounds(gc[j]);
                if (bounds == null)
                    bounds = r;
                else 
                    bounds = bounds.union(r);
            }
        }
        return bounds;
    }
    
    /** Return the visible bounds for the graphics configuration.  This will
     * exclude any permanent menu bar or dashboard decorations.
     */
    public static Rectangle getVisibleBounds(GraphicsConfiguration gc) {
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        Rectangle r = gc.getBounds();
        r.x += insets.left;
        r.y += insets.top;
        r.width -= insets.left + insets.right;
        r.height -= insets.top + insets.bottom;
        return r;
    }

    private static final int SCREEN_MARGIN = 10;
    /** Ensure the given window is visible on screen. */
    public static void ensureOnScreen(Window w) {
        // One of the upper corners of the window needs to be on screen
        if (!onScreen(w)) {
            Rectangle bounds = getVirtualDisplayBounds(false);
            int x = w.getX();
            int y = w.getY();
            if (w.getX() + w.getWidth() < bounds.x + SCREEN_MARGIN) {
                x = bounds.x + SCREEN_MARGIN - w.getWidth();
            }
            if (w.getX() > bounds.x + bounds.width + SCREEN_MARGIN) {
                x = bounds.x + bounds.width - SCREEN_MARGIN;
            }
            if (w.getY() < bounds.y + SCREEN_MARGIN) {
                y = bounds.y + SCREEN_MARGIN;
            }
            if (w.getY() > bounds.y + bounds.height + SCREEN_MARGIN) {
                y = bounds.y + bounds.height - SCREEN_MARGIN;
            }
            w.setLocation(x, y);
            if (!onScreen(w)) {
                // If still not on screen, just center it on the default screen
                centerOnScreen(w);
            }
        }
    }

    /** Set the position of the window in a platform-specific manner.
     * Uses Window.setLocationByPlatform if available, otherwise
     * centers on screen. 
     */
    public static void setLocationByPlatform(Window w) {
        try {
            Method m = Window.class.getDeclaredMethod("setLocationByPlatform", 
                                                      new Class[] { boolean.class });
            m.invoke(w, new Object[] { Boolean.TRUE });
        }
        catch(Exception e) {
            centerOnScreen(w);
        }
    }
    
    /** Ensure the given component renders its initial HTML wrapped at the 
     * given preferred width.
     */
    public static void setHTMLPreferredWidth(JComponent c, int width) {
        // Something of a hack.  This property gets set prior to setting
        // the component's initial size, so we tweak the view to have 
        // a preferred x-axis span to what we want, restoring it after
        // the initial size has been set.
        c.addPropertyChangeListener("ancestor", new PropertyChangeListener() {
            final int PREF_WIDTH = 200;
            public void propertyChange(PropertyChangeEvent e) {
                JComponent c = (JComponent)e.getSource();
                c.removePropertyChangeListener("ancestor", this);
                final View view = (View)c.getClientProperty("html");
                if (view != null) {
                    final float prefx = view.getPreferredSpan(View.X_AXIS);
                    final float prefy = view.getPreferredSpan(View.Y_AXIS);
                    view.setSize(Math.min(PREF_WIDTH, prefx), prefy);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            view.setSize(prefx, prefy);
                        }
                    });
                }
            }
        });
    }
    
    /** Combine the two colors with equal weight. */
    public static Color combine(Color c1, Color c2) {
        return combine(c1, c2, 0.5f);
    }
    /** Combine the two colors, giving the requested weight to the first.
     */
    public static Color combine(Color c1, Color c2, float weight) {
        float w1 = (float)Math.max(Math.min(weight, 1.0), 0);
        float w2 = 1.0f - w1;
        return new Color((int)(c1.getRed()*w1 + c2.getRed()*w2),
                         (int)(c1.getGreen()*w1 + c2.getGreen()*w2),
                         (int)(c1.getBlue()*w1 + c2.getBlue()*w2),
                         (int)(c1.getAlpha()*w1 + c2.getAlpha()*w2));
    }

    /** Returns true if there is an active menu on the JFrame (if any)
        containing the given component. */
    public static boolean isMenuActive(Component c) {
        Frame f = getFrame(c);
        if (f instanceof JFrame) {
            JFrame frame = (JFrame)f;
            JMenuBar mb = frame.getJMenuBar();
            if (mb != null) {
                for (int i=0;i < mb.getMenuCount();i++) {
                    JMenu menu = mb.getMenu(i);
                    if (menu == null)
                        continue;
                    if (menu.isSelected() || menu.isPopupMenuVisible())
                        return true;
                }
            }
        }

        return false;
    }

    /**
     * Display a frame relative to the given component.
     */
    public static void showFrameRelative(Frame frame, Component relativeTo) {
        moveFrameRelativeTo(frame, relativeTo);
        if (frame.getState() == Frame.ICONIFIED) {
            frame.setState(Frame.NORMAL);
        }
        frame.show();
    }

    /** Center the given {@link Window} on the default screen. */
    public static void centerOnScreen(Window window) {
        Point center = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
        Rectangle max = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int x = Math.max(center.x - Math.round(window.getWidth()/2f), max.x);
        int y = Math.max(center.y - Math.round(window.getHeight()/2f), max.y);
        window.setLocation(new Point(x, y));
    }

    /** Center on the specified frame. */
    public static void centerOnFrame(Window window, Frame frame) {
        int ww = window.getWidth();
        int wh = window.getHeight();
        
        int fw = frame.getWidth();
        int fh = frame.getHeight();
        
        int x = (int)Math.round((double)(fw - ww) / 2);
        int y = (int)Math.round((double)(fh - wh) / 2);
        
        Point location = frame.getLocationOnScreen();
        location.translate(x, y);
        window.setLocation(location);
        ensureOnScreen(window);
    }

    public static void moveFrameRelativeTo(Frame frame, Component relativeTo) {
        Point location = RELATIVE_OFFSET;
        Window reference = null;
        if (relativeTo != null) {
            reference = getWindow(relativeTo);
        }
        if (reference != null) {
            location = reference.getLocationOnScreen();
            location.translate(RELATIVE_OFFSET.x, RELATIVE_OFFSET.y);
        }
        frame.setLocation(location);
        ensureOnScreen(frame);
    }
    
    /** Return whether the given component either has focus or is the ancestor
     * of the focused component.
     */
    public static boolean containsFocus(Component c) {
        KeyboardFocusManager mgr = 
            KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Component owner = mgr.getFocusOwner();
        return owner != null
            && SwingUtilities.isDescendingFrom(owner, c);
    }
    
    /** NOTE: on pointer-focused systems, the frontmost window is not 
     * necessarily the one with focus.
     */
    public static Window getFocusedWindow() {
        KeyboardFocusManager mgr = 
            KeyboardFocusManager.getCurrentKeyboardFocusManager();
        return mgr.getFocusedWindow();
    }
    
    /** NOTE: NOT necessarily the same thing as the focused window. */
    public static Window getActiveWindow() {
        KeyboardFocusManager mgr = 
            KeyboardFocusManager.getCurrentKeyboardFocusManager();
        return mgr.getActiveWindow();
    }

    private static boolean containsHeavyweightComponent(Component root) {
        if (root instanceof Container) {
            if (root instanceof JComponent
                && Boolean.TRUE.equals(((JComponent)root).getClientProperty(CONTAINS_HEAVYWEIGHT_COMPONENT))) {
                return true;
            }
            Container c = (Container)root;
            for (int i=0;i < c.getComponentCount();i++) {
                if (containsHeavyweightComponent(c.getComponents()[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    /** @return whether the hierarchy within which the given Component sits
     * contains a heavyweight component. 
     */
    public static boolean hierarchyHasHeavyweightComponent(Component base) {
        Window w = getWindow(base);
        return containsHeavyweightComponent(w);
    }

    /** Return the {@link Frame} corresponding to the given object. */
    public static Frame getFrame(Object o) {
        Window w = getWindow(o);
        while (!(w instanceof Frame) && w != null) {
            w = (Window)w.getParent();
        }
        return w instanceof Frame ? (Frame)w : JOptionPane.getRootFrame();
    }
    
    /** Return the window corresponding to the given object. */
    public static Window getWindow(Object o) {
        if (o instanceof Component) {
            Component c = (Component)o;
            while (c instanceof JMenuItem) {
                c = c.getParent();
            }
            if (c instanceof JPopupMenu) {
                return getWindow(((JPopupMenu)c).getInvoker());
            }
            return c instanceof Window 
                ? (Window)c : SwingUtilities.getWindowAncestor(c);
        }
        return JOptionPane.getRootFrame();
    }

    /** Return a copy of the given color with a new alpha component. */
    public static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    /** Find the first instance of the given class under the given component,
     * or null if none found.
     */
    public static Component find(Component root, final Class type) {
        return find(root, new ComponentPredicate() {
            public boolean evaluate(Component o) {
                return o != null && type.isAssignableFrom(o.getClass());
            }
        });
    }
    
    /** Cascade the given window based on the currently active {@link Frame}. */
    public static void cascade(Window w) {
        cascade(w, DEFAULT_CASCADE.x, DEFAULT_CASCADE.y);
    }

    /** Cascade the given window based on the currently active {@link Frame}. */
    public static void cascade(Window w, int xoff, int yoff) {
        // Make sure we get a frame, not a dialog
        Frame f = getFrame(getActiveWindow());
        if (f != null && f.isShowing()) {
            w.setLocation(f.getX() + xoff, f.getY() + yoff);
            ensureOnScreen(w);
        }
        else {
            centerOnScreen(w);
        }
    }

    public static interface ComponentPredicate {
        /** Return whether the given component is the desired one. */
        boolean evaluate(Component c);
    }
    
    /** Find the first component matching the given predicate, 
     * or null if none found.
     */
    public static Component find(Component root, ComponentPredicate test) {
        if (test.evaluate(root))
            return root;
        if (root instanceof Container) {
            Component[] kids = ((Container)root).getComponents();
            for (int i=0;i < kids.length;i++) {
                Component c = find(kids[i], test);
                if (c != null)
                    return c;
            }
        }
        return null;
    }

    /** Find the first instance of {@link RootPaneContainer} in the given
     * container.  Basically finds applets.
     */
    public static RootPaneContainer findRootPaneContainer(Container c) {
        if (c instanceof RootPaneContainer) {
            return (RootPaneContainer)c;
        }
        Component[] kids = c.getComponents();
        for (int i=0;i < kids.length;i++) {
            if (kids[i] instanceof RootPaneContainer)
                return (RootPaneContainer)kids[i];
            if (kids[i] instanceof Container) {
                RootPaneContainer rcp = findRootPaneContainer((Container)kids[i]);
                if (rcp != null)
                    return rcp;
            }
        }
        return null;
    }

    private AWT() { }

    /** Return whether the given Component has only its default name set. */
    public static boolean hasDefaultName(Component c) {
        String name = getName(c);
        if (name == null)
            return true;

        if (c instanceof JComponent) {
            return (c instanceof JLayeredPane
                    && "null.layeredPane".equals(name))
                || (c instanceof JPanel
                    && ("null.glassPane".equals(name)
                        || "null.contentPane".equals(name)));
        }
        
        return (c instanceof Button
                && Regexp.stringMatch("button[0-9]+", name))
            || (c instanceof Canvas
                && Regexp.stringMatch("canvas[0-9]+", name))
            || (c instanceof Checkbox
                && Regexp.stringMatch("checkbox[0-9]+", name))
            || (c instanceof Choice
                && Regexp.stringMatch("choice[0-9]+", name))
            || (c instanceof Dialog
                && Regexp.stringMatch("dialog[0-9]+", name))
            || (c instanceof FileDialog
                && Regexp.stringMatch("filedlg[0-9]+", name))
            || (c instanceof Frame
                && Regexp.stringMatch("frame[0-9]+", name))
            || (c instanceof java.awt.List
                && Regexp.stringMatch("list[0-9]+", name))
            || (c instanceof Label
                && Regexp.stringMatch("label[0-9]+", name))
            || (c instanceof Panel
                && Regexp.stringMatch("panel[0-9]+", name))
            || (c instanceof Scrollbar
                && Regexp.stringMatch("scrollbar[0-9]+", name))
            || (c instanceof ScrollPane
                && Regexp.stringMatch("scrollpane[0-9]+", name))
            || (c instanceof TextArea
                && Regexp.stringMatch("text[0-9]+", name))
            || (c instanceof TextField
                && Regexp.stringMatch("textfield[0-9]+", name))
            || (c instanceof Window
                && Regexp.stringMatch("win[0-9]+", name));
    }

    /** Ensure the given action happens on the event dispatch thread.  Any
     * component modifications must be invoked this way.
     */
    public static void invokeAndWait(Runnable action) {
        if (EventQueue.isDispatchThread()) {
            action.run();
        }
        else {
            try {
                EventQueue.invokeAndWait(action);
            }
            catch(InterruptedException ie) {
                Log.warn(ie);
                throw new InterruptedAbbotException("Interrupted during invokeAndWait");
            }
            catch(java.lang.reflect.InvocationTargetException ite) {
                Log.warn(ite);
            }
        }
    }

    /** Ensure the given action happens on the event dispatch thread.  Any
     * component modifications must be invoked this way.  Note that this is
     * <b>not</b> the same as EventQueue.invokeLater, since if the current
     * thread is the dispatch thread, the action is invoked immediately.
     */
    public static void invokeAction(Runnable action) {
        if (EventQueue.isDispatchThread()) {
            action.run();
        }
        else {
            EventQueue.invokeLater(action);
        }
    }

    /** Returns whether the menu component is on a MenuBar. */
    public static boolean isOnMenuBar(MenuComponent mc) {
        if (mc instanceof MenuBar)
            return true;
        return mc.getParent() instanceof MenuComponent
            && isOnMenuBar((MenuComponent)mc.getParent());
    }

    /** Returns the invoker, if any, of the given AWT menu component.  Returns
        null if the menu component is not attached to anything, or if it is
        within a MenuBar hierarchy.
    */
    public static Component getInvoker(MenuComponent mc) {
        if (isOnMenuBar(mc))
            return null;
        MenuContainer parent = mc.getParent();
        while (parent instanceof MenuComponent) {
            parent = ((MenuComponent)parent).getParent();
        }
        return parent instanceof Component ? (Component)parent : null;
    }

    /** Returns the invoker, if any, of the given component.  Returns null if
     * the component is not on a popup of any sort.
     */
    public static Component getInvoker(Component comp) {
        if (comp instanceof JPopupMenu)
            return ((JPopupMenu)comp).getInvoker();
        comp = comp.getParent();
        return comp != null ? getInvoker(comp) : null;
    }

    /** Similar to SwingUtilities.getWindowAncestor(), but returns the
     * component itself if it is a Window, or the invoker's window if on a
     * popup.
     */
    public static Window getWindow(Component comp) {
        if (comp == null)
            return null;
        if (comp instanceof Window)
            return (Window)comp;
        if (comp instanceof MenuElement) {
            Component invoker = getInvoker(comp);
            if (invoker != null)
                return getWindow(invoker);
        }
        return getWindow(hierarchy.getParent(comp));
    }

    /** Returns whether there is an AWT popup menu currently showing. */
    public static boolean isAWTPopupMenuBlocking() {
        // For now, just do a quick check to see if a PopupMenu is active on
        // w32.  Extend it if we find other common situations that might block
        // the EDT, but for now, keep it simple and restricted to what we've
        // run into.
        // NOTE: technically, the popup menu blocks the toolkit, not the EDT,
        // but in practice it doesn't make much difference since the EDT 
        // depends heavily on the toolkit.
        return Bugs.showAWTPopupMenuBlocks() && isAWTTreeLockHeld();
    }
    
    /** Returns whether the AWT Tree Lock is currently held. */
    private static boolean isAWTTreeLockHeld() {
        return isAWTTreeLockHeld(Toolkit.getDefaultToolkit().getSystemEventQueue());
    }

    /** Returns whether the AWT Tree Lock is currently held. */
    public static boolean isAWTTreeLockHeld(EventQueue eq) {
        Frame[] frames = Frame.getFrames();
        if (frames.length == 0)
            return false;

        // hack based on 1.4.2 java.awt.PopupMenu implementation,
        // which blocks the event dispatch thread while the popup is visible,
        // while holding the AWT tree lock

        // Start another thread which attempts to get the tree lock
        // If it can't get the tree lock, then there is a popup active in the
        // current tree.
        // Any component can provide the tree lock
        ThreadStateChecker checker =
            new ThreadStateChecker(frames[0].getTreeLock());
        try {
            checker.start();
            // Wait a little bit for the checker to finish
            int delay = Properties.getProperty("abbot.treelock_wait", 100, 0, 60000);
            if (checker.isAlive()) 
                checker.join(delay);
            return checker.isAlive();
        }
        catch(InterruptedException e) {
            throw new InterruptedAbbotException("Interrupted when checking for tree lock");
       }
    }

    /** Ensure any extant AWT popup is dismissed.  This should only be called
     * from off the EDT, since (at least on w32) the EDT is blocked while
     * an AWT popup is showing.
     */
    // FIXME this sometimes causes the windows menu to display on w32
    public static void dismissAWTPopup() {
        // If we're on the EDT, we know a priori there is no AWT popup
        if (SwingUtilities.isEventDispatchThread())
            return;
        java.awt.Robot robot = Robot.getRobot();
        if (robot != null) {
            Component c = getFocusOwner();
            if (c != null) {
                Window w = getWindow(c);
                if (w != null && w.isShowing()) {
                    robot.keyPress(KeyEvent.VK_ESCAPE);
                    robot.keyRelease(KeyEvent.VK_ESCAPE);
                }
            }
        }
        else {
            Log.warn("The current system configuation can not automatically dismiss an AWT popup");
        }
    }

    /** Returns whether the given MenuComponent is on a top-level AWT popup
        (that is, <i>not</i> under a MenuBar. 
    */
    public static boolean isOnPopup(MenuComponent mc) {
        MenuContainer parent = mc.getParent();
        while (parent instanceof MenuComponent) {
            if (parent instanceof MenuBar)
                return false;
            parent = ((MenuComponent)parent).getParent();
        }
        return true;
    }

    /** Returns whether the given component is on a top-level popup.  A
     * top-level popup is one generated by a popup trigger, which means popups
     * generated from a JMenu are not included.
     */
    public static boolean isOnPopup(Component comp) {
        boolean isWrapper = isTransientPopup(comp);
        Component invoker = getInvoker(comp);
        boolean isOnJMenu = invoker instanceof JMenu
            && invoker.getParent() instanceof JMenuBar;
        return isWrapper || (invoker != null && !isOnJMenu);
    }

    /** Returns whether the given component is a heavyweight popup, that is, a
        container for a JPopupMenu that is implemented with a heavyweight
        component (usually a Window).
    */
    public static boolean isHeavyweightPopup(Component c) {
        if (c instanceof Window) {
           
            if (!(c instanceof Dialog) && !(c instanceof Frame)) {
               String name = getName(c);
               String cname = c.getClass().getName();
               return ("###overrideRedirect###".equals(name)
                       || "###focusableSwingPopup###".equals(name)
                       // These classes are known to be heavyweight popups
                       // javax.swing.DefaultPopupFactory$WindowPopup (1.3)
                       || cname.indexOf("PopupFactory$WindowPopup") != -1
                       // javax.swing.Popup.HeavyWeightWindow (1.4)
                       || cname.indexOf("HeavyWeightWindow") != -1);              
            }
            
            if (c instanceof Window && c instanceof RootPaneContainer &&  
                ((RootPaneContainer)c).getRootPane()!=null &&
                ((RootPaneContainer)c).getRootPane().getClientProperty("abbot.customPopup")!=null)
            {
               return true;
            }
        }
        return false;
    }

    // Work around some components throwing exceptions if getName is 
    // called prematurely
    private static String getName(Component c) {
        try {
            return c.getName();
        }
        catch(Throwable e) {
            Log.warn(e);
            return null;
        }
    }

    /** Returns whether the given component is a lightweight popup, that is, a
        container for a JPopupMenu that is implemented with a lightweight
        component (usually JPanel).
    */
    public static boolean isLightweightPopup(Component c) {
        if (c instanceof JPanel) {
            Window w = SwingUtilities.getWindowAncestor(c);
            if (w != null && isHeavyweightPopup(w))
                return false;
            JPanel panel = (JPanel)c;
            Container parent = panel.getParent();
            if (parent != null) {
                if (parent instanceof JLayeredPane) {
                    int layer = JLayeredPane.POPUP_LAYER.intValue();
                    if (JLayeredPane.getLayer(panel) == layer)
                        return true;
                }
            }
            return panel.getComponentCount() == 1
                && panel.getComponents()[0] instanceof JPopupMenu;
        }
        return false;
    }

    /** Returns whether the given Component is the content pane for a
        {@link RootPaneContainer}. 
        @see javax.swing.RootPaneContainer#getContentPane
    */
    public static boolean isContentPane(Component c) {
        if (c.getParent() instanceof JLayeredPane) {
            JLayeredPane p = (JLayeredPane)c.getParent();
            if (p.getParent() instanceof JRootPane) {
                return ((JRootPane)p.getParent()).getContentPane() == c;
            }
            int layer = JLayeredPane.FRAME_CONTENT_LAYER.intValue();
            return p.getLayer(c) == layer
                && !(c instanceof JMenuBar);
        }
        return false;
    }

    /** Returns whether the given Component is the Glass Pane for a
        {@link JRootPane}. 
        @see javax.swing.JRootPane#getGlassPane
    */
    public static boolean isGlassPane(Component c) {
        if (c.getParent() instanceof JRootPane) {
            JRootPane p = (JRootPane)c.getParent();
            return p.getGlassPane() == c;
        }
        return false;
    }


    /** Return whether the given component is part of the transient wrapper
     * around a popup.
     */
    public static boolean isTransientPopup(Component c) {
        return isLightweightPopup(c) 
            || isHeavyweightPopup(c);
    }

    private static boolean containsToolTip(Component c) {
        if (c instanceof JToolTip)
            return true;
        if (c instanceof Container) {
            Component[] kids = ((Container)c).getComponents();
            for (int i=0;i < kids.length;i++) {
                if (containsToolTip(kids[i]))
                    return true;
            }
        }
        return false;
    }

    /** Return whether the given component is part of the transient wrapper
        around a tooltip.
    */
    public static boolean isToolTip(Component c) {
        return isTransientPopup(c)
            && containsToolTip(c);
    }

    /** Return whether the given component is part of an internal frame's LAF
        decoration.
    */
    public static boolean isInternalFrameDecoration(Component c) {
        Component parent = c.getParent();
        return (parent instanceof JInternalFrame
                && !(c instanceof JRootPane))
            || (parent != null
                && (parent.getParent() instanceof JInternalFrame)
                && (!(parent instanceof JRootPane)));
    }

    // Macintosh *used* to map button2 to the popup trigger (circa 1.3)
    // Not clear when this changed
    private static final boolean POPUP_ON_BUTTON2 = false;

    /** Returns the InputEvent mask for the popup trigger button. */
    public static int getPopupMask() {
        return POPUP_ON_BUTTON2
            ? InputEvent.BUTTON2_MASK : InputEvent.BUTTON3_MASK;
    }
    /** Returns the InputEvent mask for the tertiary button. */
    public static int getTertiaryMask() {
        return POPUP_ON_BUTTON2
            ? InputEvent.BUTTON3_MASK : InputEvent.BUTTON2_MASK;
    }
    /** Returns whether the platform registers a popup on mouse press. */
    public static boolean getPopupOnPress() {
        // Only w32 is popup on release 
        return !Platform.isWindows();
    }

    private static final PopupMenu[] NO_POPUPS = new PopupMenu[0];
    /** Return all AWT popup menus associated with the given component. */
    public static PopupMenu[] getPopupMenus(Component c) {
        // Here's a nice little hack to get access to the popup list on the
        // given invoker...
        try {
            Field field = Component.class.getDeclaredField("popups");
            boolean accessible = field.isAccessible();
            field.setAccessible(true);
            Vector popups = (Vector)field.get(c);
            field.setAccessible(accessible);
            if (popups != null)
                return (PopupMenu[])popups.toArray(new PopupMenu[popups.size()]);
            return NO_POPUPS;
        }
        catch(NoSuchFieldException e) {
            // not gonna happen
            throw new Error("No field named 'popups' in class Component");
        }
        catch(IllegalAccessException e) {
            // neither should this
            throw new Error("Can't access popup for component " + c);
        }
    }

    /** Returns all MenuItems matching the given label or path which are on
        PopupMenus on the given Component. */
    public static MenuItem[] findAWTPopupMenuItems(Component parent,
                                                   String path) {
        PopupMenu[] popups = getPopupMenus(parent);
        ArrayList list = new ArrayList();
        for (int i=0;i < popups.length;i++) {
            list.addAll(findMenuItems(popups[i], path, true));
        }
        return (MenuItem[])list.toArray(new MenuItem[list.size()]);
    }

    /** Returns all MenuItems matching the given label or path which are found
        in the given Frame's MenuBar. */
    public static MenuItem[] findAWTMenuItems(Frame frame, String path) {
        MenuBar mb = frame.getMenuBar();
        if (mb != null) {
            Collection items = findMenuItems(mb, path, true);
            return (MenuItem[])items.toArray(new MenuItem[items.size()]);
        }
        return new MenuItem[0];
    }

    /** Returns a unique path to the given MenuItem. */
    public static String getPath(MenuItem item) {
        String path = getPath(item, false);
        if (isOnPopup(item)
            && findAWTPopupMenuItems(getInvoker(item), path).length > 1) {
            path = getPath(item, true);
        }
        return path;
    }

    /** Returns a unique path to the given MenuItem.  If on a PopupMenu,
        optionally include the PopupMenu name. */
    private static String getPath(MenuItem item, boolean includePopupName) {
        MenuContainer invoker = getInvoker(item);
        MenuContainer top;
        if (invoker == null) {
            // Find the top-most Menu above this MenuItem
            top = item.getParent();
            while (top instanceof Menu
                   && !(((Menu)top).getParent() instanceof MenuBar)) {
                top = ((Menu)top).getParent();
            }
            if (top == null)
                throw new RuntimeException("MenuItem is not attached to the hierarchy");
        }
        else {
            // Find the containing PopupMenu
            top = item.getParent();
            while (top instanceof Menu
                   && !(((Menu)top).getParent() instanceof Component)) {
                top = ((Menu)top).getParent();
            }
        }

        // Return a path to the item, starting at the first top level Menu 
        String path = item.getLabel();
        MenuItem mi = item;
        while (mi.getParent() != top) {
            mi = (MenuItem)mi.getParent();
            path = mi.getLabel() + "|" + path;
        }
        if (top instanceof PopupMenu) {
            if (includePopupName) {
                // If the popup has the default name, use its index
                // on the invoker instead.  
                String name = ((PopupMenu)top).getName();
                if (Regexp.stringMatch("popup[0-9]+", name)) {
                    PopupMenu[] all = getPopupMenus((Component)invoker);
                    for (int i=0;i < all.length;i++) {
                        if (all[i] == top) {
                            // Make it different from the default name
                            name = "popup#" + i;
                            break;
                        }
                    }
                }
                path = name + "|" + path;
            }
        }        
        else {
            path = ((Menu)top).getLabel() + "|" + path;
        }
        Log.debug("Path for " + item + " is " + path);
        return path;
    }

    /** Returns all AWT menu items found with the given label; if matchPath is
        set then the MenuItem path is examined as well as the label.
    */
    private static Collection findMenuItems(MenuContainer mc,
                                            String path,
                                            boolean matchPath) {
        if (matchPath) 
            Log.debug("Searching for '" + path + "' on '" + mc);
        ArrayList list = new ArrayList();
        if (mc instanceof MenuBar) {
            for (int i=0;i < ((MenuBar)mc).getMenuCount();i++) {
                Menu menu = ((MenuBar)mc).getMenu(i);
                Log.debug("Scanning '" + menu + "'");
                list.addAll(findMenuItems(menu, path, matchPath));
            }
        }
        else if (mc instanceof Menu) {
            for (int i=0;i < ((Menu)mc).getItemCount();i++) {
                MenuItem mi = ((Menu)mc).getItem(i);
                if (mi instanceof MenuContainer) {
                    Log.debug("Scanning '" + mi + "'");
                    list.addAll(findMenuItems((MenuContainer)mi, path,
                                              matchPath));
                }
                else if (path.equals(mi.getLabel())) {
                    Log.debug("Found '" + mi + "'");
                    list.add(mi);
                }
                else if (matchPath) {
                    if (ExtendedComparator.stringsMatch(path, getPath(mi, false))
                        || ExtendedComparator.stringsMatch(path, getPath(mi, true))) {
                        Log.debug("Found (path) '" + mi + "'");
                        list.add(mi);
                    }
                    // TODO: fuzzy matching on the unique id (i.e. drop off or
                    // add the popup menu name.
                }
            }
        }
        return list;
    }

    /** Return the focus owner under the given Window. 
        As of 1.4.x, components will report that they do not have focus
        if asked from a different AppContext than their own.  Account
        for that here.
    */
    public static Component getFocusOwner() {
        try {
            Class cls = Class.forName("java.awt.KeyboardFocusManager");
            Field field = cls.getDeclaredField("focusOwner");
            boolean accessible = field.isAccessible();
            field.setAccessible(true);
            Component c = (Component)field.get(null);
            field.setAccessible(accessible);
            return c;
        }
        catch(Exception e) {
            if(!(e instanceof ClassNotFoundException))
                Log.log(e);
            // FIXME this lookup doesn't seem to work on 1.3!
            Iterator iter = new AWTHierarchy().getRoots().iterator();
            Component focus = null;
            while (iter.hasNext()) {
                Window w = (Window)iter.next();
                if (w.isShowing() && (focus = getFocusOwner(w)) != null)
                    break;
            }
            return focus;
        }
    }

    private static Component getFocusOwner(Window w) {
        Component focus = w.getFocusOwner();
        if (focus == null) {
            Window[] owned = w.getOwnedWindows();
            for (int i=0;i < owned.length;i++) {
                if ((focus = owned[i].getFocusOwner()) != null)
                    return focus;
            }
        }
        return focus;
    }

    /** For debugging purposes only. */
    public static AppContext getAppContext(Component c) {
        try {
            Field field = Component.class.getDeclaredField("appContext");
            boolean accessible = field.isAccessible();
            field.setAccessible(true);
            AppContext appContext = (AppContext)field.get(c);
            field.setAccessible(accessible);
            return appContext;
        }
        catch(Exception e) {
            Log.warn(e);
            return null;
        }
    }

    /** WARNING: This uses 1.3/1.4 implementation details. */
    public static boolean eventTypeEnabled(Component c, int id) {
        // certain AWT components should have events enabled, even if they
        // claim not to.
        // NOTE: Checkbox could be included here, obviating the need for
        // CheckboxTester's AWT-mode function.
        if (c instanceof Choice)
            return true;
        try {
            AWTEvent ev = new AWTEvent(c, id) { };
            Method m = Component.class.getDeclaredMethod("eventEnabled",
                                                         new Class[] {
                                                             AWTEvent.class
                                                         });
            m.setAccessible(true);
            Boolean b = (Boolean)m.invoke(c, new Object[] { ev });
            return b.booleanValue();
        }
        catch(Exception e) {
            Log.warn(e);
            return true;
        }
    }

    // Don't use the full classname, since anonymous inner classnames
    // are dependent on order of appearance in the source
    static final String ROOT_FRAME_CLASSNAME =
        SwingUtilities.class.getName() + "$";
    /** Is the given component the default Swing hidden frame? */
    public static boolean isSharedInvisibleFrame(Component c) {
        // Must perform an additional check, since applets may
        // have their own version in their AppContext
        return c instanceof Frame
            && (c == JOptionPane.getRootFrame()
                || c.getClass().getName().startsWith(ROOT_FRAME_CLASSNAME));
    }

    public static boolean isAppletViewerFrame(Component c) {
        return c.getClass().getName().equals("sun.applet.AppletViewer");
    }

    private static final Matcher POPUP_MATCHER = 
        new ClassMatcher(JPopupMenu.class, true);

    /** Returns the currently active popup menu, if any.  If no popup is
        currently showing, returns null.
    */
    public static JPopupMenu getActivePopupMenu() {
        try {
            return (JPopupMenu)BasicFinder.getDefault().find(POPUP_MATCHER);
        }
        catch(ComponentSearchException e) {
            return null;
        }
    }

    /** Find the currently active Swing popup menu, if any, waiting up to
        POPUP_TIMEOUT ms.  Returns null if no popup found.
    */
    public static JPopupMenu findActivePopupMenu() {
        JPopupMenu popup = getActivePopupMenu();
        if (popup == null
            && !SwingUtilities.isEventDispatchThread()) {
            long now = System.currentTimeMillis();
            while ((popup = getActivePopupMenu()) == null) {
                if (System.currentTimeMillis() - now > POPUP_TIMEOUT) {
                    break;
                }
                try { Thread.sleep(100); } 
                catch(Exception e) { }
            }
        }
        return popup;
    }

    /** Safe version of {@link Component#getLocationOnScreen}, which 
     * avoids lockup if an AWT popup menu is showing.  The AWT popup
     * holds the AWT tree lock when showing, which lock is required by
     * {@link Component#getLocationOnScreen}.
    */
    public static Point getLocationOnScreen(Component c) {
        if (isAWTTreeLockHeld()) {
            if (!c.isShowing())
                throw new IllegalComponentStateException("component must be showing on the screen to determine its location");
            Point loc = new Point(c.getLocation());
            if (!(c instanceof Window)) {
                Container parent = c.getParent();
                if (parent == null)
                    throw new IllegalComponentStateException("component must be showing on the screen to determine its location");
                Point ploc = getLocationOnScreen(parent);
                loc.translate(ploc.x, ploc.y);
            }
            return loc;
        }
        return new Point(c.getLocationOnScreen());
    }

    /** Return whether the given component is part of a transient dialog.
     * This includes dialogs generated by JFileChooser, JOptionPane,
     * JColorChooser, and ProgressMonitor.<p>
     * Note that it is possible to use JOptionPane.createDialog to create a
     * reusable dialog, so just because it's transient doesn't mean it will be
     * disposed of when it is hidden.<p>
     * Note that this won't detect transient Dialogs after their components
     * have been reassigned to a new transient Dialog.
     */
    public static boolean isTransientDialog(Component c) {
        if (c instanceof Window) {
            if (c instanceof JDialog) {
                Container contentPane = ((JDialog)c).getContentPane();
                Component[] kids = contentPane.getComponents();
                if (kids.length == 1) {
                    return kids[0] instanceof JOptionPane
                        || kids[0] instanceof JFileChooser
                        || kids[0] instanceof JColorChooser;
                }
            }
        }
        else if (!(c instanceof JOptionPane // also covers ProgressMonitor
                   || c instanceof JFileChooser
                   || c instanceof JColorChooser)) {
            Container parent = c.getParent();
            return parent != null && isTransientDialog(parent);
        }
        return false;
    }

    /** Returns the Applet descendent of the given Container, if any. */
    public static Applet findAppletDescendent(Container c) {
        try {
            return (Applet)BasicFinder.getDefault().
                find(c, new ClassMatcher(Applet.class));
        }
        catch(ComponentSearchException e) {
            return null;
        }
    }

    /** Return whether this is the tertiary button, considering primary to be
     * button1 and secondary to be the popup trigger button.
     */
    public static boolean isTertiaryButton(int mods) {
        return ((mods & AWTConstants.BUTTON_MASK) != InputEvent.BUTTON1_MASK)
            && ((mods & AWTConstants.POPUP_MASK) == 0);
    }

    /** Convert the string representation into the actual modifier mask. */
    public static int getModifiers(String mods) {
        int value = 0;
        if (mods != null && !mods.equals("")) {
            StringTokenizer st = new StringTokenizer(mods, "| ");
            while (st.hasMoreTokens()) {
                String flag = st.nextToken();
                // Allow short-form modifiers
                if (!flag.endsWith("_MASK"))
                    flag = flag + "_MASK";
                if (AWTConstants.POPUP_MODIFIER.equals(flag))
                    value |= AWTConstants.POPUP_MASK;
                else if (AWTConstants.TERTIARY_MODIFIER.equals(flag))
                    value |= AWTConstants.TERTIARY_MASK;
                else if (!flag.equals("0"))
                    value |= Reflector.getFieldValue(InputEvent.class, flag);
            }
        }
        return value;
    }

    private static String getModifiers(int flags, boolean isMouse) {
        // Historical note: 
        // On a mac, ALT+BUTTON1 means BUTTON2; META+BUTTON1 means BUTTON3
        int macModifiers = 
            InputEvent.CTRL_MASK|InputEvent.ALT_MASK|InputEvent.META_MASK;
        boolean isMacButton = isMouse
            && Platform.isMacintosh()
            && (flags & macModifiers) != 0;
        String mods = "";
        String or = "";
        if ((flags & InputEvent.ALT_GRAPH_MASK) != 0) {
            mods += or + "ALT_GRAPH_MASK"; or = "|";
            flags &= ~InputEvent.ALT_GRAPH_MASK;
        }
        if ((flags & InputEvent.BUTTON1_MASK) != 0 && !isMacButton) {
            mods += or + "BUTTON1_MASK"; or = "|";
            flags &= ~InputEvent.BUTTON1_MASK;
        }
        // Mask for ALT is the same as MB2
        if ((flags & InputEvent.ALT_MASK) != 0 && !isMacButton && !isMouse) {
            mods += or + "ALT_MASK"; or = "|";
            flags &= ~InputEvent.ALT_MASK;
        }
        // Mac uses ctrl modifier to get MB2
        if ((flags & InputEvent.CTRL_MASK) != 0 && !isMacButton) {
            mods += or + "CTRL_MASK"; or = "|";
            flags &= ~InputEvent.CTRL_MASK;
        }
        // Mask for META is the same as MB3
        if ((flags & InputEvent.META_MASK) != 0 && !isMacButton && !isMouse) {
            mods += or + "META_MASK"; or = "|";
            flags &= ~InputEvent.META_MASK;
        }
        if ((flags & AWTConstants.POPUP_MASK) != 0) {
            mods += or + "POPUP_MASK"; or = "|";
            flags &= ~AWTConstants.POPUP_MASK;
        }
        if ((flags & AWTConstants.TERTIARY_MASK) != 0) {
            mods += or + "TERTIARY_MASK"; or = "|";
            flags &= ~AWTConstants.TERTIARY_MASK;
        }
        if ((flags & InputEvent.SHIFT_MASK) != 0) {
            mods += or + "SHIFT_MASK"; or = "|";
            flags &= ~InputEvent.SHIFT_MASK;
        }
        // Empty strings are confusing and invisible; make it explicit
        if ("".equals(mods))
            mods = "0";
        return mods;
    }

    public static String getKeyModifiers(int flags) {
        return getModifiers(flags, false);
    }

    public static String getMouseModifiers(int flags) {
        return getModifiers(flags, true);
    }

    /** Convert the integer modifier flags into a string representation. */
    public static String getModifiers(InputEvent event) {
        return getModifiers(event.getModifiers(), 
                            event instanceof MouseEvent);
    }

    public static String getKeyCode(int keycode) {
        return Reflector.getFieldName(KeyEvent.class, keycode, "VK_");
    }

    public static int getKeyCode(String code) {
        return Reflector.getFieldValue(KeyEvent.class, code);
    }

    public static boolean isModifier(int keycode) {
        switch(keycode) {
        case KeyEvent.VK_META:
        case KeyEvent.VK_ALT:
        case KeyEvent.VK_ALT_GRAPH:
        case KeyEvent.VK_CONTROL:
        case KeyEvent.VK_SHIFT:
            return true;
        default:
            return false;
        }
    }

    public static int keyCodeToMask(int code) {
        switch(code) {
        case KeyEvent.VK_META: return InputEvent.META_MASK;
        case KeyEvent.VK_ALT: return InputEvent.ALT_MASK;
        case KeyEvent.VK_ALT_GRAPH: return InputEvent.ALT_GRAPH_MASK;
        case KeyEvent.VK_CONTROL: return InputEvent.CTRL_MASK;
        case KeyEvent.VK_SHIFT: return InputEvent.SHIFT_MASK;
        default:
            throw new IllegalArgumentException("Keycode is not a modifier: "
                                               + code);
        }
    }

    /** Convert the given modifier event mask to the equivalent key code. */
    public static int maskToKeyCode(int mask) {
        switch(mask) {
        case InputEvent.META_MASK: return KeyEvent.VK_META;
        case InputEvent.ALT_MASK: return KeyEvent.VK_ALT;
        case InputEvent.ALT_GRAPH_MASK: return KeyEvent.VK_ALT_GRAPH;
        case InputEvent.CTRL_MASK: return KeyEvent.VK_CONTROL;
        case InputEvent.SHIFT_MASK: return KeyEvent.VK_SHIFT;
        default:
            throw new IllegalArgumentException("Unrecognized mask '"
                                               + mask + "'");
        }
    }

    /** If a component does not have mouse events enabled, use the
        first ancestor which does.
    */
    public static Component retargetMouseEvent(Component comp, int id, Point pt) {
        Point where = pt;
        while (!(comp instanceof Window)
               && !eventTypeEnabled(comp, id)) {
            Log.debug("Retargeting event, " + Robot.toString(comp) 
                      + " not interested");
            where = SwingUtilities.convertPoint(comp, where.x, where.y,
                                                comp.getParent());
            comp = comp.getParent();
        }
        pt.setLocation(where);
        return comp;
    }

    public static Insets getInsets(Container c) {
        try {
            Insets insets = c.getInsets();
            if (insets != null)
                return insets;
        }
        catch(NullPointerException e) {
            // FileDialog.getInsets() throws (1.4.2_07)
        }
        return new Insets(0, 0, 0, 0);
    }

    // Try to lock the AWT tree lock; returns immediately if it can
    private static class ThreadStateChecker extends Thread {
        public boolean started;
        private Object lock;
        public ThreadStateChecker(Object lock) {
            super("thread state checker");
            setDaemon(true);
            this.lock = lock;
        }
        public synchronized void start() {
            super.start();
            try {
                wait(30000);
            }
            catch(InterruptedException e) {
                throw new InterruptedAbbotException("Interrupted starting AWT object");
            }
        }
        public void run() {
            synchronized(this) {
                started = true;
                notifyAll();
            }
            synchronized(lock) {
                // dummy operation
                setName(super.getName());
            }
        }
    }
}
