package abbot.tester;

import abbot.Log;

import java.awt.*;
import java.awt.event.InputEvent;

import java.lang.reflect.InvocationTargetException;

import javax.accessibility.AccessibleContext;

import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.*;
import javax.swing.plaf.basic.BasicTreeUI;

import abbot.WaitTimedOutException;
import abbot.script.ArgumentParser;
import abbot.util.AWT;
import abbot.util.Condition;
import abbot.i18n.Strings;


import java.lang.reflect.Method;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import java.util.concurrent.TimeUnit;

import javax.accessibility.AccessibleText;

import javax.swing.JComponent;
import javax.swing.event.TreeSelectionListener;

/** Provide operations on a JTree component.
    The JTree substructure is a "row", and JTreeLocation provides different
    identifiers for a row.
    <ul>
    <li>Select an item by row index
    <li>Select an item by tree path (the string representation of the full
    path).
    </ul>
    @see abbot.tester.JTreeLocation
 */
// TODO: multi-select
// TODO: expand/collapse actions
public class JTreeTester extends JComponentTester {

    /** Returns whether the given point is in one of the JTree's node
     * expansion controls.
     */ 
    public static boolean isLocationInExpandControl(JTree tree, int x, int y) {
        int row = tree.getRowForLocation(x, y);
        if (row == -1) {
            row = tree.getClosestRowForLocation(x, y);
            if (row != -1) {
                Rectangle rect = tree.getRowBounds(row);
                if (row == tree.getRowCount()-1) {
                    if (y >= rect.y + rect.height)
                        return false;
                }
                // An approximation: use a square area to the left of the row
                // bounds. 
                TreePath path = tree.getPathForRow(row);
                if (path == null || tree.getModel().
                    isLeaf(path.getLastPathComponent()))
                    return false;

                if (tree.getUI() instanceof BasicTreeUI) {
                    try {
                        java.lang.reflect.Method method = 
                            BasicTreeUI.class.
                            getDeclaredMethod("isLocationInExpandControl",
                                              new Class[] { 
                                                  TreePath.class,
                                                  int.class, int.class,
                                              });
                        method.setAccessible(true);
                        Object b = method.invoke(tree.getUI(), new Object[] {
                            path, new Integer(x), new Integer(y),
                        });
                        return b.equals(Boolean.TRUE);
                    }
                    catch(Exception e) {
                    }
                }
                // fall back to a best guess
                //return x >= rect.x - rect.height && x < rect.x;
                String msg = "Can't determine location of tree expansion "
                    + "control for " + tree.getUI();
                throw new LocationUnavailableException(msg);
            }
        }
        return false;
    }

    /** Return a unique {@link String} representation of the final component of 
     * the given {@link TreePath}, or <code>null</code> if one can not be
     * obtained.  Assumes the path is visible.  
     */
    public static String valueToString(JTree tree, TreePath path) {
        return valueToString(tree, path, true);
    }
    
    
    
    
    /** Return the {@link String} representation of the final component of the
     * given {@link TreePath}, or <code>null</code> if one can not be
     * obtained.  Assumes the path is visible.
     * Optionally include a unique trailing index.  
     */
    private static String valueToStringInternal(final JTree tree, final TreePath path, final boolean includeIndex) {
        Object value = path.getLastPathComponent();
        int row = tree.getRowForPath(path);
        // The default renderer will rely on JTree.convertValueToText
        Component cr = tree.getCellRenderer().
            getTreeCellRendererComponent(tree, value, false,
                                         tree.isExpanded(row), 
                                         tree.getModel().isLeaf(value),
                                         row, false);
        String string = convertRendererToString(cr);
        
        if (string == null) {
            string = tree.convertValueToText(value, false,
                                             tree.isExpanded(row),
                                             tree.getModel().isLeaf(value),
                                             row, false);
            if (ArgumentParser.isDefaultToString(string))
                string = null;
        }
        
        if (string == null) {
            String s = ArgumentParser.toString(value);
            string = s == ArgumentParser.DEFAULT_TOSTRING ? null : s;
        }
        
        if (includeIndex) {
            // If there are preceding siblings which produce the same
            // string value, obtain one that is unique by adding an index
            TreePath parentPath = path.getParentPath();
            if (parentPath != null) {
                Object parent = parentPath.getLastPathComponent();
                int idx = tree.getModel().getIndexOfChild(parent, value);
                int count = 0;
                for (int i=0;i < idx;i++) {
                    Object child = tree.getModel().getChild(parent, i);
                    TreePath childPath = parentPath.pathByAddingChild(child);
                    String text = valueToString(tree, childPath, false); 
                    // string == text deals with double null null case
                    if ((string==text) || (string!=null && string.equals(text))) {
                        ++count;
                    }
                }
                
                // If the string is null then ensure that we at least
                // traces something out
                //
                
                if (count > 0 || string == null) {
                    if (string!=null) {
                        string += "[" + count + "]";
                    }
                    else {
                        string = "[" + count + "]";
                    }
                }
            }
        }
        return string;
    }

    /**
     * @param tree The tree to dump
     * @return A textual representaiton of the current tree
     */
    public static String dumpTree(final JTree tree) {
        String treeDump = Robot.callAndWait(tree, new Callable<String>()
        {
            @Override
            public String call() throws Exception {
                StringBuilder sb = new StringBuilder();
                try {
                    dumpTree(sb, tree, new TreePath(tree.getModel().getRoot()), "");
                    return sb.toString();
                }
                catch (Exception ex) {
                    return "Problem dumping tree,"+ ex.getMessage() + ", so far:\n " + sb.toString();
                }
            }
        });
        return treeDump;
    }
    
    
    private static void dumpTree(StringBuilder sb, JTree tree, TreePath path, String prefix) {
        
        String currentName = JTreeTester.valueToString(tree, path);
        sb.append(prefix).append(currentName).append('\n');
        Object parent = path.getLastPathComponent();
        int count = tree.getModel().getChildCount(parent);
        for (int i = 0; i < count; i++) {
            dumpTree(sb, tree, new TreePath(path, tree.getModel().getChild(parent, i)) {}, prefix + "-");
        }

    }



    /** Return the {@link String} representation of the final component of the
     * given {@link TreePath}, or <code>null</code> if one can not be
     * obtained.  Assumes the path is visible.
     * Optionally include a unique trailing index. Ensures operation is performed
     * on EDT
     */
    private static String valueToString(final JTree tree, final TreePath path, final boolean includeIndex) {
        // Ensure this work is done on the EDT, TODO move this
        // code up to Robot if we are sure we need to reuse it
        //

        return Robot.callAndWait(tree, new Callable<String>() {
            public String call() {
                return valueToStringInternal(tree, path, includeIndex);               
            }
        });
    }

    /** Return the String representation of the given TreePath, or null if one
     * can not be obtained.  Assumes the path is visible. 
     */
    public static TreePath pathToStringPath(JTree tree, TreePath path) {
        if (path == null)
            return null;

        String string = valueToString(tree, path);
        
        // Special case for a null value when this is a hidden root element
        //
        
        if (string == null &&
            path.getParentPath() == null &&
            !tree.isRootVisible()) {
            return path;
        }
        
        // 
        //
        
        if (string != null) {
            // Prepend the parent value, if any
            if (path.getPathCount() > 1) {
                TreePath parent = pathToStringPath(tree, path.getParentPath());
                if (parent == null)
                    return null;
                return parent.pathByAddingChild(string);
            }
            return new TreePath(string);
        }
        return null;
    }


    /**
     * Converts a tree location to a tree path safely by invoking the required
     * methods on the EDT
     * @param tree
     * @param location
     * @return The converted tree path
     */
    private TreePath convertLocationSafelyToPath(final JTree tree,
                                                 final JTreeLocation location) {
        return Robot.callAndWait(tree, new Callable<TreePath>() {

                public TreePath call() {
                    return location.getPath(tree);
                }
            });
    }
    

    /** Click at the given location.  If the location indicates a path, ensure
        it is visible first.
    */
    public void actionClick(Component c, ComponentLocation loc) {
        if (loc instanceof JTreeLocation) {
            TreePath path = convertLocationSafelyToPath((JTree)c, (JTreeLocation)loc);
            if (path != null)
                makeVisible(c, path);
        }
        super.actionClick(c, loc);
    }

    /** Select the given row.  If the row is already selected, does nothing. */
    public void actionSelectRow(final Component c, final ComponentLocation loc) {
        final JTree tree = (JTree)c;
        if (loc instanceof JTreeLocation) {
            final JTreeLocation jTreeLocation = (JTreeLocation)loc;
            TreePath path = convertLocationSafelyToPath((JTree)c,jTreeLocation);
            if (path == null) {
                String msg = Strings.get("tester.JTree.path_not_found",
                                         new Object[] { loc, JTreeTester.dumpTree(tree) });
                throw new LocationUnavailableException(msg);
            }
            makeVisible(c, path);

            // Scroll to that location, makes the action click easier as the
            // code in there for scrolling is more fragiles as it doesn't
            // have access to the tree path.
            //
            // Need to fetch path again as data can be different now that
            // the tree is properly expanded, also needs to be performed
            // on the EDT
            //

            invokeAndWait(new Runnable() {
                              public void run() {
                                  tree.scrollPathToVisible(jTreeLocation.getPath(tree));            
                              }
                          });
        }
        
        // We had an odd failure case that we could never track down
        // where for small amounts of time we couldn't access the point
        // setting breakpoints would disturb the flow so we never got to
        // the bottom on why it wasn't working.
        // Instead we are using a wait to repeatidy refetch until we get
        // the location we need

        final Point whereF[] = new Point[1];
        
        wait(new ConditionEDTDecorator(tree, new Condition() {
                public boolean test() {
                    try
                    {
                        whereF[0] = loc.getPoint(c);
                        return whereF[0]!=null;
                    }
                    catch (LocationUnavailableException lue) {
                        return false;
                    }
                }
                
                public String toString() {
                    return Strings.get("tester.JTree.path_not_found",
                                         new Object[] { loc, JTreeTester.dumpTree(tree) });
                }
            }), defaultDelay / 4); // Ramp up delay as still getting 
                                                      // intermittent failures
        
        final Point where = whereF[0];
        
        // Find out what the target row is
        //
        final int targetRow = ((JTreeLocation)loc).getRow(tree);
        
        // Check, if it is already selected then we can bail, always best to do this after
        // we have made sure it is visible etc above.
        //
        if (Robot.callAndWait(tree, new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return tree.isRowSelected(targetRow);
            }
        })) {
            // We can just return as the row is already selected
            return;
        }
        
        
        Rectangle rect = Robot.callAndWait(tree, new Callable<Rectangle>() {
                public Rectangle call() {
                    int row = tree.getRowForLocation(where.x, where.y);
                    if (tree.getLeadSelectionRow() != row
                        || tree.getSelectionCount() != 1) {
                        // NOTE: the row bounds *do not* include the expansion handle
                        return tree.getRowBounds(row);
                    }
                    else {
                        return null;
                    }
                }
            });
            
        if (rect!=null)
        {
            // Create a listener so we can monitor the selection
            
            class TemporaryTreeSelectionListener implements TreeSelectionListener {
                
                volatile boolean matched = false;
                
                @Override
                public void valueChanged(TreeSelectionEvent e) {
                    // If the tree matches, all well and good
                    if (tree.getSelectionModel().isRowSelected(targetRow)) {
                        matched = true;
                    }
                }
            };
            final TemporaryTreeSelectionListener tsl = new TemporaryTreeSelectionListener();
            
            
            // Add listener, so we can wait until the tree item is checked
            //
            invokeAndWait(tree, new Runnable() {
                             public void run() {
                                 tree.getSelectionModel().addTreeSelectionListener(tsl);
                             }
                          });
            try
            {
                // NOTE: if there's no icon, this may start editing
                actionClick(tree, rect.x + rect.width/2, rect.y + rect.height/2);
                
                // Wait for the row to be selected at some point
                //
                
                final Robot.ConditionEDTDecorator condition = new Robot.ConditionEDTDecorator(c, new Condition() {
                    public boolean test() {
                        return tsl.matched;
                    }
                
                    public String toString() {
                        return Strings.get("tester.JTree.row_not_selected", new Object[] { loc.toString(), dumpTree(tree),
                         Arrays.toString(tree.getSelectionModel().getSelectionRows()) });
                    }
                });


                try {
                    wait(condition, componentDelay/4);
                }
                catch (WaitTimedOutException wte) {
                    throw wte;               
                }
                
                
            }
            finally {
                // Remove listener
                //
                invokeAndWait(tree, new Runnable() {
                                 public void run() {
                                     tree.getSelectionModel().removeTreeSelectionListener(tsl);
                                 }
                              });
                
            }
        }
        else {
            
            throw new ActionFailedException(Strings.get("tester.JTree.row_not_selected_location", new Object[] { loc.toString(), dumpTree(tree),
                         Arrays.toString(tree.getSelectionModel().getSelectionRows()) }));
        }
    }

    /** Select the given row.  If the row is already selected, does nothing.
        Equivalent to actionSelectRow(c, new JTreeLocation(row)).
     */
    public void actionSelectRow(Component tree, int row) {
        actionSelectRow(tree, new JTreeLocation(row));
    }

    /** Simple click on the given row. */
    public void actionClickRow(Component tree, int row) {
        actionClick(tree, new JTreeLocation(row));
    }

    /** Click with modifiers on the given row.
        @deprecated Use the ComponentLocation version.
     */
    public void actionClickRow(Component tree, int row, String modifiers) {
        actionClick(tree, new JTreeLocation(row), AWT.getModifiers(modifiers));
    }

    /** Multiple click on the given row.
        @deprecated Use the ComponentLocation version.
     */
    public void actionClickRow(Component c, int row,
                               String modifiers, int count) {
        actionClick(c, new JTreeLocation(row), AWT.getModifiers(modifiers), count);
    }

    /** Make the given path visible, if possible, and returns whether any
     * action was taken.
     * @throws LocationUnavailableException if no corresponding path can be
     * found. 
     */ 
    protected boolean makeVisible(Component c, TreePath path) {
        return makeVisible(c, path, false);
    }

    private boolean makeVisible(Component c, final TreePath path,
                                boolean expandWhenFound) {
        return makeVisible(c, path, componentDelay, expandWhenFound);
    }

    private boolean makeVisible(Component c, final TreePath path,
                                final int timeout,
                                boolean expandWhenFound) {
        final JTree tree = (JTree)c;
        // Match, make visible, and expand the path one component at a time,
        // from uppermost ancestor on down, since children may be lazily
        // loaded/created 
        boolean changed = false;
        if (path.getPathCount() > 1) {
            changed = makeVisible(c, path.getParentPath(), timeout, true);
            if (changed)
                waitForIdle();
        }


        final TreePath realPath = getRealPath(path, timeout, tree);
        if (expandWhenFound) {
            
            Callable<Boolean> realPathExanded = new Callable<Boolean>()
                {
                    @Override
                    public Boolean call() {
                        return tree.isExpanded(realPath);
                    }
                };
            
            if (!Robot.callAndWait(tree, realPathExanded)) {
                // Use this method instead of a toggle action to avoid
                // any component visibility requirements
                invokeAndWait(new Runnable() {
                    public void run() {
                        tree.expandPath(realPath);
                    }
                });
            }
            
            // Wait for a child to show up
            try {
                wait(new ConditionEDTDecorator(tree, new Condition() {
                    public boolean test() {
                        
                        // It is possible that the path has been updated, in the test
                        // case where we saw this change the lazily loaded nodes
                        // are replaced with the new correct nodes on expand
                        // so we need to get hold of the new version
                        //

                        final TreePath updatedPath = getRealPath(path, timeout, tree);
                        final Object o = updatedPath.getLastPathComponent();
                        
                        return tree.getModel().getChildCount(o) != 0;
                    }
                    public String toString() {
                        return Strings.get("tester.JTree.path_not_shown",
                                           new Object[] { path.toString(), dumpTree(tree) });
                    }
                }), timeout);
                changed = true;
            }
            catch(WaitTimedOutException e) {
                throw new LocationUnavailableException(e.getMessage());
            }
        }
        return changed;
    }
    

    /**
     * @param path The string path used by abbot
     * @param timeout Timeout to use on query
     * @param tree The tree we are working with
     * @return The real world path for this particular node
     */
    private TreePath getRealPath(final TreePath path, final int timeout,
                                 final JTree tree) {
        // Wait for the node to exist
        //
        
        final TreePath found[] = new TreePath[1];
        
        try {
            wait(new ConditionEDTDecorator(tree, new Condition()
                {
                    public boolean test() {
                        try{
                            found[0] = JTreeLocation.findMatchingPath(tree, path); 
                            return found[0]!=null;
                        }
                        catch (LocationUnavailableException lue) {
                            return false;
                        }
                    }
                    public String toString() {
                        return Strings.get("tester.JTree.path_not_shown",
                                           new Object[] { path.toString(), dumpTree(tree) });
                    }
                }), timeout);
        } catch(WaitTimedOutException e) {
            throw new LocationUnavailableException(e.getMessage());
        }
        
        //

        final TreePath realPath = found[0];
        return realPath;
    }

    /** Ensure all elements of the given path are visible. */
    public void actionMakeVisible(Component c, TreePath path) {
        makeVisible(c, path);
    }

    /** Select the given path, expanding parent nodes if necessary. */
    public void actionSelectPath(Component c, TreePath path) {
        actionSelectRow(c, new JTreeLocation(path));
    }

    
    /** Change the open/closed state of the given row, if possible.
        @deprecated Use the ComponentLocation version instead.
     */
    public void actionToggleRow(Component c, int row) {
        actionToggleRow(c, new JTreeLocation(row));
    }

    /** Change the open/closed state of the given row, if possible. */
    // NOTE: a reasonable assumption is that the toggle control is just to the
    // left of the row bounds and is roughly a square the dimensions of the
    // row height.  clicking in the center of that square should work.
    public void actionToggleRow(Component c, ComponentLocation loc) {
        final JTree tree = (JTree)c;
        
        // Accessing the toggle click count is thread safe; but updating
        // it can fire a properly listener which is going to be an issue in some cases
        // hence this little class to update the value as required
        class SetToggleClickCount implements Runnable {

            int clickCount;
            
            public SetToggleClickCount(int clickCount) {
                this.clickCount = clickCount;
            }

            @Override
            public void run() {
                tree.setToggleClickCount(clickCount);
            }
        }
        
        // It turns out that some trees have double click actions on them
        // so this doesn't work too well. So we are going to increase the click
        // count to 1 just for the toggle operation, this should make things more
        // reliable
        int originalToggleRowCount = tree.getToggleClickCount();
        invokeAndWait(new SetToggleClickCount(1));
        try {
            actionClick(tree, loc, InputEvent.BUTTON1_MASK,
                        tree.getToggleClickCount());
            
        }
        finally {
            invokeAndWait(new SetToggleClickCount(originalToggleRowCount));
        }
        
//        // Alternatively, we can reflect into the UI and do a single click
//        // on the appropriate expand location, but this is safer.
//        if (tree.getToggleClickCount() != 0) {
//            actionClick(tree, loc, InputEvent.BUTTON1_MASK,
//                        tree.getToggleClickCount());
//        }
//        else {
//            // BasicTreeUI provides this method; punt if we can't find it
//            if (!(tree.getUI() instanceof BasicTreeUI))
//                throw new ActionFailedException("Can't toggle row for "
//                                                + tree.getUI());
//            try {
//                final java.lang.reflect.Method method =
//                    BasicTreeUI.class.
//                    getDeclaredMethod("toggleExpandState",
//                                      new Class[] {
//                                          TreePath.class
//                                      }); 
//                method.setAccessible(true);
//                final Point where = loc.getPoint(tree);
//                Robot.callAndWait(tree, new Callable<Object>() {
//                        public Object call() throws Exception {
//                            return method.invoke(tree.getUI(), new Object[] {
//                                tree.getPathForLocation(where.x, where.y)});
//                        }
//                    });
//            }
//            catch(Exception e) {
//                throw new ActionFailedException(e.toString());
//            }
//        }
        
        // Make sure all operations have finsished
        waitForIdle();
    }

    /** Determine whether a given path exists, expanding ancestor nodes as
     * necessary to find it.
     * @return Whether the given path on the given tree exists. 
     */
    public boolean assertPathExists(Component tree, TreePath path) {
        return assertPathExists(tree, path, false);
    }

    /** Determine whether a given path exists, expanding ancestor nodes as
     * necessary to find it.
     * @param invert Whether it invert the sense of this operation, when inverted
     *   it assumed a timeout of zero.
     * @return Whether the given path on the given tree exists. 
     */
    public boolean assertPathExists(Component tree, TreePath path, boolean invert) {
        return assertPathExists(tree, path, invert ? 0 : componentDelay, invert);
    }

    /** Determine whether a given path exists, expanding ancestor nodes as
     * necessary to find it.
     * @param timeout How long to wait for the tree path to become avaliable
     * @param invert Whether it invert the sense of this operation
     * @return Whether the given path on the given tree exists. 
     */
    public boolean assertPathExists(Component tree, TreePath path, int timeout, boolean invert) {
        try {
            makeVisible(tree, path, timeout, false);
            return !invert;
        }
        catch(LocationUnavailableException e) {
            return invert;
        }
    }

    /** Parse the String representation of a JTreeLocation into the actual
        JTreeLocation object.
    */
    public ComponentLocation parseLocation(String encoded) {
        return new JTreeLocation().parse(encoded);
    }

    /** Convert the coordinate into a more meaningful location.  Namely, use a
     * path, row, or coordinate.
     */
    public ComponentLocation getLocation(final Component c, final Point p) {
       return Robot.callAndWait(c, new Callable<JTreeLocation>()
       {
            @Override
            public JTreeLocation call() {
                JTree tree = (JTree)c;
                if (tree.getRowCount() == 0)
                    return new JTreeLocation(p);
                Rectangle rect = tree.getRowBounds(tree.getRowCount()-1);
                int maxY = rect.y + rect.height;
                if (p.y > maxY)
                    return new JTreeLocation(p);

                // TODO: ignore clicks to the left of the expansion control, or maybe
                // embed them in the location.
                TreePath path = tree.getClosestPathForLocation(p.x, p.y);
                TreePath stringPath = pathToStringPath(tree, path);
                if (stringPath != null) {
                    // if the root is hidden, drop it from the path
                    if (!tree.isRootVisible()) {
                        Object[] objs = stringPath.getPath();
                        Object[] subs = new Object[objs.length-1];
                        System.arraycopy(objs, 1, subs, 0, subs.length);
                        stringPath = new TreePath(subs);
                    }
                    return new JTreeLocation(stringPath);
                }
                int row = tree.getClosestRowForLocation(p.x, p.y);
                if (row != -1) {
                    return new JTreeLocation(row);
                }
                return new JTreeLocation(p);
            }
        });
    }

}
