package abbot.tester;

import abbot.InterruptedAbbotException;
import abbot.Log;
import abbot.Platform;
import abbot.WaitTimedOutException;

import abbot.finder.BasicFinder;
import abbot.finder.ComponentFinder;
import abbot.finder.ComponentNotFoundException;
import abbot.finder.Matcher;
import abbot.finder.MultipleComponentsFoundException;
import abbot.finder.matchers.JMenuItemMatcher;
import abbot.finder.matchers.JMenuMatcher;

import abbot.i18n.Strings;

import abbot.util.AWT;
import abbot.util.Bugs;
import abbot.util.Condition;
import abbot.util.NamedThreadFactory;
import abbot.util.Properties;
import abbot.util.Reflector;

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Label;
import java.awt.MenuBar;
import java.awt.MenuComponent;
import java.awt.MenuItem;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.InvocationEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.PaintEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

import java.lang.reflect.Field;

import java.lang.reflect.InvocationTargetException;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleIcon;

import javax.swing.AbstractButton;
import javax.swing.FocusManager;
import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;

import javax.xml.ws.Holder;

import sun.awt.SunToolkit;

/** Provide a higher level of abstraction for user input (A Better Robot).
    The Robot's operation may be affected by the following properties:<br>
    <pre><code>abbot.robot.auto_delay</code></pre><br>
    Set this to a value representing the millisecond count in between
    generated events.  Usually just set to 100-200 if you want to slow down
    the playback to simulate actual user input.  The default is zero delay.<br>
    <pre><code>abbot.robot.mode</code></pre><br>
    Set this to either "robot" or "awt" to designate the desired mode of event
    generation.  "robot" uses java.awt.Robot to generate events, while "awt"
    stuffs events directly into the AWT event queue.<br>
    <pre><code>abbot.robot.event_post_delay</code></pre><br>
    This is the maximum number of ms it takes the system to post an AWT event
    in response to a Robot-generated event.
    <pre><code>abbot.robot.default_delay</code></pre><br>
    Base delay setting, acts as default value for the next two.
    <pre><code>abbot.robot.popup_delay</code></pre><br>
    Set this to the maximum time to wait for a menu to appear or be generated.
    <pre><code>abbot.robot.component_delay</code></pre><br>
    Set this to the maximum time to wait for a Component to become available.
    <p>
    The Robot class provides a generic solution for dealing with asynchronous
    updates to the UI with the {@link #wait(Condition,long, int)} method.
    This allows the testing thread to pause until a given component of data value
    in a component is ready.
    <p>
    NOTE: Only use event queue synchronization (e.g.
    {@link #invokeAndWait(Runnable)} or {@link #waitForIdle()} when a
    subsequent robot-level action is being applied to the results of a prior
    action (e.g. focus, deiconify, menu selection).  Otherwise, don't
    introduce a mandatory delay (e.g. use {@link #invokeLater(Runnable)}).
    <p>
    NOTE: If a robot action isn't reproduced properly, you may need to
    introduce either additional events or extra delay.  Adding enforced delay
    for a given platform is usually preferable to generating additional events,
    so always try that first, but be sure to restrict it to the platform in
    question.
    <p>
    NOTE: Robot actions should <b>never</b> be invoked on the event dispatch
    thread.
 */

public class Robot implements AWTConstants {
    
    
    /** Use java.awt.Robot to generate events. */
    public static int EM_ROBOT = 0;
    /** Post events to the AWT event queue. */
    public static int EM_AWT   = 1;

    private static final String LABELED_BY_PROPERTY = "labeledBy";
    private static final Toolkit toolkit = Toolkit.getDefaultToolkit();
    // Max robot delay, in ms
    private static final int MAX_DELAY = 60000;
    // TODO: verify this value for X11, etc.; ALT for w32, option for OSX
    public static final int MOUSELESS_MODIFIER_MASK = InputEvent.ALT_MASK;
    public static final String MOUSELESS_MODIFIER =
        AWT.getKeyModifiers(MOUSELESS_MODIFIER_MASK);

    /** OS X using screenMenuBar actually uses an AWT menu as the live
        component.  The JXXX components exist, but are not effectively
        active. 
    */
    protected static final boolean useScreenMenuBar() {
        // Ideally we'd install a menu and check where it ended up, since the
        // property is read once at startup and ignored thereafter.
        return Platform.isOSX()
            && (Boolean.getBoolean("com.apple.macos.useScreenMenuBar")
                || Boolean.getBoolean("apple.laf.useScreenMenuBar"));
    }

    /** Base delay setting. */
    public static int defaultDelay =
        Properties.getProperty("abbot.robot.default_delay", 30000, 0, 60000);

    /** Delay before checking for idle.  This allows the system a little time
        to put a native event onto the AWT event queue. */
    private static int eventPostDelay =
        Properties.getProperty("abbot.robot.event_post_delay", 100, 0, 1000);

    protected static long IDLE_TIMEOUT =
        Integer.getInteger("abbot.robot.idle_timeout", 10000).intValue();

    protected static int REALSYNC_IDLE_TIMEOUT_DEFAULT = 22000;
// This code was generating timeout that were far too long for practical use
// so I am going to try with a hardcoded default of 22 seconds
//    static {
//        try {
//            String fieldName;
//            if (Platform.is7OrAfter()) {
//                fieldName = "DEFAULT_WAIT_TIME";
//            }
//            else {
//                fieldName = "WAIT_TIME";
//            }
//            
//            Field $WAITTIME = SunToolkit.class.getDeclaredField(fieldName);
//            $WAITTIME.setAccessible(true);
//            int timeout = $WAITTIME.getInt(SunToolkit.class);
//
//            Field $MAX_ITERS = SunToolkit.class.getDeclaredField("MAX_ITERS");
//            $MAX_ITERS.setAccessible(true);
//            int max_inters = $MAX_ITERS.getInt(SunToolkit.class);
//            // Assume time out should be just a couple of more interations than
//            // the timeout we might see if everything has gone wrong
//            REALSYNC_IDLE_TIMEOUT_DEFAULT = 
//                timeout * (max_inters + 2);
//        } catch (Exception e) {
//            Log.warn("Problem defaulting idle time", e);
//        }
//    }

    protected static long REALSYNC_IDLE_TIMEOUT =
        Integer.getInteger("abbot.robot.realsync_idle_timeout", REALSYNC_IDLE_TIMEOUT_DEFAULT).intValue();

    /** Delay before failing to find a popup menu that should appear. */
    protected static int popupDelay =
        Properties.getProperty("abbot.robot.popup_delay",
                               defaultDelay, 0, 60000);
    
    
    // Some platforms require a pause between mouse down and mouse motion
    protected int DRAG_DELAY = 
        Properties.getProperty("abbot.robot.drag_delay",
                               Platform.isX11() || Platform.isOSX()
                               ? 100 : 0, 0,
                               60000);
    

   /** Delay before jittering the mouse to encourage a popup menu to appear. */
   protected static int popupJitterDelay =
       Properties.getProperty("abbot.robot.popup_jitter_delay",
                              300, 0, 10000);

    /** Delay before failing to find a component that should be visible. */
    public static int componentDelay =
        Properties.getProperty("abbot.robot.component_delay",
                               defaultDelay, 0, 60000);

   /** Number of times to retry a popup menu before we give up. */
   public static final int popupRetry =
       Properties.getProperty("abbot.robot.popup_retry",
                              1, 0,10);
    
    /** With decreased robot auto delay, OSX popup menus don't activate
     * properly.  Indicate the minimum delay for proper operation (determined
     * experimentally). 
     */
    private static final int subMenuDelay = Platform.isOSX() ? 100 : 0; 

    /** How events are generated. */
    private static int eventMode = EM_ROBOT;
    private static boolean verified = false;
    private static boolean serviceMode = false;
    
    // FIXME add one per graphics device?
    /** The robot used to generate events. */
    private static java.awt.Robot robot;
    private static WindowTracker tracker;
    /** Current input state.  This will either be that of the AWT event queue
     * or of the robot, depending on the dispatch mode. 
     * Note that the robot state may be different from that seen by the AWT
     * event queue, since robot events may be as yet unprocessed.
     */
    private static InputState state;


    /** Suitable inter-event delay for most cases; tests have been run safely
     * at this value.  Should definitely be less than the double-click
     * threshold.<p>  
    */
    private static final int DEFAULT_DELAY = getPreferredRobotAutoDelay();
    private static final int SLEEP_INTERVAL =         
        Properties.getProperty("abbot.robot.wait_sleep_interval", 50, 10, 1000);

    private static int autoDelay = DEFAULT_DELAY;
    public static int getAutoDelay() { return autoDelay; }



    /** Returns a functioning instance of java.awt.Robot. If this method
     * returns null, it should be assumed that java.awt.Robot is unavailable
     * or non-functional on the current system.  
     */
    public static java.awt.Robot getRobot() {
        initializeRobot();
        return serviceMode ? null : robot;
    }

    /** Return a singleton InputState object. */
    public static InputState getState() {
        initializeRobot();
        return state;
    }

    private static synchronized void initializeRobot() {
        if (state == null) {
            robot = createRobot();
            tracker = WindowTracker.getTracker();
            state = new InputState();
        }
    }
    
    
    private static class InterruptibleRobot extends java.awt.Robot
   {
      public InterruptibleRobot()
         throws AWTException
      {
         super();
      }
      
      
      
      /**
       * Sleep, but reset interrupt flag if interrupted
       */
      public synchronized void delay(int ms) {
          try {
              TimeUnit.MILLISECONDS.sleep(ms);
          } catch(InterruptedException ite) {
             throw new InterruptedAbbotException("Interrupted when running a delay");
          }
      }
      
      /**
       * Wait, but reset interrupt flag if interrupted
       */
      public synchronized void waitForIdle() {
          if (EventQueue.isDispatchThread()) {
             throw new IllegalThreadStateException("Only on the event dispatch thread");
          }
          // Post marker event to queue
          try {
              SunToolkit.flushPendingEvents();
              EventQueue.invokeAndWait( new Runnable() {
                                              public void run() {
                                                  // dummy implementation
                                              }
                                          } );
          } catch(InterruptedException ite) {
             throw new InterruptedAbbotException("Interrupted when waiting for idle");
          } catch(InvocationTargetException ine) {
              ine.printStackTrace();
          }
      }

   }
    

    private static java.awt.Robot createRobot() {
      java.awt.Robot robot = null;
        String mode = System.getProperty("abbot.robot.mode", "robot");
        autoDelay = Properties.getProperty("abbot.robot.auto_delay",
                                           autoDelay, -1, 60000);
        try {
            // Even if the robot doesn't work, we can still use it for some
            // things. 
            robot = new InterruptibleRobot();
            if (autoDelay != -1) {
                robot.setAutoDelay(autoDelay);
            }
            else {
                autoDelay = robot.getAutoDelay();
            }
            if (!verified) {
                verified = true;
                boolean verify = !"false".equals(System.getProperty("abbot.robot.verify"));
                if (verify && !RobotVerifier.verify(robot)) {
                    // robot doesn't work (w32 service mode)
                    serviceMode = true;
                    System.err.println("Robot non-functional, "
                                       + "falling back to AWT mode");
                    mode = "awt";
                }
            }
        }
        catch(AWTException e) {
            // no robot available, send AWT events
            System.err.println("Falling back to AWT mode: "
                               + e.getMessage());
            mode = "awt";
        }
        if (mode.equals("awt")) {
            eventMode = EM_AWT;
        }
        return robot;
    }

    /** Returns the current event-generation mode. */
    public static int getEventMode() {
        initializeRobot();
        return eventMode; 
    }
    public static String getEventModeDescription() {
        initializeRobot();
        String desc = eventMode == EM_ROBOT ? "robot" : "awt";
        if (serviceMode)
            desc += " (service)";
        return desc;
    }

    /** Set the event-generation mode.
        @throws IllegalStateException if the requested mode is EM_ROBOT and
        java.awt.Robot is unavailable in the current environment.
     */
    public static void setEventMode(int mode) {
        initializeRobot();
        if (eventMode != mode) {
            if (mode == EM_ROBOT && (serviceMode || robot == null)) {
                String msg = Strings.get("tester.Robot.no_robot_mode");
                throw new IllegalStateException(msg);
            }
            eventMode = mode;
        }
    }

    public static int getEventPostDelay() { return eventPostDelay; }
    public static void setEventPostDelay(int delay) {
        eventPostDelay = Math.min(1000, Math.max(0, delay));
    }

    /** Allow this to be adjusted, mostly for testing. */
    public static void setAutoDelay(int ms) {
        initializeRobot();
        ms = Math.min(60000, Math.max(0, ms));
        if (eventMode == EM_ROBOT)
            robot.setAutoDelay(ms);
        autoDelay = ms;
        Log.debug("Auto delay set to " + ms);
    }

    /** Default constructor. */
    public Robot() {
        initializeRobot();
    }
    
    /** 
     * Move the mouse to the given location, in screen coordinates.  
     * NOTE: in robot mode, you may need to invoke this with a little jitter.
     * There are some conditions where a single mouse move will not
     * generate the necessary enter event on a component (typically a
     * dialog with an OK button) before a mousePress.  See also click().
     * NOTE: does 1.4+ need jitter?
     */
    private void mouseMove(int x, int y) {
        if (eventMode == EM_ROBOT) {
            Log.debug("ROBOT: Mouse move: (" + x + "," + y + ")");
            robot.mouseMove(x, y);
        }
        else {
            // Can't stuff an AWT event for an arbitrary location
        }
    }

    /** Send a button press event. */
    public void mousePress(int buttons) {
        if (eventMode == EM_ROBOT) {
            Log.debug("ROBOT: Mouse press: " + AWT.getMouseModifiers(buttons));
            // OSX 1.4.1 accidentally swaps mb2 and mb3; fix it here
            robot.mousePress(buttons);
        }
        else {
            Component c = state.getMouseComponent();
            if (c == null) {
                Log.warn("No current mouse component for button press", 4);
                return;
            }
            Point where = state.getMouseLocation();
            postMousePress(c, where.x, where.y, buttons);
        }
    }

    /** Send a button release event for button 1. */
    public void mouseRelease() {
        mouseRelease(MouseEvent.BUTTON1_MASK);
    }

    /** Send a button release event. */
    public void mouseRelease(int buttons) {
        if (eventMode == EM_ROBOT) {
            Log.debug("ROBOT: Mouse release: " + AWT.getMouseModifiers(buttons));
            robot.mouseRelease(buttons);
        }
        else {
            Component source = state.isDragging()
                ? state.getDragSource()
                : (lastMousePress != null 
                   ? lastMousePress.getComponent()
                   : state.getMouseComponent());
            Point where = state.getMouseLocation();
            if (source == null) {
                Log.warn("Mouse release outside of available frames");
                return;
            }
            else if (where == null) {
                if (lastMousePress == null) {
                    Log.warn("No record of most recent mouse press");
                    return;
                }
                where = lastMousePress.getPoint();
            }
            postMouseRelease(source, where.x, where.y, buttons);
        }
    }

    /** Move keyboard focus to the given component.  Note that the component
        may not yet have focus when this method returns.
    */
    public void focus(Component comp) {
        focus(comp, false);
    }

    /** Use an explicit listener, since hasFocus is not always reliable. */
    private class FocusWatcher extends FocusAdapter {
        public volatile boolean focused = false;
        public FocusWatcher(Component c) {
            focused = AWT.getFocusOwner() == c;
        }
        public void focusGained(FocusEvent f) {
            focused = true;
        }
        public void focusLost(FocusEvent f) {
            if (focused) {
               Log.warn("FFF gained focus and lost again");
            }
            focused = false;
        }
    }

    /** Move keyboard focus to the given component. */
    public void focus(final Component comp, boolean wait) {
       
        if (comp instanceof Window)
        {
            Window focusedWindow = AWT.getFocusedWindow();
            if (comp == focusedWindow) {
                return;
            }
        }

        Component currentOwner = AWT.getFocusOwner();
        if (currentOwner == comp) {
            return;
        }

        // Check to see if the currentOwner is in fact visibiel
        if (currentOwner != null && !AWT.getWindow(currentOwner).isVisible()) {
            Log.warn("FFF Current focued component is not visible\n" + currentOwner.toString());
            Log.warn("FFF Going to attempt a hard reset of the focus owner");

            invokeAndWait(comp, new Runnable() {
                public void run() {
                    KeyboardFocusManager kfm = FocusManager.getCurrentKeyboardFocusManager();
                    kfm.clearGlobalFocusOwner();
                }
            });

            waitForIdle();

            // Do we still have a problem?

            Component ownerAfterRest = AWT.getFocusOwner();
            if (ownerAfterRest != null && ownerAfterRest == currentOwner) {
                Log.warn("FFF Focus owner hasn't changed, going to force window show and hide");
                final Window parent = AWT.getWindow(ownerAfterRest);
                Log.warn("FFF window " + parent);

                invokeAndWait(comp, new Runnable() {
                    public void run() {
                        parent.setVisible(true);
                    }
                });

                waitForIdle();

                invokeAndWait(comp, new Runnable() {
                    public void run() {
                        KeyboardFocusManager kfm = FocusManager.getCurrentKeyboardFocusManager();
                        kfm.clearGlobalFocusOwner();
                    }
                });

                waitForIdle();

                invokeAndWait(comp, new Runnable() {
                    public void run() {
                        parent.setVisible(false);
                        parent.dispose();
                    }
                });

                waitForIdle();


                if (AWT.getFocusOwner() == currentOwner) {
                    Log.warn("FFF Nothing I do seems to make a diffenc, still stuck with previous state");
                }

            }
        }


        Log.debug("Focus change");
        
        

        final FocusWatcher fw = new FocusWatcher(comp);

        try {
            
            // Register a focus watcher on this component
            invokeAndWait(comp, new Runnable() {

                @Override
                public void run() {
                    comp.addFocusListener(fw);
                }
            });
            // for pointer focus
            mouseMove(comp, comp.getWidth()/2, comp.getHeight()/2); 
            waitForIdle();
            // Make sure the correct window is in front
            Window w1 = currentOwner != null 
                ? AWT.getWindow(currentOwner) : null;
            Window w2 = AWT.getWindow(comp);
            if (w1 != w2) {
                activate(w2);
                waitForIdle();
            }
            // NOTE: while it would be nice to have a robot method instead of
            // requesting focus, clicking to change focus may have 
            // side effects 
            invokeAndWait(comp, new Runnable() {
                public void run() {
                    boolean possible = comp.requestFocusInWindow();
                    if (!possible) 
                    {
                       comp.requestFocus();
                    }
                }
            });
            
            //
            
            waitForIdle();
            
            //

            if (wait) {
                
                try {
                    wait(new ConditionEDTDecorator(comp, new Condition(){
    
                        @Override
                        public boolean test() {
                            return fw.focused;
                        }
                    }),componentDelay);
                }
                catch (WaitTimedOutException wte) {
                    String msg =
                        Strings.get("tester.Robot.focus_failed",
                                    new Object[] { toString(comp) });
                    throw new ActionFailedException(msg, wte);
                }
            }
        }
        finally {
            invokeAndWait(comp, new Runnable() {

                @Override
                public void run() {
                    comp.removeFocusListener(fw);
                }
            });
        }
        
        // Wait for idle so we can ignore any side effects
        //
        
        waitForIdle();
    }

    /** Usually only needed when dealing with Applets. */
    protected static EventQueue getEventQueue(Component c) {
        initializeRobot();
        if (c != null) {
            return tracker.getQueue(c);
        }
        else {
           return toolkit.getSystemEventQueue();
        }
    }

    /** Post a runnable on the given component's event queue.  Useful when
        driving multiple Applets, but is also useful to ensure an operation
        happens on the event dispatch thread.
    */
    public void invokeLater(Component context, Runnable action) {
        EventQueue queue = getEventQueue(context);
        queue.postEvent(new InvocationEvent(toolkit, action));
    }

    /** Post a runnable on the given component's event queue and wait for it
        to finish.  */
    public void invokeAndWait(Component c, final Runnable action) {
       
        // Original code just perfomed a invoke later then a waitForIdle
        // which doesn't work in some cases where the scrolling takes some
        // time as the event queue is empty whilst processing the messages
        //invokeLater(c, action);
        // See issue 3396894
       
       
        // Instead we are going to use our callable code which makes
        // sure the event is run
        callAndWait(c, new Callable<Object>() {
            @Override
            public Object call() {
               action.run();
               return null;
            }
         });
       
         // Make sure any post actions are complete
         waitForIdle();
    }

    /** @deprecated Method renamed to {@link #invokeLater(Runnable)}
     * @param action
     */
    public void invokeAction(Runnable action) {
        invokeLater(action);
    }

    /** @deprecated Method renamed to {@link #invokeLater(Component, Runnable)}
     * @param c
     * @param action
     */
    public void invokeAction(Component c, Runnable action) {
        invokeLater(c, action);
    }
   
    /** Run the given action on the event dispatch thread.  This should be
     * used for any non-read-only methods invoked directly on a GUI
     * component. 
     * NOTE: if you want to use the results of the action, use invokeAndWait
     * instead. 
     */
    public void invokeLater(Runnable action) {
        invokeLater(null, action);
    }

    /** Run the given action on the event dispatch thread, but don't return
        until it's been run.
    */
    public void invokeAndWait(Runnable action) {
        invokeAndWait(null, action);
    }

    public void keyPress(int keycode) {
        keyPress(keycode, KeyEvent.CHAR_UNDEFINED);
    }

    /** Send a key press event. */
    private void keyPress(int keycode, char keyChar) {
        if (eventMode == EM_ROBOT) {
            Log.debug("ROBOT: key press " + AWT.getKeyCode(keycode));
            try {
                robot.keyPress(keycode);
            }
            catch(IllegalArgumentException e) {
                throw new IllegalArgumentException("invalid key code "
                                                   + keycode
                                                   + " (char "
                                                   + keyChar + ")");
            }
        }
        else {
            int mods = state.getModifiers();
            if (AWT.isModifier(keycode))
                mods |= AWT.keyCodeToMask(keycode);
            postKeyEvent(KeyEvent.KEY_PRESSED, mods,
                         keycode, KeyEvent.CHAR_UNDEFINED);
            // Auto-generate KEY_TYPED events, as best we can
            int mask = state.getModifiers();
            if (keyChar == KeyEvent.CHAR_UNDEFINED) {
                KeyStroke ks = KeyStroke.getKeyStroke(keycode, mask);
                keyChar = KeyStrokeMap.getChar(ks);
            }
            if (keyChar != KeyEvent.CHAR_UNDEFINED) {
                postKeyEvent(KeyEvent.KEY_TYPED, mask,
                             KeyEvent.VK_UNDEFINED, keyChar);
            }
        }
    }

    /** Send a key release event. */
    public void keyRelease(int keycode) {
        if (eventMode == EM_ROBOT) {
            Log.debug("ROBOT: key release " + AWT.getKeyCode(keycode));
            robot.keyRelease(keycode);
            if (Bugs.hasKeyInputDelay()) {
                // OSX, empirical
                int KEY_INPUT_DELAY = 200;
                if (KEY_INPUT_DELAY > autoDelay) {
                    delay(KEY_INPUT_DELAY - autoDelay);
                }
            }
        }
        else {
            int mods = state.getModifiers();
            if (AWT.isModifier(keycode))
                mods &= ~AWT.keyCodeToMask(keycode);
            postKeyEvent(KeyEvent.KEY_RELEASED, mods,
                         keycode, KeyEvent.CHAR_UNDEFINED);
        }
    }

    private void postKeyEvent(int id, int modifiers, int keycode, char ch) {
        Component c = findFocusOwner();
        if (c != null) {
            postEvent(c, new KeyEvent(c, id,
                                      System.currentTimeMillis(),
                                      modifiers, keycode, ch));
        }
        else {
            Log.warn("No component has focus, key press discarded");
        }
    }

    /** Sleep for a little bit, measured in UI time. */
    public void sleep() {
        delay(SLEEP_INTERVAL);
    }

    /** Sleep the given duration of ms. */
    public static void delay(int ms) {
        
        initializeRobot();
        
        //
        
        if (eventMode == EM_ROBOT) {
            while (ms > MAX_DELAY) {
                robot.delay(MAX_DELAY);
                ms -= MAX_DELAY;
            }
            robot.delay(ms);
        }
        else {
            try { Thread.sleep(ms); } catch(InterruptedException ie) {
               throw new InterruptedAbbotException("Interrupted during delay");
            }
        }
    }

    private static final Runnable EMPTY_RUNNABLE =
        new Runnable() { public void run() { } };

    /** Check for a blocked event queue (symptomatic of an active w32 AWT
     * popup menu). 
     * @return whether the event queue is blocked.  
     */
    protected boolean queueBlocked() {
        return postInvocationEvent(toolkit.getSystemEventQueue(),
                                   toolkit, 200);
    }

    /** @return whether we timed out waiting for the invocation to run */
    protected boolean postInvocationEvent(EventQueue eq, Toolkit toolkit,
                                          long timeout) {
        class RobotIdleLock { }
        Object lock = new RobotIdleLock();
        synchronized(lock) {
            eq.postEvent(new InvocationEvent(toolkit, EMPTY_RUNNABLE,
                                             lock, true));
            long start = System.currentTimeMillis();
            try {
                // NOTE: on fast linux systems when showing a dialog, if we
                // don't provide a timeout, we're never notified, and the
                // test will wait forever (up through 1.5.0_05).
                lock.wait(timeout);
                return (System.currentTimeMillis() - start) >= IDLE_TIMEOUT;
            }
            catch(InterruptedException e) {
                Log.warn("Invocation lock interrupted");
                throw new InterruptedAbbotException("Interrupted during invocation lock");
            }
        }
    }

    private static volatile boolean HAS_SUN_TOOLKIT = !Boolean.getBoolean("abbot.DisableSunToolkitWait");
    private static ExecutorService REAL_SYNC_POOL = new ThreadPoolExecutor(0, 10,
                                      60L, TimeUnit.SECONDS,
                                      new SynchronousQueue<Runnable>(),
                                      new NamedThreadFactory("SunToolkit.realSync"));
                                                                

    private  boolean waitForIdle(EventQueue eq) {
        if (EventQueue.isDispatchThread())
            throw new IllegalThreadStateException("Cannot call method from the event dispatcher thread");

        // Synchronize with the platform event queue
        //
        
        if (HAS_SUN_TOOLKIT) {
            try {
                
                Future f = REAL_SYNC_POOL.submit(new Runnable() {
                    public void run() {
                        ((SunToolkit)toolkit).realSync();
                    }
                });
                
                // We are going to wait for minute or two, and if it hasn't returned then
                // give it a poke
                
                try {
                    f.get(REALSYNC_IDLE_TIMEOUT, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new InterruptedAbbotException("Interrupted while waiting for idle");
                } catch (ExecutionException e) {
                    //
                    Robot.<RuntimeException>throwAny(e.getCause());
                } catch (TimeoutException e) {
                    
                    Log.warn("Timeout waiting for SunToolkit.realSync method to return", e);
                    
                    // Interrupt the future so we have some chance of recovering
                    // look at the SunToolkit code this should interrupt this lock
                    // in waitForIdle if things goes wrong
                    //
                    // try {
                    //   while (!eventDispatched) {
                    //     waitLock.wait();
                    //   }
                    // } catch (InterruptedException ie) {
                    //   return false;
                    // }
                    
                    f.cancel(true);

                    
                    // We think that the realSync method is stuck because
                    // a timeout happened posting the events to the event queue
                    // due to bug xxxxx, so we are going to use reflection to
                    // try to get hold of the lock object and notify on it
                    
                    try {
                        
                        Field $waitLock = SunToolkit.class.getDeclaredField("waitLock");
                        $waitLock.setAccessible(true);
                        Object waitLock = $waitLock.get(toolkit);
                        
                        synchronized (waitLock) {
                            waitLock.notifyAll();
                        }
                        
                        
                    } catch (Throwable th) {
                        Log.warn("Problem trying to poke the SunToolkit.realSync method back into life", th);
                    }
                }
            
            }
            catch (sun.awt.SunToolkit.OperationTimedOut oto) {
                Log.warn("Timeout trying to sync with event queue, going to sleep for a little bit");
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                   throw new InterruptedAbbotException("Interrupted while waiting for idle");
                }
            }
            catch (sun.awt.SunToolkit.InfiniteLoop oto) {
                Log.warn("Inifinte loop trying to sync with event queue, going to sleep for a little bit");
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                   throw new InterruptedAbbotException("Interrupted while waiting for idle");
                }
            }
            catch (NoClassDefFoundError nodef) {
                Log.warn("Not using the sun toolkit, test likely to be less reliable", nodef);
                HAS_SUN_TOOLKIT = false;
            }
        }

        // NOTE: as of Java 1.3.1, robot.waitForIdle only waits for the
        // last event on the queue at the time of this invocation to be
        // processed.  We need better than that.  Make sure the given event
        // queue is empty when this method returns

        // We always post at least one idle event to allow any current event
        // dispatch processing to finish.
        long start = System.currentTimeMillis();
        int count = 0;
        do {

            if (postInvocationEvent(eq, toolkit, IDLE_TIMEOUT)) {
                Log.warn("Timed out waiting for idle"
                         + " (posted invocation event): "
                         + IDLE_TIMEOUT + "ms (after " + count + " events)"
                         + " posted to " + eq,
                         Log.FULL_STACK);
                return false;
            }
            if (System.currentTimeMillis() - start > IDLE_TIMEOUT) {
                Log.warn("Timed out waiting for idle event queue after "
                         + count + " events");
                return false;
            }
            ++count;
            // Force a yield
            sleep();

            // NOTE: this does not detect invocation events (i.e. what
            // gets posted with EventQueue.invokeLater), so if someone
            // is repeatedly posting one, we might get stuck.  Not too
            // worried, since if a Runnable keeps calling invokeLater
            // on itself, *nothing* else gets much chance to run, so it
            // seems to be a bad programming practice.
        } while (eq.peekEvent() != null);
        
        return true;
    }
    
    
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwAny(Throwable e) throws E {
        throw (E)e;
    }
 
    /**
     * Wait for an idle AWT event queue.  Note that this is different from
     * the implementation of <code>java.awt.Robot.waitForIdle()</code>, which
     * may have events on the queue when it returns.  Do <b>NOT</b> use this
     * method if there are animations or other continual refreshes happening,
     * since in that case it may never return.<p>
     * 
     * @return Whether the waitForIdle timed out or not
     */
    public  boolean waitForIdle() {
        if (eventPostDelay > autoDelay) {
            delay(eventPostDelay - autoDelay);
        }
        Collection queues = tracker.getEventQueues();
        if (queues.size() == 1) {
            return waitForIdle(toolkit.getSystemEventQueue());
        }
        else {
            // FIXME this resurrects dead event queues
            Iterator iter = queues.iterator();
            while (iter.hasNext()) {
                
                EventQueue eq = (EventQueue)iter.next();
                boolean hasNextQueue = false;
                try {
                    Field $nextQueue = EventQueue.class.getDeclaredField("nextQueue");
                    $nextQueue.setAccessible(true);
                    Object value = $nextQueue.get(eq);
                    hasNextQueue = value!=null;
                } catch (NoSuchFieldException e) {
                    Log.warn("Problem accessing field EventQueue.nextQueue");
                } catch (IllegalAccessException e) {
                    Log.warn("Problem accessing field EventQueue.nextQueue", 2);
                }

                // If eq has a nextQueue then ignore it as it isn't currently processing 
                // requests because another queue has been pushed on top of it, 
                // For bug fix 3476008
                //
                if (!hasNextQueue && !waitForIdle(eq)) {
                    return false;
                }
            }
        }
        
        return true;
    }

    /** Sample the color at the given point on the screen. */
    public Color sample(int x, int y) {
        // Service mode always returns black when sampled
        if (robot != null && !serviceMode) {
            // Make sure we sync the graphics
            Toolkit.getDefaultToolkit().sync();
            //
            return robot.getPixelColor(x, y);
        }
        String msg = Strings.get("tester.Robot.no_sample");
        throw new UnsupportedOperationException(msg);
    }

    /** Sample the color at the given point on the component. */
    public Color sample(Component c, int x, int y) {
        return sample(c, new ComponentLocation(new Point(x, y)));
    }

    /** Sample the color at the given location on the component. */
    public Color sample(Component c, ComponentLocation loc) {
        Point p = loc.getPoint(c);
        Point where = AWT.getLocationOnScreen(c);
        where.translate(p.x, p.y);
        return sample(where.x, where.y);
    }

    /** Capture the contents of the given rectangle. */
    /* NOTE: Text components (and maybe others with a custom cursor) will
     * capture the cursor.  May want to move the cursor out of the component
     * bounds, although this might cause issues where the component is
     * responding visually to mouse movement. 
     * Is this an OSX bug?
     */
    public BufferedImage capture(final Rectangle bounds) {
        Log.debug("Screen capture " + bounds);
        BufferedImage image = null;
        if (robot != null) {
            image = robot.createScreenCapture(bounds);
        }
        return image;
    }

    /** Capture the contents of the given component, sans any border or
     * insets.  This should only be used on components that do not use a LAF
     * UI, or the results will not be consistent across platforms.
     */ 
    public BufferedImage capture(Component comp) {
        return capture(comp, true);
    }

    /** Capture the contents of the given component, optionally including the
     * border and/or insets.  This should only be used on components that do
     * not use a LAF UI, or the results will not be consistent across
     * platforms. 
     */ 
    public BufferedImage capture(Component comp, boolean ignoreBorder) {
        Rectangle bounds = new Rectangle(comp.getSize());
        Point loc = AWT.getLocationOnScreen(comp);
        bounds.setLocation(loc.x, loc.y);
        Log.debug("Component bounds " + bounds);
        if (ignoreBorder) {
            Insets insets = ((Container)comp).getInsets();
            if (insets != null) {
                bounds.x += insets.left;
                bounds.y += insets.top;
                bounds.width -= insets.left + insets.right;
                bounds.height -= insets.top + insets.bottom;
                Log.debug("Component insets " + insets);
            }
        }
        return capture(bounds);
    }

    // Bug workaround support
    protected void jitter(Component comp, int x, int y) {
        Log.debug("jitter");
        mouseMove(comp, (x > 0 ? x - 1 : x + 1), y);
    }

    // Bug workaround support
    protected void jitter(int x, int y) {
        mouseMove((x > 0 ? x - 1 : x + 1), y);
    }

    /** Move the pointer to the center of the given component. */
    public void mouseMove(Component comp) {
        mouseMove(comp, comp.getWidth() / 2, comp.getHeight() / 2);
    }

   /** Wait the given number of ms for the component to be showing and
       ready.  Returns false if the operation times out. */
    private boolean waitForComponent(Component c, long delay) {
      return waitForComponent(c, delay, SLEEP_INTERVAL);
   }
   
    /** Wait the given number of ms for the component to be showing and
        ready.  Returns false if the operation times out. */
    private boolean waitForComponent(Component c, long delay, int sleep) {
        if (!isReadyForInput(c)) {
            Log.debug("Waiting for component to show: " + toString(c));
            long start = System.currentTimeMillis();
            
            // Give the component some time before we go into the loop,
            // might take a little time
            //delay(sleep);

            // Start loop waiting for component to show
            while (!isReadyForInput(c)) {
                if (c instanceof JPopupMenu) {
                    // wiggle the mouse over the parent menu item to
                    // ensure the submenu shows
                    Component invoker = ((JPopupMenu)c).getInvoker();
                    if (invoker instanceof JMenu) {
                        jitter(invoker, invoker.getWidth()/2, 
                               invoker.getHeight()/2);
                    }
                }
                if (System.currentTimeMillis() - start > delay) {
                    Log.warn("Component " + toString(c)
                             + " (" + Integer.toHexString(c.hashCode()) + ")"
                             + " not ready after " + delay + "ms: "
                             + "showing=" + c.isShowing()
                             + " win ready="
                             + tracker.isWindowReady(AWT.getWindow(c)));
                    return false;
                }
                delay(sleep);
            }
        }
        return true;
    }

    /** Move the pointer to the given coordinates relative to the given
     * component.
     */ 
    public void mouseMove(Component comp, int x, int y) {
        if (!waitForComponent(comp, componentDelay)) {
            String msg = "Can't obtain position of component "
                + toString(comp);
            throw new ComponentNotShowingException(msg);
        }
        if (eventMode == EM_ROBOT) {
            try {
                Point point = AWT.getLocationOnScreen(comp);
                if (point != null) {
                    point.translate(x, y);
                    mouseMove(point.x, point.y);
                }
            }
            catch(java.awt.IllegalComponentStateException e) {
            }
        }
        else {
            Component eventSource = comp;
            int id = MouseEvent.MOUSE_MOVED;
            boolean outside = false;

            // When dragging, the event source is always the target of the
            // original mouse press.
            if (state.isDragging()) {
                id = MouseEvent.MOUSE_DRAGGED;
                eventSource = state.getDragSource();
            }
            else {
                Point pt = new Point(x, y);
                eventSource = comp = AWT.retargetMouseEvent(comp, id, pt);
                x = pt.x; y = pt.y;
                outside = x < 0 || y < 0
                    || x >= comp.getWidth() || y >= comp.getHeight();
            }

            Component current = state.getMouseComponent();
            if (current != comp) {
                if (outside && current != null) {
                    Point pt = 
                        SwingUtilities.convertPoint(comp, x, y, current);
                    postMouseMotion(current, MouseEvent.MOUSE_EXITED, pt);
                    return;
                }
                postMouseMotion(comp, MouseEvent.MOUSE_ENTERED,
                                new Point(x, y));
            }
            Point pt = new Point(x, y);
            if (id == MouseEvent.MOUSE_DRAGGED) {
                // Drag coordinates are relative to drag source component
                pt = SwingUtilities.convertPoint(comp, pt, eventSource);
            }
            postMouseMotion(eventSource, id, pt);
            // Add an exit event if warranted
            if (outside) {
                postMouseMotion(comp, MouseEvent.MOUSE_EXITED,
                                new Point(x, y));
            }
        }
    }

    /** Move the mouse appropriately to get from the source to the
        destination.  Enter/exit events will be generated where appropriate.
    */
    public void dragOver(Component dst, int x, int y) {
        mouseMove(dst, x-4, y);
        mouseMove(dst, x, y);
    }

    /** Begin a drag operation using button 1.<p>
        This method is tuned for native drag/drop operations, so if you get
        odd behavior, you might try using a simple
        {@link #mousePress(Component,int,int)} instead. 
    */
    public void drag(Component src, int sx, int sy) {
        drag(src, sx, sy, InputEvent.BUTTON1_MASK);
    }


    /** Begin a drag operation using the given button mask.<p>
        This method is tuned for native drag/drop operations, so if you get
        odd behavior, you might try using a simple
        {@link #mousePress(Component,int,int,int)} instead. 
    */
    // TODO: maybe auto-switch to robot mode if available?
    public void drag(Component src, int sx, int sy, int buttons) {
        if (Bugs.dragDropRequiresNativeEvents()
            && eventMode != EM_ROBOT
            && !Boolean.getBoolean("abbot.ignore_drag_error")) {
            String msg = Strings.get("abbot.Robot.no_drag_available");
            if (serviceMode) {
                // If we start a native drag in this mode, it'll pretty much
                // lock up the system, apparently with the native AWT libs
                // starting a thread invisible to the VM that chews up all CPU
                // time. 
                throw new ActionFailedException(msg);
            }
            Log.warn(msg);
        }

        Log.debug("drag");

        mousePress(src, sx, sy, buttons);
        if (DRAG_DELAY > autoDelay) {
            delay(DRAG_DELAY);
        }
        if (Platform.isWindows() || Platform.isMacintosh()) {
            int dx = sx + AWTConstants.DRAG_THRESHOLD < src.getWidth()
                ? AWTConstants.DRAG_THRESHOLD : 0;
            int dy = sy + AWTConstants.DRAG_THRESHOLD < src.getHeight()
                ? AWTConstants.DRAG_THRESHOLD : 0;
            if (dx == 0 && dy == 0)
                dx = AWTConstants.DRAG_THRESHOLD;
            mouseMove(src, sx + dx/4, sy + dy/4);
            mouseMove(src, sx + dx/2, sy + dy/2);
            mouseMove(src, sx + dx, sy + dy);
            mouseMove(src, sx + dx + 1, sy + dy);
        }
        else {
            mouseMove(src, sx + AWTConstants.DRAG_THRESHOLD/2, sy + AWTConstants.DRAG_THRESHOLD/2);
            mouseMove(src, sx + AWTConstants.DRAG_THRESHOLD, sy + AWTConstants.DRAG_THRESHOLD);
            mouseMove(src, sx + AWTConstants.DRAG_THRESHOLD/2, sy + AWTConstants.DRAG_THRESHOLD/2);
            mouseMove(src, sx, sy);
        }
        Log.debug("drag started");
    }

    /** End a drag operation, releasing the mouse button over the given target
        location.<p>
        This method is tuned for native drag/drop operations, so if you get
        odd behavior, you might try using a simple
        {@link #mouseMove(Component,int,int)}, {@link #mouseRelease()}
        instead.  
    */
    public void drop(Component target, int x, int y) {
        Log.debug("drop");
        // Delay between final move and drop to ensure drop ends.
        int DROP_DELAY = 
            Properties.getProperty("abbot.robot.drop_delay",
                                   Platform.isWindows() ? 200 : 0, 0, 60000);

        dragOver(target, x, y);
        long start = System.currentTimeMillis();
        while (!state.isDragging()) {
            if (System.currentTimeMillis() - start > eventPostDelay*4) {
                String msg = Strings.get("Robot.no_current_drag");
                throw new ActionFailedException(msg);
            }
            sleep();
        }
        if (DROP_DELAY > autoDelay)
            delay(DROP_DELAY - autoDelay);

        mouseRelease(state.getButtons());
        Log.debug("dropped");
    }

    /** Generate a mouse enter/exit/move/drag for the destination component.
     * NOTE: The VM automatically usually generates exit events; need a test
     * to define the behavior, though.
     */
    private void postMouseMotion(Component dst, int id, Point to) {
        // The VM auto-generates exit events as needed (1.3, 1.4)
        if (id != MouseEvent.MOUSE_DRAGGED)
            dst = AWT.retargetMouseEvent(dst, id, to);
        // Avoid multiple moves to the same location
        if (state.getMouseComponent() != dst
            || !to.equals(state.getMouseLocation())) {
            postEvent(dst, new MouseEvent(dst, id, System.currentTimeMillis(),
                                          state.getModifiers(), to.x, to.y,
                                          state.getClickCount(), false));
        }
    }

    /** Type the given keycode with no modifiers. */
    public void key(int keycode) {
        key(keycode, 0);
    }

    /** Press or release the appropriate modifiers corresponding to the given
        mask.
    */
    public void setModifiers(int modifiers, boolean press) {
        boolean altGraph = (modifiers & InputEvent.ALT_GRAPH_MASK) != 0;
        boolean shift = (modifiers & InputEvent.SHIFT_MASK) != 0;
        boolean alt = (modifiers & InputEvent.ALT_MASK) != 0;
        boolean ctrl = (modifiers & InputEvent.CTRL_MASK) != 0;
        boolean meta = (modifiers & InputEvent.META_MASK) != 0;
        if (press) {
            if (altGraph) keyPress(KeyEvent.VK_ALT_GRAPH);
            if (alt) keyPress(KeyEvent.VK_ALT);
            if (shift) keyPress(KeyEvent.VK_SHIFT);
            if (ctrl) keyPress(KeyEvent.VK_CONTROL);
            if (meta) keyPress(KeyEvent.VK_META);
        }
        else {
            // For consistency, release in the reverse order of press
            if (meta) keyRelease(KeyEvent.VK_META);
            if (ctrl) keyRelease(KeyEvent.VK_CONTROL);
            if (shift) keyRelease(KeyEvent.VK_SHIFT);
            if (alt) keyRelease(KeyEvent.VK_ALT);
            if (altGraph) keyRelease(KeyEvent.VK_ALT_GRAPH);
        }
    }

    /** Type the given keycode with the given modifiers.  Modifiers is a mask
     * from the available InputEvent masks.
     */
    public void key(int keycode, int modifiers) {
        key(KeyEvent.CHAR_UNDEFINED, keycode, modifiers);
    }

    private void key(char ch, int keycode, int modifiers) {
        Log.debug("key keycode=" + AWT.getKeyCode(keycode)
                  + " mod=" + AWT.getKeyModifiers(modifiers));
        boolean isModifier = true;
        switch(keycode) {
        case KeyEvent.VK_ALT_GRAPH:
            modifiers |= InputEvent.ALT_GRAPH_MASK; break;
        case KeyEvent.VK_ALT:
            modifiers |= InputEvent.ALT_MASK; break;
        case KeyEvent.VK_SHIFT:
            modifiers |= InputEvent.SHIFT_MASK; break;
        case KeyEvent.VK_CONTROL:
            modifiers |= InputEvent.CTRL_MASK; break;
        case KeyEvent.VK_META:
            modifiers |= InputEvent.META_MASK;break;
        default: isModifier = false; break;
        }
        setModifiers(modifiers, true);
        if (!isModifier) {
            keyPress(keycode, ch);
            keyRelease(keycode);
        }
        setModifiers(modifiers, false);
        if (Bugs.hasKeyStrokeGenerationBug())
            delay(100);
    }

    /**
     * Type the given character.  Note that this sends the key to whatever
     * component currently has the focus.
     */
    // FIXME should this be renamed to "key"?
    public void keyStroke(char ch) {
        KeyStroke ks = KeyStrokeMap.getKeyStroke(ch);
        if (ks == null) {
            // If no mapping is available, we omit press/release events and
            // only generate a KEY_TYPED event
            Log.debug("No key mapping for '" + ch + "'");
            Component focus = findFocusOwner();
            if (focus == null) {
                Log.warn("No component has focus, keystroke discarded",
                         Log.FULL_STACK);
                return;
            }
            KeyEvent ke = new KeyEvent(focus, KeyEvent.KEY_TYPED,
                                       System.currentTimeMillis(),
                                       0, KeyEvent.VK_UNDEFINED, ch);
            // Allow any pending robot events to complete; otherwise we
            // might stuff the typed event before previous robot-generated
            // events are posted.
            if (eventMode == EM_ROBOT)
                waitForIdle();
            postEvent(focus, ke);
        }
        else {
            int keycode = ks.getKeyCode();
            int mod = ks.getModifiers();
            Log.debug("Char '" + ch + "' generated by keycode=" 
                      + keycode + " mod=" + mod);
            key(ch, keycode, mod);
        }
    }

    /** Type the given string. */
    public void keyString(String str) {
        char[] ch = str.toCharArray();
        for (int i=0;i < ch.length;i++) {
            keyStroke(ch[i]);
        }
    }

    public void mousePress(Component comp) {
        mousePress(comp, InputEvent.BUTTON1_MASK);
    }

    public void mousePress(Component comp, int mask) {
        mousePress(comp, comp.getWidth() / 2, comp.getHeight() / 2, mask);
    }

    public void mousePress(Component comp, int x, int y) {
        mousePress(comp, x, y, InputEvent.BUTTON1_MASK);
    }

    /** Mouse down in the given part of the component.  All other mousePress
        methods must eventually invoke this one.
    */
    public void mousePress(Component comp, int x, int y, int mask) {
        if (eventMode == EM_ROBOT && Bugs.hasRobotMotionBug()) {
            jitter(comp, x, y);
        }
        mouseMove(comp, x, y);
        if (eventMode == EM_ROBOT)
            mousePress(mask);
        else {
            postMousePress(comp, x, y, mask);
        }
    }

    /** Post a mouse press event to the AWT event queue for the given
        component.
    */
    private void postMousePress(Component comp, int x, int y, int mask) {
        long when = lastMousePress != null 
            ? lastMousePress.getWhen() : 0;
        long now = System.currentTimeMillis();
        int count = 1;
        Point where = new Point(x, y);
        comp = AWT.retargetMouseEvent(comp, MouseEvent.MOUSE_PRESSED, where);
        if (countingClicks
            && comp == lastMousePress.getComponent()) {
            long delta = now - when;
            if (delta < AWTConstants.MULTI_CLICK_INTERVAL) {
                count = state.getClickCount() + 1;
            }
        }
        postEvent(comp, new MouseEvent(comp, MouseEvent.MOUSE_PRESSED, now,
                                       state.getKeyModifiers() | mask,
                                       where.x, where.y, count,
                                       AWTConstants.POPUP_ON_PRESS
                                       && (mask & AWTConstants.POPUP_MASK) != 0));
    }

    /** Post a mouse release event to the AWT event queue for the given
        component.
    */
    private void postMouseRelease(Component c, int x, int y, int mask) {
        long now = System.currentTimeMillis();
        int count = state.getClickCount();
        Point where = new Point(x, y);
        c = AWT.retargetMouseEvent(c, MouseEvent.MOUSE_PRESSED, where);
        postEvent(c, new MouseEvent(c, MouseEvent.MOUSE_RELEASED, now,
                                    state.getKeyModifiers() | mask,
                                    where.x, where.y, count,
                                    !AWTConstants.POPUP_ON_PRESS
                                    && (mask & AWTConstants.POPUP_MASK) != 0));
    }

    /** Click in the center of the given component. */
    final public void click(Component comp) {
        click(comp, comp.getWidth()/2, comp.getHeight()/2);
    }

    /** Click in the center of the given component, specifying which button. */
    final public void click(Component comp, int mask) {
        click(comp, comp.getWidth()/2, comp.getHeight()/2, mask);
    }

    /** Click in the component at the given location. */
    final public void click(Component comp, int x, int y) {
        click(comp, x, y, InputEvent.BUTTON1_MASK);
    }

    /** Click in the component at the given location with the given button. */
    final public void click(Component comp, int x, int y, int mask) {
        click(comp, x, y, mask, 1);
    }

    /** Click in the given part of the component.  All other click methods
     * must eventually invoke this one.  This method sometimes needs to be
     * redefined (i.e. JComponent to scroll before clicking).
     */
    public void click(Component comp, int x, int y, int mask, int count) {
        Log.debug("Click at (" + x + "," + y + ") on " + toString(comp)
                  + (count > 1 ? (" count=" + count) : ""));
        int keyModifiers = mask & ~AWTConstants.BUTTON_MASK;
        mask &= AWTConstants.BUTTON_MASK;
        setModifiers(keyModifiers, true);
        // Adjust the auto-delay to ensure we actually get a multiple click
        // In general clicks have to be less than 200ms apart, although the
        // actual setting is not readable by java that I'm aware of.
        int oldDelay = getAutoDelay();
        if (count > 1 && oldDelay * 2 > 200) {
            setAutoDelay(0);
        }
        long last = System.currentTimeMillis();
        mousePress(comp, x, y, mask);
        while (count-- > 1) {
            mouseRelease(mask);
            long delta = System.currentTimeMillis() - last;
            if (delta > AWTConstants.MULTI_CLICK_INTERVAL)
                Log.warn("Unexpected delay in multi-click: " + delta);
            last = System.currentTimeMillis();
            mousePress(mask);
        }
        setAutoDelay(oldDelay);
        mouseRelease(mask);
        setModifiers(keyModifiers, false);

    }

    /** @deprecated Renamed to {@link #selectAWTMenuItem(Frame,String)}. */
    public void selectAWTMenuItemByLabel(Frame frame, String path) {
        selectAWTMenuItem(frame, path);
    }

    /** Select the given menu item from the given Frame.  The given String may
        be either a label or path of labels, but must uniquely identify the
        menu item.  For example, "Copy" would be valid if there is only one
        instance of that menu label under the MenuBar, otherwise you would
        need to specify "Edit|Copy" to ensure the proper selection. 
        Note that this method doesn't require referencing the MenuComponent
        directly as a parameter. 
     */
    public void selectAWTMenuItem(Frame frame, String path) {
        MenuBar mb = frame.getMenuBar();
        if (mb == null) {
            String msg = Strings.get("tester.Robot.no_menu_bar",
                                     new Object[] { toString(frame) });
            throw new ActionFailedException(msg);
        }
        MenuItem[] items = AWT.findAWTMenuItems(frame, path);
        if (items.length == 0) {
            String msg = Strings.get("tester.Robot.no_menu_item",
                                     new Object[] { path, toString(frame) });
            throw new ActionFailedException(msg);
        }
        if (items.length > 1) {
            String msg = Strings.get("tester.Robot.multiple_menu_items");
            throw new ActionFailedException(msg);
        }
        selectAWTMenuItem(items[0]);
    }

    /** @deprecated Renamed to
        {@link #selectAWTPopupMenuItem(Component,String)}.
    */
    public void selectAWTPopupMenuItemByLabel(Component invoker, String path) {
        selectAWTPopupMenuItem(invoker, path);
    }

    /** Select the given menu item from a PopupMenu on the given Component.
        The given String may be either a label or path of labels, but must
        uniquely identify the menu item.  For example, "Copy" would be valid
        if there is only one instance of that menu label under the MenuBar,
        otherwise you would need to specify "Edit|Copy" to ensure the proper
        selection.  If there are more than one PopupMenu registerd on the
        invoking component, you will need to prefix the PopupMenu name as
        well, e.g. "popup0|Edit|Copy". */ 
    public void selectAWTPopupMenuItem(Component invoker, String path) {
        try {
            PopupMenu[] popups = AWT.getPopupMenus(invoker);
            if (popups.length == 0)
                throw new ActionFailedException(Strings.get("tester.Robot.awt_popup_missing"));
            
            MenuItem[] items = AWT.findAWTPopupMenuItems(invoker, path);
            if (items.length == 1) {
                selectAWTPopupMenuItem(items[0]);
                return;
            }
            else if (items.length == 0) {
                String msg = Strings.get("tester.Robot.no_popup_menu_item",
                                         new Object[] { path,
                                                        toString(invoker) });
                throw new ActionFailedException(msg);
            }
            String msg = Strings.get("tester.Robot.multiple_menu_items",
                                     new Object[] { path });
            throw new ActionFailedException(msg);
        }
        finally {
            AWT.dismissAWTPopup();
        }
    }

    protected void fireAccessibleAction(Component context,
                                        final AccessibleAction action,
                                        String name) {
        if (action != null && action.getAccessibleActionCount() > 0) {
            invokeLater(context, new Runnable() {
                public void run() {
                    action.doAccessibleAction(0);
                }
            });
        }
        else {
            String msg = Strings.get("tester.Robot.no_accessible_action",
                                     new String[] { name });
            throw new ActionFailedException(msg);
        }
    }

    private Component getContext(MenuComponent item) {
        while (!(item.getParent() instanceof Component)
               && item.getParent() instanceof MenuComponent)
            item = (MenuComponent)item.getParent();
        return (Component)item.getParent();
    }

    /** Select an AWT menu item.  */
    public void selectAWTMenuItem(MenuComponent item) {
        // Can't do this through coordinates because MenuComponents don't
        // store any of that information
        fireAccessibleAction(getContext(item), item.getAccessibleContext().
                             getAccessibleAction(), toString(item));
        if (queueBlocked())
            key(KeyEvent.VK_ESCAPE);
    }

    /** Select an AWT popup menu item. */
    public void selectAWTPopupMenuItem(MenuComponent item) {
        // Can't do this through coordinates because MenuComponents don't
        // store any of that information
        fireAccessibleAction(getContext(item), item.getAccessibleContext().
                             getAccessibleAction(), toString(item));
        if (queueBlocked())
            key(KeyEvent.VK_ESCAPE);
    }

    /** Is the given component ready for robot input? */
    protected boolean isReadyForInput(Component c) {
        if (eventMode == EM_AWT)
            return c.isShowing();
        Window w = AWT.getWindow(c);
        if (w == null) {
//            throw new ActionFailedException("Component '" + toString(c)
//                                            + "' has no Window ancestor");
           
           // Return false in more complicated cases where the component is
           // visible then not, modifying the code just to return false in this
           // case
           //
           
           return false;
        }
        return c.isShowing()
            && tracker.isWindowReady(w);
    }

    private boolean isOnJMenuBar(Component item) {
        if (item instanceof javax.swing.JMenuBar)
            return true;
        Component parent = item instanceof JPopupMenu
            ? ((JPopupMenu)item).getInvoker() : item.getParent();
        return parent != null && isOnJMenuBar(parent);
    }

    /** Find and select the given menu item, by path. */
    public void selectMenuItem(Component sameWindow, String path) {
      
        Container context=null;
        String nextPath = null;
      
        try {
            Window window = AWT.getWindow(sameWindow);
            
            java.util.List selectionPath = JMenuItemMatcher.splitMenuPath(path);
            
            // For each path entry select in turn to make sure we trigger
            // any lazy loading
            //
            
            int i = selectionPath.size();
            context = window;
            for (int j = 0; j < i; j++) {
                
                // If we are at the last item then we are looking
                // for a menu item, otherwise we are looking for
                // a menu instead
                //
                
                nextPath = (String)selectionPath.get(j);
                final ComponentFinder cf = BasicFinder.getDefault();
                final Matcher m = (j == i -1) ?
                    new JMenuItemMatcher(nextPath)
                    : new JMenuMatcher(nextPath);
                final Container innerContext = context; // For the inner class
                final Holder<Component> foundHolder = new Holder<Component>();
                final Holder<ComponentNotFoundException> cnfHolder = new Holder<ComponentNotFoundException>();
                final Holder<MultipleComponentsFoundException> mcfHolder = new Holder<MultipleComponentsFoundException>();
              
                try {
                    wait(new Condition(){
                      @Override
                      public boolean test() {
                            try {
                                final Component component = cf.find(innerContext, m);
                                foundHolder.value = component;
                                return component != null;
                            } catch (ComponentNotFoundException e) {
                                cnfHolder.value = e;
                                return false;
                            } catch (MultipleComponentsFoundException e) {
                                mcfHolder.value = e;
                                return false;
                            }
                        }
                    });  
                } catch (WaitTimedOutException wtoe) {
                    // Rethrow any exceptions
                    
                    if (mcfHolder.value!=null) {
                        throw mcfHolder.value;
                    }

                    if (cnfHolder.value!=null) {
                        throw cnfHolder.value;
                    }
                }
                       
                  
                // Don't care about the hierarchy on this one, since there'll only
                // ever be one popup active at a time.
                Component item = foundHolder.value;
                selectMenuItem(item);
                waitForIdle();
                context = (Container)item;
            }
        } 
        catch(ComponentNotFoundException e) { 
            throw new ComponentMissingException("Can't find menu item '"
                                                + path + "' by looking for '" + nextPath + "' in " + context,e);
        }
        catch(MultipleComponentsFoundException e) {
            throw new ActionFailedException(e.getMessage());
        }
    }

    /** Find and select the given menu item. */
    public void selectMenuItem(Component item) {
        Log.debug("Selecting menu item " + toString(item));
        Component parent = item.getParent();
        JPopupMenu parentPopup = null;
        if (parent instanceof JPopupMenu) {
            parentPopup = (JPopupMenu)parent;
            parent = ((JPopupMenu)parent).getInvoker();
        }
        boolean inMenuBar = parent instanceof javax.swing.JMenuBar;
        boolean isMenu = item instanceof javax.swing.JMenu;

        if (isOnJMenuBar(item) && useScreenMenuBar()) {
            // Use accessibility action instead
            fireAccessibleAction(item, item.getAccessibleContext().
                                 getAccessibleAction(), toString(item));
            return;
        }

        // If our parent is a menu, activate it first, if it's not already.
        if (parent instanceof javax.swing.JMenuItem) {
            if (parentPopup == null || !parentPopup.isShowing()) {
                Log.debug("Opening parent menu " + toString(parent));
                selectMenuItem(parent);
            }
        }

        // Make sure the appropriate window is in front
        if (inMenuBar) {
            final Window win = AWT.getWindow(parent);
            if (win != null) {
                // Make sure the window is in front, or its menus may be
                // obscured by another window.
                invokeAndWait(win, new Runnable() {
                    public void run() {
                        win.toFront();
                    }
                });
                
                // toFront might have side effects such as focus grabbing, make sure the event queue is idle
                // before we try to do anything else
                
                waitForIdle();
                
                //
                
                mouseMove(win);
            }
        }

        // Activate the item
        if (isMenu && !inMenuBar) {
            // Submenus only require a mouse-over to activate, but do
            // a click to be certain
            if (subMenuDelay > autoDelay) {
                delay(subMenuDelay - autoDelay);
            }
        }
        // Top-level menus and menu items *must* be clicked on
        Log.debug("Activating menu item " + toString(item));
        if (!item.isEnabled()) {
            throw new ActionFailedException("Menu item " + toString(item)
                                            + " is disabled");
        }
        
        // Try showing the menu item twice
        try {
            showMenuItemAndMakeVisible(item, isMenu);
        }
        catch (ComponentMissingException cme) {
            
            // Try again
            showMenuItemAndMakeVisible(item, isMenu);
        }
    }


    /**
     * I extracted the coda of the selectMenuItem method so we can retry one
     * if we fail, this is to try and work around an irritating intermittent
     * failure where the menu is not shown.
     * @param item
     * @param isMenu
     */
    private void showMenuItemAndMakeVisible(Component item, boolean isMenu) {
        // Try to bring up the menu item
        click(item);
        waitForIdle();

        // If this item is a menu, make sure its popup is showing before we
        // return 
        if (isMenu) {
            JPopupMenu popup = ((javax.swing.JMenu)item).getPopupMenu();
            if (!waitForComponent(popup, popupDelay, popupJitterDelay)) {
                String msg = "Clicking on '"
                    + ((javax.swing.JMenu)item).getText() 
                    + "' never produced a popup menu";
                throw new ComponentMissingException(msg);
            }
            // for OSX 1.4.1; isShowing set before popup is available
            if (subMenuDelay > autoDelay) {
                delay(subMenuDelay - autoDelay);
            }
        }
    }

    public void selectPopupMenuItem(Component invoker,
                                    ComponentLocation loc,
                                    String path) {
        Point where = loc.getPoint(invoker);

        if (where.x == -1)
            where.x = invoker.getWidth()/2;
        if (where.y == -1)
            where.y = invoker.getHeight()/2;
        Component popup = showPopupMenu(invoker, where.x, where.y);
        try {
            
            
            java.util.List selectionPath = JMenuItemMatcher.splitMenuPath(path);
            
            // For each path entry select in turn to make sure we trigger
            // any lazy loading
            //
            
            Container context = (Container)popup;
            int i = selectionPath.size();
            for (int j = 0; j < i; j++) {
                Matcher m = new JMenuItemMatcher(
                                (String)selectionPath.get(j));
                // Don't care about the hierarchy on this one, since there'll only
                // ever be one popup active at a time.
                Component item = BasicFinder.getDefault().
                    find(context, m);
                selectMenuItem(item);
                waitForIdle();
                context = (Container)item;
            }

        }
        catch(ComponentNotFoundException e) {
            throw new ComponentMissingException("Can't find menu item '"
                                                + path + "'");
        }
        catch(MultipleComponentsFoundException e) {
            throw new ActionFailedException(e.getMessage());
        }
    }


    
    

    /** Attempt to display a popup menu at center of the component. */
    public Component showPopupMenu(Component invoker) {
        return showPopupMenu(invoker,
                             invoker.getWidth()/2, invoker.getHeight()/2);
    }

    /** Attempt to display a popup menu at the given coordinates. */
    public Component showPopupMenu(Component invoker, int x, int y) {
        String where = " at (" + x + "," + y + ")";


        int retryCount = popupRetry;
        ComponentMissingException cme = null;
        
        sucess: while ((--retryCount) >= 0) {

            try
            {
                cme = null;
                Log.debug("Invoking popup " + where + " repeats left " + retryCount);
                showPopupMenuIntl(invoker, x, y, where);
                break sucess;
            }
            catch (ComponentMissingException cme2) 
            {
                if (cme2.getMessage().contains("POPUP_MASK")) {

                   Log.warn("Invoking popup failed, repeats left " + retryCount);
                   
                   //Take a breath
                   waitForIdle();
                   delay(2000);

                   // Hold and possibly repeat the action
                   cme = cme2;
                }
                else {
                   throw cme2;
                }
            }
        }
        
        // Throw the exception if it has failed at some point
        if (cme!=null) {
            throw cme;
        }

        
        // Wait for UI to be ready
        //

        Component popup = AWT.findActivePopupMenu();
        long start = System.currentTimeMillis();
        while (!isReadyForInput(SwingUtilities.getWindowAncestor(popup))
               && System.currentTimeMillis() - start > popupDelay) {
            sleep();
        }
        return popup;
    }

    /** For the moment refactor this out so we can retry if this fails **/
    private void showPopupMenuIntl(Component invoker, int x, int y, String where) {
        click(invoker, x, y, AWTConstants.POPUP_MASK);

        try
        {
            wait(new Condition()
                 {
                    public boolean test() {
                        return AWT.findActivePopupMenu()!=null;
                    }
                    
                    public String toString() {
                        return "Cannot find popup menu";
                    }
                    
                }, popupDelay, SLEEP_INTERVAL * 4);
        }
        catch (WaitTimedOutException wtoe) {
            String msg = "No popup responded to "
                + AWTConstants.POPUP_MODIFIER 
                + where + " on " 
                + toString(invoker);
            throw new ComponentMissingException(msg);
        }
    }

    /** Activate the given window. */
    public void activate(final Window win) {
       
        Window originalActiveWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().
                      getActiveWindow();
        if (win==originalActiveWindow) {
           return;
        }
        else if (originalActiveWindow!=null && originalActiveWindow.getParent() == win && AWT.isHeavyweightPopup(originalActiveWindow)) {
           return; // If we have a popup window on the current window
           
        }

        class ActivateWatcher extends WindowAdapter {
            volatile boolean active;

            public ActivateWatcher() {
                active = callAndWait(win, new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return win.isActive();
                    }
                }); 
            }

            @Override
            public void windowActivated(WindowEvent e) {
               active = true;
            }
        }
        final ActivateWatcher aw = new ActivateWatcher();


        try {
            // Add listener to check we have an active window
            invokeAndWait(win, new Runnable() {
                public void run() {
                    // FIXME figure out why two are sometimes needed
                    win.addWindowListener(aw);
                }
            });

            // ACTIVATE means window gets keyboard focus.
            invokeAndWait(win, new Runnable() {
                // FIXME figure out why two are sometimes needed
                public void run() {
                    win.toFront();
                    win.toFront();
                }
            });


            // Check this window becomes active
            try {
                wait(new ConditionEDTDecorator(win, new Condition() {

                    @Override
                    public boolean test() {
                        return aw.active;
                    }
                }), componentDelay);
            } catch (WaitTimedOutException wte) {
               
                Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().
                               getActiveWindow();
               
                String msg = Strings.get("tester.Robot.activate_failed", new Object[] { toString(win) });
                throw new ActionFailedException(msg, wte);
            }

            waitForIdle();


            // For pointer-focus systems
            mouseMove(win);

        } finally {
            // remove listener to check we have an active window
            invokeAndWait(win, new Runnable() {
                public void run() {

                    win.removeWindowListener(aw);
                }
            });

        }

    }

    protected Point getCloseLocation(Container c) {
        Dimension size = c.getSize();
        Insets insets = c.getInsets();
        if (Platform.isOSX()) {
            return new Point(insets.left + 15, insets.top / 2);
        }
        return new Point(size.width - insets.right - 10, insets.top / 2);
    }

    /** Invoke the window close operation. */
    public void close(Window w) {
        if (w.isShowing()) {
            // Move to a corner and "pretend" to use the window manager
            // control 
            try {
                Point p = getCloseLocation(w);
                mouseMove(w, p.x, p.y);
            }
            catch(Exception e) {
                // ignore
            }
            WindowEvent ev = new WindowEvent(w, WindowEvent.WINDOW_CLOSING);
            // If the window contains an applet, send the event on the
            // applet's queue instead to ensure a shutdown from the
            // applet's context (assists AppletViewer cleanup).
            Component applet = AWT.findAppletDescendent(w);
            EventQueue eq = tracker.getQueue(applet != null ? applet : w);
            eq.postEvent(ev);
        }
    }

    /** Return where the mouse usually grabs to move a window.  Center of the
     * top of the frame is usually a good choice.
     */
    protected Point getMoveLocation(Container c) {
        Dimension size = c.getSize();
        Insets insets = c.getInsets();
        return new Point(size.width/2, insets.top/2);
    }

    /** Move the given Frame/Dialog to the requested location. */
    public void move(Container comp, int newx, int newy) {
        Point loc = AWT.getLocationOnScreen(comp);
        moveBy(comp, newx - loc.x, newy - loc.y);
    }

    /** Move the given Window by the given amount. */
    public void moveBy(final Container comp, final int dx, final int dy) {
        final Point loc = AWT.getLocationOnScreen(comp);
        boolean userMovable = userMovable(comp);
        if (userMovable) {
            Point p = getMoveLocation(comp);
            mouseMove(comp, p.x, p.y);
            mouseMove(comp, p.x + dx, p.y + dy);
        }
        invokeAndWait(comp, new Runnable() {
            public void run() {
                comp.setLocation(new Point(loc.x + dx, loc.y + dy));
            }
        });
        if (userMovable) {
            Point p = getMoveLocation(comp);
            mouseMove(comp, p.x, p.y);
        }
    }

    /** Return where the mouse usually grabs to resize a window.  The lower
     * right corner of the window is usually a good choice.
     */ 
    protected Point getResizeLocation(Container c) {
        Dimension size = c.getSize();
        Insets insets = c.getInsets();
        return new Point(size.width-insets.right/2,
                         size.height-insets.bottom/2);
    }

    /** Return whether it is possible for the user to move the given
        component.
    */
    protected boolean userMovable(Component comp) {
        return comp instanceof Dialog
            || comp instanceof Frame
            || canMoveWindows();
    }

    /** Return whether it is possible for the user to resize the given
        component.
    */
    protected boolean userResizable(Component comp) {
        if (comp instanceof Dialog)
            return ((Dialog)comp).isResizable();
        if (comp instanceof Frame)
            return ((Frame)comp).isResizable();
        // most X11 window managers allow arbitrary resizing
        return canResizeWindows();
    }

    /** Resize the given Frame/Dialog to the given size.  */
    public void resize(Container comp, int width, int height) {
        Dimension size = comp.getSize();
        resizeBy(comp, width - size.width, height - size.height);
    }

    /** Resize the given Frame/Dialog by the given amounts.  */
    public void resizeBy(final Container comp, final int dx, final int dy) {
        // Fake the pointer motion like we're resizing
        boolean userResizable = userResizable(comp);
        if (userResizable) {
            Point p = getResizeLocation(comp);
            mouseMove(comp, p.x, p.y);
            mouseMove(comp, p.x + dx, p.y + dy);
        }
        invokeAndWait(comp, new Runnable() {
            public void run() {
                comp.setSize(comp.getWidth()+dx, comp.getHeight()+dy);
            }
        });
        if (userResizable) {
            Point p = getResizeLocation(comp);
            mouseMove(comp, p.x, p.y);
        }
    }

    /** Identify the coordinates of the iconify button where we can, returning
     * (0, 0) if we can't.
     */
    protected Point getIconifyLocation(Container c) {
        Dimension size = c.getSize();
        Insets insets = c.getInsets();
        // We know the exact layout of the window manager frames for w32 and
        // OSX.  Currently no way of detecting the WM under X11.  Maybe we
        // could send a WM message (WM_ICONIFY)?
        Point loc = new Point();
        loc.y = insets.top / 2;
        if (Platform.isOSX()) {
            loc.x = 35;
        }
        else if (Platform.isWindows()) {
            int offset = Platform.isWindowsXP() ? 64 : 45;
            loc.x = size.width - insets.right - offset;
        }
        return loc;
    }

    private static final int MAXIMIZE_BUTTON_OFFSET =
        Platform.isOSX() ? 25 
        : Platform.isWindows() ? -20 : 0;

    /** Identify the coordinates of the maximize button where possible,
        returning null if not.
    */
    protected Point getMaximizeLocation(Container c) {
        Point loc = getIconifyLocation(c);
        loc.x += MAXIMIZE_BUTTON_OFFSET;
        return loc;
    }

    /** Iconify the given Frame.  Don't support iconification of Dialogs at
     * this point (although maybe should).
     */
    public void iconify(final Frame frame) {
        Point loc = getIconifyLocation(frame);
        if (loc != null) {
            mouseMove(frame, loc.x, loc.y);
        }
        invokeAndWait(frame, new Runnable() {
            public void run() {
                frame.setState(Frame.ICONIFIED);
            }
        });

        // Wait for the event queue to settle down after this
        //
        
        waitForIdle();
    }

    public void deiconify(Frame frame) {
        normalize(frame);
    }

    public void normalize(final Frame frame) {
        invokeAndWait(frame, new Runnable() {
            public void run() {
                frame.setState(Frame.NORMAL);
                if (Bugs.hasFrameDeiconifyBug())
                    frame.setVisible(true);
            }
        });
        
        // Wait for the event queue to settle down after this
        //
        
        waitForIdle();
    }

    /** Make the window full size.  On 1.3.1, this is not reversible. */
    public void maximize(final Frame frame) {
        Point loc = getMaximizeLocation(frame);
        if (loc != null) {
            mouseMove(frame, loc.x, loc.y);
        }
        invokeAndWait(frame, new Runnable() {
            public void run() {
                // If the maximize is unavailable, set to full screen size
                // instead. 
                try {
                    if (toolkit.isFrameStateSupported(Frame.MAXIMIZED_BOTH) && !serviceMode) {
                        frame.setExtendedState(Frame.MAXIMIZED_BOTH);
                    }
                    else {
                        throw new ActionFailedException("Platform won't maximize");
                    }
                }
                catch(Exception e) {
                    Log.debug("Maximize not supported: " + e);
                    Rectangle rect =
                        frame.getGraphicsConfiguration().getBounds();
                    frame.setLocation(rect.x, rect.y);
                    frame.setSize(rect.width, rect.height);
                }
            }
        });

        // Wait for the event queue to settle down after this
        //

        waitForIdle();
    }

    /** Send the given event as appropriate to the event-generation mode. */
    public void sendEvent(AWTEvent event) {
        // Modifiers are ignored, assuming that an event will be
        // sent that causes modifiers to be sent appropriately.  
        if (eventMode == EM_ROBOT) {
            int id = event.getID();
            Log.debug("Sending event id " + id);
            if (id >= MouseEvent.MOUSE_FIRST && id <= MouseEvent.MOUSE_LAST) {
                MouseEvent me = (MouseEvent)event;
                Component comp = me.getComponent();
                if (id == MouseEvent.MOUSE_MOVED) {
                    mouseMove(comp, me.getX(), me.getY());
                }
                else if (id == MouseEvent.MOUSE_DRAGGED) {
                    mouseMove(comp, me.getX(), me.getY());
                }
                else if (id == MouseEvent.MOUSE_PRESSED) {
                    mouseMove(comp, me.getX(), me.getY());
                    mousePress(me.getModifiers() & AWTConstants.BUTTON_MASK);
                }
                else if (id == MouseEvent.MOUSE_ENTERED) {
                    mouseMove(comp, me.getX(), me.getY());
                }
                else if (id == MouseEvent.MOUSE_EXITED) {
                    mouseMove(comp, me.getX(), me.getY());
                }
                else if (id == MouseEvent.MOUSE_RELEASED) {
                    mouseMove(comp, me.getX(), me.getY());
                    mouseRelease(me.getModifiers() & AWTConstants.BUTTON_MASK);
                }
            }
            else if (id >= KeyEvent.KEY_FIRST && id <= KeyEvent.KEY_LAST) {
                KeyEvent ke = (KeyEvent)event;
                if (id == KeyEvent.KEY_PRESSED) {
                    keyPress(ke.getKeyCode());
                }
                else if (id == KeyEvent.KEY_RELEASED) {
                    keyRelease(ke.getKeyCode());
                }
            }
            else {
                Log.warn("Event not supported: " + event);
            }
        }
        else {
            // Post the event to the appropriate AWT event queue
            postEvent((Component)event.getSource(), event);
        }
    }

    /** Return the symbolic name of the given event's ID. */
    public static String getEventID(AWTEvent event) {
        // Optimize here to avoid field name lookup overhead
        switch(event.getID()) {
        case MouseEvent.MOUSE_MOVED: return "MOUSE_MOVED";
        case MouseEvent.MOUSE_DRAGGED: return "MOUSE_DRAGGED";
        case MouseEvent.MOUSE_PRESSED: return "MOUSE_PRESSED";
        case MouseEvent.MOUSE_CLICKED: return "MOUSE_CLICKED";
        case MouseEvent.MOUSE_RELEASED: return "MOUSE_RELEASED";
        case MouseEvent.MOUSE_ENTERED: return "MOUSE_ENTERED";
        case MouseEvent.MOUSE_EXITED: return "MOUSE_EXITED";
        case KeyEvent.KEY_PRESSED: return "KEY_PRESSED";
        case KeyEvent.KEY_TYPED: return "KEY_TYPED";
        case KeyEvent.KEY_RELEASED: return "KEY_RELEASED";
        case WindowEvent.WINDOW_OPENED: return "WINDOW_OPENED";
        case WindowEvent.WINDOW_CLOSING: return "WINDOW_CLOSING";
        case WindowEvent.WINDOW_CLOSED: return "WINDOW_CLOSED";
        case WindowEvent.WINDOW_ICONIFIED: return "WINDOW_ICONIFIED";
        case WindowEvent.WINDOW_DEICONIFIED: return "WINDOW_DEICONIFIED";
        case WindowEvent.WINDOW_ACTIVATED: return "WINDOW_ACTIVATED";
        case WindowEvent.WINDOW_DEACTIVATED: return "WINDOW_DEACTIVATED";
        case ComponentEvent.COMPONENT_MOVED: return "COMPONENT_MOVED";
        case ComponentEvent.COMPONENT_RESIZED: return "COMPONENT_RESIZED";
        case ComponentEvent.COMPONENT_SHOWN: return "COMPONENT_SHOWN";
        case ComponentEvent.COMPONENT_HIDDEN: return "COMPONENT_HIDDEN";
        case FocusEvent.FOCUS_GAINED: return "FOCUS_GAINED";
        case FocusEvent.FOCUS_LOST: return "FOCUS_LOST";
        case HierarchyEvent.HIERARCHY_CHANGED: return "HIERARCHY_CHANGED";
        case HierarchyEvent.ANCESTOR_MOVED: return "ANCESTOR_MOVED";
        case HierarchyEvent.ANCESTOR_RESIZED: return "ANCESTOR_RESIZED";
        case PaintEvent.PAINT: return "PAINT";
        case PaintEvent.UPDATE: return "UPDATE";
        case ActionEvent.ACTION_PERFORMED: return "ACTION_PERFORMED";
        case InputMethodEvent.CARET_POSITION_CHANGED: return "CARET_POSITION_CHANGED";
        case InputMethodEvent.INPUT_METHOD_TEXT_CHANGED: return "INPUT_METHOD_TEXT_CHANGED";
        default:
            return Reflector.getFieldName(event.getClass(), event.getID(), "");
        }
    }

    public static Class getCanonicalClass(Class refClass) {
        // Don't use classnames from anonymous inner classes...
        // Don't use classnames from platform LAF classes...
        String className = refClass.getName();
        while (className.indexOf("$") != -1
               || className.startsWith("javax.swing.plaf")
               || className.startsWith("com.apple.mrj")) {
            refClass = refClass.getSuperclass();
            className = refClass.getName();
        }
        return refClass;
    }

    /** Provides a more concise representation of the component than the
     * default Component.toString().
     */
    public static String toString(Component comp) {
        if (comp == null)
            return "(null)";

        if (AWT.isTransientPopup(comp)) {
            boolean tooltip = AWT.isToolTip(comp);
            if (AWT.isHeavyweightPopup(comp)) {
                return tooltip 
                    ? Strings.get("component.heavyweight_tooltip")
                    : Strings.get("component.heavyweight_popup");
            }
            else if (AWT.isLightweightPopup(comp)) {
                return tooltip 
                    ? Strings.get("component.lightweight_tooltip")
                    : Strings.get("component.lightweight_popup");
            }
        }
        else if (AWT.isSharedInvisibleFrame(comp)) {
            return Strings.get("component.default_frame");
        }
        String name = getDescriptiveName(comp);
        String classDesc = descriptiveClassName(comp.getClass());
        if (name == null) {
            if (AWT.isContentPane(comp)) {
                name = Strings.get("component.content_pane");
            }
            else if (AWT.isGlassPane(comp)) {
                name = Strings.get("component.glass_pane");
            }
            else if (comp instanceof JLayeredPane) {
                name = Strings.get("component.layered_pane");
            }
            else if (comp instanceof JRootPane) {
                name = Strings.get("component.root_pane");
            }
            else {
                name = classDesc + " instance";
            }
        }
        else {
            name = "'" + name + "' (" + classDesc + ")";
        }
        return name;
    }

    /** Provide a string representation of the given component (Component or
     * MenuComponent.
     */
    public static String toString(Object obj) {
        if (obj instanceof Component)
            return toString((Component)obj);
        else if (obj instanceof MenuBar)
            return "MenuBar";
        else if (obj instanceof MenuItem)
            return ((MenuItem)obj).getLabel();
        return obj.toString();
    }

    protected static String descriptiveClassName(Class cls) {
        StringBuffer desc = new StringBuffer(simpleClassName(cls));
        Class coreClass = getCanonicalClass(cls);
        String coreClassName = coreClass.getName();
        while (!coreClassName.startsWith("java.awt.")
               && !coreClassName.startsWith("javax.swing.")
                    && !coreClassName.startsWith("java.applet.")) {
            coreClass = coreClass.getSuperclass();
            coreClassName = coreClass.getName();
        }
        if (!coreClass.equals(cls)) {
            desc.append("/");
            desc.append(simpleClassName(coreClass));
        }
        return desc.toString();
    }

    /** Provides the hierarchic path of the given component by component
        class, e.g. "JFrame:JRootPane:JPanel:JButton".
    */
    public static String toHierarchyPath(Component c) {
        StringBuffer buf = new StringBuffer();
        Container parent = c.getParent();
        if (parent != null) {
            buf.append(toHierarchyPath(parent));
            buf.append(":");
        }
        buf.append(descriptiveClassName(c.getClass()));
        String name = getDescriptiveName(c);
        if (name != null) {
            buf.append("(");
            buf.append(name);
            buf.append(")");
        }
        else if (parent != null
                 && parent.getComponentCount() > 1
                 && c instanceof JPanel) {
            buf.append("[");
            buf.append(String.valueOf(getIndex(parent, c)));
            buf.append("]");
        }
        return buf.toString();
    }

    /** Provide a more concise representation of the event than the default
     * AWTEvent.toString().
     */
    public static String toString(AWTEvent event) {
        String name = toString(event.getSource());
        String desc = getEventID(event);
        if (event.getID() == KeyEvent.KEY_PRESSED
            || event.getID() == KeyEvent.KEY_RELEASED) {
            KeyEvent ke = (KeyEvent)event;
            desc += " (" + AWT.getKeyCode(ke.getKeyCode());
            if (ke.getModifiers() != 0) {
                desc += "/" + AWT.getKeyModifiers(ke.getModifiers());
            }
            desc += ")";
        }
        else if (event.getID() == InputMethodEvent.INPUT_METHOD_TEXT_CHANGED) {
            desc += " (" + ((InputMethodEvent)event).getCommittedCharacterCount() + ")";
        }
        else if (event.getID() == KeyEvent.KEY_TYPED) {
            char ch = ((KeyEvent)event).getKeyChar();
            int mods = ((KeyEvent)event).getModifiers();
            desc += " ('" + ch
                + (mods != 0 ? "/" + AWT.getKeyModifiers(mods) : "")
                + "')";
        }
        else if (event.getID() >= MouseEvent.MOUSE_FIRST
                 && event.getID() <= MouseEvent.MOUSE_LAST) {
            MouseEvent me = (MouseEvent)event;
            if (me.getModifiers() != 0) {
                desc += " <" + AWT.getMouseModifiers(me.getModifiers());
                if (me.getClickCount() > 1) {
                    desc += "," + me.getClickCount();
                }
                desc += ">";
            }
            desc += " (" + me.getX() + "," + me.getY() + ")";
        }
        else if (event.getID() == HierarchyEvent.HIERARCHY_CHANGED) {
            HierarchyEvent he = (HierarchyEvent)event;
            long flags = he.getChangeFlags();
            String type = "";
            String bar = "";
            if ((flags & HierarchyEvent.SHOWING_CHANGED) != 0) {
                type += (he.getComponent().isShowing() ? "" : "!") 
                    + "SHOWING"; bar = "|";
            }
            if ((flags & HierarchyEvent.PARENT_CHANGED) != 0) {
                type += bar + "PARENT:" 
                    + toString(he.getComponent().getParent()); bar = "|";
            }
            if ((flags & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                type += bar + "DISPLAYABILITY"; 
            }
            desc += " (" + type + ")";
        }
        return desc + " on " + name;
    }

    /** Return the numeric event ID corresponding to the given string. */
    public static int getEventID(Class cls, String id) {
        return Reflector.getFieldValue(cls, id);
    }

    /** Strip the package from the class name. */
    public static String simpleClassName(Class cls) {
        String name = cls.getName();
        int dot = name.lastIndexOf(".");
        return name.substring(dot+1, name.length());
    }

    private AWTEvent lastEventPosted = null;
    private MouseEvent lastMousePress = null;
    private boolean countingClicks = false;

    /** Post the given event to the corresponding event queue for the given
        component. */
    protected void postEvent(Component comp, AWTEvent ev) { 
        if (Log.isClassDebugEnabled(Robot.class))
            Log.debug("POST: " + toString(ev));
        if (eventMode == EM_AWT
            && AWT.isAWTPopupMenuBlocking()) {
            throw new Error("Event queue is blocked by an active AWT PopupMenu");
        }
        // Force an update of the input state, so that we're in synch
        // internally.  Otherwise we might post more events before this
        // one gets processed and end up using stale values for those events.
        state.update(ev);
        EventQueue q = getEventQueue(comp);
        q.postEvent(ev);
        delay(autoDelay);
        AWTEvent prev = lastEventPosted;
        lastEventPosted = ev;
        if (ev instanceof MouseEvent) {
            if (ev.getID() == MouseEvent.MOUSE_PRESSED) {
                lastMousePress = (MouseEvent)ev;
                countingClicks = true;
            }
            else if (ev.getID() != MouseEvent.MOUSE_RELEASED
                     && ev.getID() != MouseEvent.MOUSE_CLICKED) {
                countingClicks = false;
            }
        }

        // Generate a click if there are no events between press/release
        // Unfortunately, I can only guess how the VM generates them
        if (eventMode == EM_AWT
            && ev.getID() == MouseEvent.MOUSE_RELEASED
            && prev.getID() == MouseEvent.MOUSE_PRESSED) {
            MouseEvent me = (MouseEvent)ev;
            AWTEvent click = new MouseEvent(comp,
                                            MouseEvent.MOUSE_CLICKED,
                                            System.currentTimeMillis(),
                                            me.getModifiers(),
                                            me.getX(), me.getY(), 
                                            me.getClickCount(),
                                            false);
            postEvent(comp, click);
        }
    }

    /**Wait for the given Condition to return true.  The default timeout may
     * be changed by setting abbot.robot.default_delay.
     * @throws WaitTimedOutException if the default timeout (30s) is exceeded.
     * @see Robot#wait(Condition,long,int) Robot.wait For a description of the use of this function to support lazy loading in class files
     */
    public static void wait(Condition condition) {
        wait(condition, defaultDelay);
    }

    /**Wait for the given Condition to return true, waiting for timeout ms.
     * @throws WaitTimedOutException if the timeout is exceeded.
     * @see Robot#wait(Condition,long,int) Robot.wait For a description of the use of this function to support lazy loading in class files
     */
    public static void wait(Condition condition, long timeout) {
        wait(condition, timeout, SLEEP_INTERVAL);
    }

    /**
     * Wait for the given Condition to return true, waiting for timeout ms,
     * polling at the given interval. This method can be used generically to
     * support components that are lazily loaded in the context of writing
     * testers or other testing code.
     * <p>
     * Take for example the simple case of trying to select an item in a list.
     * If the model for this list is populate asynchronously then you may need
     * to poll the list for a given amount of time until the item appears. This
     * is very common in an application that contains many long running tasks.
     * <p>
     * A general solution to the problem can be seen in this pseudo code:
     * <pre>
     * wait(new Condition()
     * {
     * public boolean test()
     * {
     * return whether the ui element is avaliable
     * }
     * });
     * performAction(ui element);
     * </pre>
     * <p>
     * If you are writing a tester method then it is a good idea to use a standard
     * timeout such as {@link Robot#componentDelay}to ensure they are consistently
     * handled accross different testers.
     *
     * @throws WaitTimedOutException if the timeout is exceeded.
     */
    public static void wait(Condition condition, long timeout, int interval) {
        // Make sure the robot is running
        initializeRobot();
        //
        long start = System.currentTimeMillis();
        while (!condition.test()) {
           
            if (Thread.interrupted()) {
               throw new InterruptedAbbotException("Interrupted waiting for " + condition);
            }
           
            if (System.currentTimeMillis() - start > timeout) {
                String msg = "Timed out waiting for " + condition;
                throw new WaitTimedOutException(msg);
            }
            delay(interval);
        }
    }

    public void reset() {
        if (eventMode == EM_ROBOT) {
            Dimension d = toolkit.getScreenSize();
            mouseMove(d.width/2, d.height/2);
            mouseMove(d.width/2-1, d.height/2-1);
        }
        else {
            // clear any held state
            state.clear();
        }
    }
    
    /**
     * Helper function to invoke callable statement on EDT
     * @param callable The code to invoke
     * @param context The conext component to fetch the event queue from
     * @return
     */
    public static <T> T callAndWait(Component context, Callable<T> callable) {
        return callAndWait(context, callable, componentDelay);
    }
    
    /**
     * Helper function to invoke callable statement on EDT
     * @param callable The code to invoke
     * @param context The conext component to fetch the event queue from
     * @return
     */
    public static <T> T callAndWait(Component context, Callable<T> callable, long timeout) {

        EventQueue queue = getEventQueue(context);
        
        // It turns out that getEventQueue doesn't always keep up with
        // the changing queue situations so the safest route is to just
        // see if the current thread in the event queue, if so it should
        // be safe to invoke directly
        //
        // It turns out that EventQueue.isDispatchThread can sometimes lie
        // if we are in the process of shutting down one thread and poppping
        // on the other as in the case of Abbot
        
        Thread currentThread = Thread.currentThread();
        final Class threadClass = currentThread.getClass();
        boolean runAsIfThisIsDispatchThread;
        try {
            Class clzz = Class.forName("java.awt.EventDispatchThread");
            runAsIfThisIsDispatchThread = clzz.isAssignableFrom(threadClass);            
        } catch (ClassNotFoundException e) {
            runAsIfThisIsDispatchThread = EventQueue.isDispatchThread() && queue == getEventQueue(null);
        }
        
        // Now we have a problem in some test case for example creating a new JFrame on a non event
        // queue is causing problems, this is a valid case as setVisible or pack as not been called
        // but it means that the Tree lock is going to be held by another thread temporarily
        // So we need to test for that and if this is the case then run sychornously as the event
        // queue will get stuck until this is release.
        
        if (context!=null && Thread.holdsLock(context.getTreeLock())) {
            runAsIfThisIsDispatchThread = true;
        }
        

        try {
            // Just invoke if we are on the same EDT as the component
            
            if (runAsIfThisIsDispatchThread) {
                return callable.call();
            }
            else {
                
                
                
                // TODO make sure we use the correct event queue, this method should not be eventually be static
                //
                
                FutureTask<T> ft = new FutureTask<T>(callable);
                
                //

                queue.postEvent(new InvocationEvent(toolkit, ft));
                
                
                //
                
                try {
                    return ft.get(
                               timeout, TimeUnit.MILLISECONDS);
                } 
                catch (TimeoutException te) {
                    ft.cancel(true);
                    throw new CallTimoutFailedException("Failure to run process on event queue, is it blocked?", te); 
                }
            }
        } catch (CallTimoutFailedException e) {
            throw e;
        } catch (ExecutionException e) {
            throw new FailedException("Failure to run process on event queue", e.getCause());
        } catch (Exception e) {
            throw new FailedException("Failure to run process on event queue", e);
        }
        
    }


        /**
         * Helper class that invokes a conditions on the EDT
         */
        public static class ConditionEDTDecorator implements Condition {

        private Condition condition;
        private Component component;

        public ConditionEDTDecorator(Component context, Condition condition) {
            super();
            this.condition = condition;
            this.component = context;
        }

        public boolean test() {
            return callAndWait(component, new Callable<Boolean>() {
                    public Boolean call() {
                      return condition.test() ? Boolean.TRUE : Boolean.FALSE;
                    }
                }) == Boolean.TRUE;
        }
        
        public String toString() {
            
            return "EDT Decorator:\n " + callAndWait(component, new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return condition.toString();
                }
            });
        }
    }
    

    /** Return the Component which currently owns the focus. */
    public Component findFocusOwner() {
        return AWT.getFocusOwner();
    }

    /** Return a descriptive name for the given component for use in UI
     * text (may be localized if appropriate and need not be re-usable
     * across locales.
     */ 
    public static String getDescriptiveName(Component c) {
        if (AWT.isSharedInvisibleFrame(c))
            return Strings.get("component.default_frame");
    
        String name = getName(c);
        if (name == null) {
            if ((name = getTitle(c)) == null) {
                if ((name = getText(c)) == null) {
                    if ((name = getLabel(c)) == null) {
                        if ((name = getIconName(c)) == null) {
                        }
                    }
                }
            }
        }
        return name;
    }
    
    public static String getName(Component c) {
        String name = AWT.hasDefaultName(c)
            ? null : c.getName();
        // Accessibility behaves like what we used to do with getTag.
        // Not too helpful for our purposes, especially when the
        // data on which the name is based might be dynamic.
        /*
        if (name == null) {
            AccessibleContext context = c.getAccessibleContext();
            if (context != null) 
                name = context.getAccessibleName();
        }
        */
        return name;
    }

    /** Returns the index of the given component within the given container. */
    public static int getIndex(Container parent, Component comp) {
        if (comp instanceof Window) {
            
            // getOwnedWindows is protoected by a synchronized block so this
            // is probably safe to call
            //
            
            Window[] owned = ((Window)parent).getOwnedWindows();
            for (int i=0;i < owned.length;i++) {
                if (owned[i] == comp) {
                    return i;
                }
            }
        }
        else {
            
            // Getting a list of components need to be protected by the tree lock
            // at least possibility if this is a problem then we are going to 
            // have to do a callAndWait instead.
            //
            
            synchronized(comp.getTreeLock())
            {
                Component[] children = parent.getComponents();
                for (int i = 0; i < children.length; ++i) {
                    if (children[i] == comp) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }


    public static String getText(final Component c) {
        if (c instanceof AbstractButton) {
            return ComponentTester.stripHTML(((AbstractButton)c).getText());
        }
        else if (c instanceof JLabel) {
            return ComponentTester.stripHTML(((JLabel)c).getText());
        }
        else if (c instanceof Label) {
            return ((Label)c).getText();
        }
        return null;
    }
    
    
    public static String getLabel(Component c) {
        String label = null;
        if (c instanceof JComponent) {
            Object obj =
                ((JComponent)c).getClientProperty(LABELED_BY_PROPERTY);
            // While the default is a JLabel, users may use something else as
            // the property, so be careful.
            if (obj != null) {
                if (obj instanceof JLabel) {
                    label = ((JLabel)obj).getText();
                }
                else if (obj instanceof String) {
                    label = (String)obj;
                }
            }
        }
        else if (c instanceof Button) {
            label = ((Button)c).getLabel();
        }
        else if (c instanceof Checkbox) {
            label = ((Checkbox)c).getLabel();
        }
        return ComponentTester.stripHTML(label);
    }

    private static final Object ACCESSIBLE_CONTEXT_CACHE = new Object() {};

    public static String getIconName(final Component c) {
        
        if (c instanceof JComponent)
        {
            // So we had a case where getting hold of the AccessibleContext was causing
            // work to be done off the event queue; but invoking this code everytime on a particular
            // component proved to be expense, so using a clientProperty to cache this value, just
            // calling the method the first time should it not exist. Appears to be a safe thing
            // to do after a brief review of the swing classes
            AccessibleContext ac = (AccessibleContext)((JComponent)c).getClientProperty(ACCESSIBLE_CONTEXT_CACHE);
            // 
            if (ac==null) {
                ac = callAndWait(c, new Callable<AccessibleContext>() {
                                public AccessibleContext call() {
                                    AccessibleContext accessibleContext = c.getAccessibleContext();
                                    ((JComponent)c).putClientProperty(ACCESSIBLE_CONTEXT_CACHE, accessibleContext);
                                    return accessibleContext;
                                }
                
                });
            }
            else {
                int i = 0;
            }
            
            // Otherwise we need to access the accessible context
            
            return getIconName(ac);
            
        }
        else {
            // Just don't think this will happen very much
            return getIconName(c.getAccessibleContext());
        }
    }


    private static String getIconName(AccessibleContext context)
    {
        String icon = null;
        if (context != null) {
            AccessibleIcon[] icons = context.getAccessibleIcon();
            if (icons != null && icons.length > 0) {
                icon = icons[0].getAccessibleIconDescription();
                if (icon != null) {
                    icon = icon.substring(icon.lastIndexOf("/") + 1);
                    icon = icon.substring(icon.lastIndexOf("\\") + 1);
                }
            }
        }
        return icon;
    }


    public static String getBorderTitle(Component c) {
        String title = null;
        if (c instanceof JComponent) {
            title = getBorderTitle(((JComponent)c).getBorder());
        }
        return title;
    }
    /** See javax.swing.JComponent.getBorderTitle. */
    private static String getBorderTitle(Border b) {
        String title = null;
        if (b instanceof TitledBorder)
            title = ((TitledBorder)b).getTitle();
        else if (b instanceof CompoundBorder) {
            title = getBorderTitle(((CompoundBorder)b).getInsideBorder());
            if (title == null) {
                title = getBorderTitle(((CompoundBorder)b).getOutsideBorder());
            }
        }
        return title;
    }

    public static String getTitle(Component c) {
                            if (c instanceof Dialog)
                                return ((Dialog)c).getTitle();
                            else if (c instanceof Frame) 
                                return ((Frame)c).getTitle();
                            else if (c instanceof JInternalFrame)
                                return ((JInternalFrame)c).getTitle();
                            return null;
                        }
    /** Returns whether it is possible to resize windows that are not an
        instance of Frame or Dialog.  Most X11 window managers will allow
        this, but stock Macintosh and Windows do not.
    */
    public static boolean canResizeWindows() {
        return !Platform.isWindows() && !Platform.isMacintosh();
    }

    /** Returns whether it is possible to move windows that are not an
        instance of Frame or Dialog.  Most X11 window managers will allow
        this, but stock Macintosh and Windows do not.
    */
    public static boolean canMoveWindows() {
        return !Platform.isWindows() && !Platform.isMacintosh();
    }

    /** Returns the appropriate auto delay for robot-generated events. 
     */
    public static int getPreferredRobotAutoDelay() {
        // better safe than sorry, and slower and accurate than
        // fast and inaccurate.
        /*
        if (Platform.isWindows() || Platform.isOSX() || Platform.isX11())
            return 0;
        */
        // >= 40 causes problems registering a double-click when clicks
        // are requested separately (due to auto-jitter and other delay 
        // being added on individual clicks).
        return 30;
    }
}
