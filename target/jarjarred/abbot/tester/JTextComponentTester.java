package abbot.tester;

import java.awt.*;
import javax.swing.*;
import javax.swing.text.*;

import abbot.Log;
import abbot.Platform;
import abbot.i18n.Strings;

import abbot.tester.Robot.ConditionEDTDecorator;

import abbot.util.Condition;

import java.util.concurrent.Callable;

import javax.swing.text.DefaultEditorKit;

/** Provides actions and assertions {@link JTextComponent}-based
 * components.
 */ 
public class JTextComponentTester extends JComponentTester {


    /**
     * @return The text contained in this component
     */
    protected String getTextForComponent(final JTextComponent component) {
        return callAndWait(component,new Callable<String>() {
                public String call() {
                    Document doc = component.getDocument();
                    try {
                        return doc.getText(0, doc.getLength());
                    } catch (BadLocationException e) {
                        e.printStackTrace();
                        return "";
                    }
                }
            });

    }


    /**
     * Type the given text into the given component, replacing any existing
     * text already there.  If the empty string or <code>null</code> is given,
     * simply removes all existing text.
     */
    public void actionEnterText(Component c, String text) {
        scrollToVisible(c, 0);
        actionActionMap(c, DefaultEditorKit.selectAllAction);
        if (text == null || "".equals(text)) {
            if (!"".equals(getTextForComponent((JTextComponent)c))) {
                actionActionMap(c, DefaultEditorKit.deletePrevCharAction);
            }
        }
        else {
            actionKeyString(c, text);
        }
    }

    /** Click at the given index position. */
    public void actionClick(Component tc, int index) {
        Point where = scrollToVisible(tc, index);
        actionClick(tc, where.x, where.y);
    }
    
    /** Click at the middle index of the first instance of this text, useful for selecting hyperlinks */
    public void actionClick(Component comp, String textToClick) {
        final JTextComponent tc = (JTextComponent)comp;
        
        // Get the text content and try to work out the offset
        //
        
        String componentText = getTextForComponent(tc);
        
        int index = componentText.indexOf(textToClick);
        
        // If we cannot find the text then fail
        //
        if (index==-1) {
            String msg = Strings.get("tester.JTextComponent.click_text_failed",
                                     new Object[] {
                                         textToClick
                                     });
            throw new ActionFailedException(msg);
        }
        
        // Now try to click in the middle of this text
        //

        actionClick(tc, index + textToClick.length()/2);
    }

    public void actionSetCaretPosition(Component tc, int index) {
        actionClick(tc, index);
    }

    /** Move the pointer to the given index location.  Takes care of
     * auto-scrolling through text.
     */
    // TODO move this to a JTextComponentLocation and rely on existing
    // mechanisms to do the scrolling.
    protected Point scrollToVisible(Component c, final int index) {
        final JTextComponent tc = (JTextComponent)c;
        try {

            final Callable<Rectangle> indexToRectangle = new Callable<Rectangle>() {
                @Override
                public Rectangle call() throws BadLocationException {
                    Rectangle rect = tc.modelToView(index);
                    return rect;
                }
            };

            final Callable<Rectangle> visibleRect = new Callable<Rectangle>() {

                @Override
                public Rectangle call() throws BadLocationException {
                    return tc.getVisibleRect();
                }
            };
           
            // Wait for the component to contain the index in question
            //
           
            Robot.wait(new ConditionEDTDecorator(c,new Condition() {
                        @Override
                        public boolean test() {
                            try {
                                Rectangle rect = indexToRectangle.call();
                                return rect!=null;
                            } catch (Exception e) {
                                return false;
                            }
                        }
                    }));

            //

            final Rectangle visible = callAndWait(c, visibleRect);
            Rectangle rect = callAndWait(c, indexToRectangle);
            
            Log.debug("visible=" + visible + ", index="
                      + index + " is at " + rect);
            if (rect == null) {
                String msg = Strings.get("tester.zero_size");
                throw new ActionFailedException(msg);
            }
            // Autoscroll on JTextComponent is a bit flakey
            if (!visible.contains(rect.x, rect.y)) {
                scrollRectToVisible(tc, rect);
                Rectangle newVisible = callAndWait(c,visibleRect);
                rect = callAndWait(c,indexToRectangle);
                Log.debug("visible=" + newVisible + " caret=" + rect);
                if (!newVisible.contains(rect.x, rect.y)) {
                    String msg = Strings.get("tester.JComponent.not_visible",
                                             new Object[] { 
                                                 new Integer(rect.x),
                                                 new Integer(rect.y), 
                                                 tc,
                                             });
                    throw new ActionFailedException(msg);
                }
            }
            return new Point(rect.x + rect.width/2,
                             rect.y + rect.height/2);
        }
        // No longer thrown on this event queue
//        catch(BadLocationException ble) {
//            String msg = Strings.get("tester.JTextComponent.bad_location",
//                                     new Object[] { 
//                                         ble.getMessage(), 
//                                         new Integer(index),
//                                         tc.getText()
//                                     });
//            throw new ActionFailedException(msg);
//        }
        finally {
            
        }
    }
    
    /** Account for differences in scrolling {@link javax.swing.JTextField}.
        @see JComponentTester#scrollRectToVisible
        @see JComponent#scrollRectToVisible
    */
    protected void scrollRectToVisible(JComponent c, Rectangle rect) {
        super.scrollRectToVisible(c, rect);
        // Taken from JComponent
        if (!isVisible(c, rect) && c instanceof JTextField) {
            int dx = c.getX();
            int dy = c.getY();
            Container parent;
            for (parent = c.getParent();
                 !(parent == null)
                 && !(parent instanceof JComponent)
                 && !(parent instanceof CellRendererPane);
                 parent = parent.getParent()) {
                 Rectangle bounds = parent.getBounds();
                 dx += bounds.x;
                 dy += bounds.y;
            }
            if (!(parent == null) && !(parent instanceof CellRendererPane)) {
                rect.x += dx;
                rect.y += dy;
                super.scrollRectToVisible((JComponent)parent, rect);
                rect.x -= dx;
                rect.y -= dy;
            }
        }
    }

    /** Equivalent to JTextComponent.setCaretPosition(int), but operates
     * through the UI.
     */
    protected void startSelection(Component comp, int index) {
        final JTextComponent tc = (JTextComponent)comp;
        // Avoid automatic drag/drop if the selection start is already
        // part of a selection (OSX has setDragEnabled true by default). 
        if (tc.getSelectionStart() != tc.getSelectionEnd()) {
            invokeAndWait(new Runnable() {
                public void run() {
                    tc.setCaretPosition(0);
                    tc.moveCaretPosition(0);
                }
            });
        }
        Point where = scrollToVisible(comp, index);
        mousePress(comp, where.x, where.y);
    }

    /** Equivalent to JTextComponent.moveCaretPosition(int), but operates
     * through the UI.
     */
    protected void endSelection(Component comp, int index) {
        
        final JTextComponent tc = (JTextComponent)comp;
        
        // Depending on whether we are performing a normal
        // or revese select we need to nudge the co-ordinate slightly
        final int caretPos = callAndWait(tc, new Callable<Integer>(){
                @Override
                public Integer call() {
                    return tc.getCaretPosition();
                }
            });
        
        int nudge = 0;
        if (index < caretPos) {
            nudge = 0;
        }
        else {
            nudge = -1;
        }
        
        Point where = scrollToVisible(comp, index);
        
        // Get the current value based on the caret poss
        //
        
        final Callable<Point> mousePosition = new Callable<Point>() {
            @Override
            public Point call() throws BadLocationException {
                Point rect = tc.getMousePosition();
                return rect;
            }
        };

// Remove temporarily as this code breaks a few other cases
// will fix on next commit
//        Point mousePos = Robot.callAndWait(tc, mousePosition);
//        if (mousePos!=null)
//        {
//            // In some cases the text will scroll underneath the current component
//            // so the actual distance dragged is going to be very small if not zero
//            // so we are going to need to inject some minor moves, just enough
//            // to trigger the drag on the relavent platform.
//    
//            int distance = (int)Math.sqrt(
//                               Math.pow((where.x - mousePos.x), 2) +
//                               Math.pow((where.y - mousePos.y), 2));
//            
//            if (Math.abs(distance) < AWTConstants.DRAG_THRESHOLD) {
//                mouseMove(comp, where.x - AWTConstants.DRAG_THRESHOLD, where.y);
//            }
//        }
        
        //
        
        mouseMove(comp, where.x, where.y + nudge);
        if (Platform.isOSX())
            delay(75);
        mouseRelease();
    }

    /** Start a selection at the given index. */
    public void actionStartSelection(Component comp, int index) {
        startSelection(comp, index);
        waitForIdle();
    }

    /** Terminate a selection on the given index. */
    public void actionEndSelection(Component comp, int index) {
        endSelection(comp, index);
        waitForIdle();
    }

    /** Select the given text range.
        @deprecated Use actionSelectText instead.
     */
    public void actionSelect(Component comp, int start, int end) {
        actionSelectText(comp, start, end);
    }

    /** Select the given text range. */
    public void actionSelectText(Component comp, int start, int end) {
        // An idle wait is sometimes required, otherwise the mouse press is
        // never registered (w32, 1.4) 
        
        actionStartSelection(comp, start);
        actionEndSelection(comp, end);

        // Verify the selection was properly made
        JTextComponent tc = (JTextComponent)comp;
        if (!(tc.getSelectionStart() == Math.min(start, end)
              && tc.getSelectionEnd() == Math.max(start, end))) {
            String msg = Strings.get("tester.JTextComponent.selection_failed",
                                     new Object[] {
                                         new Integer(start), new Integer(end),
                                         new Integer(tc.getSelectionStart()),
                                         new Integer(tc.getSelectionEnd()),
                                     });
            throw new ActionFailedException(msg);
        }
        
        // Prevent spurious double click events by ensureing next event is the right distance apart
        delay(AWTConstants.MULTI_CLICK_INTERVAL);
    }
    
    /** Select the given text in the document. */
    public void actionSelectText(Component comp, String textToSelect) {

        final JTextComponent tc = (JTextComponent)comp;
        
        // Get the text content and try to work out the offset
        //
        
        String componentText = getTextForComponent(tc);
        
        int index = componentText.indexOf(textToSelect);
        
        // If we cannot find the text then fail
        //
        if (index==-1) {
            String msg = Strings.get("tester.JTextComponent.selection_text_failed",
                                     new Object[] {
                                         textToSelect
                                     });
            throw new ActionFailedException(msg);
        }
        
        // Now try to select that text
        //
        
        actionSelectText(comp, index, index+textToSelect.length());
    }    
}
