package abbot.tester;

import java.awt.*;

import javax.swing.*;

import abbot.Log;
import abbot.WaitTimedOutException;

import abbot.i18n.Strings;
import abbot.script.ArgumentParser;

import abbot.tester.Robot.ConditionEDTDecorator;

import abbot.util.Condition;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/** Provide actions and assertions for a {@link JList} component.
    The {@link JList} substructure is a "row", and {@link JListLocation}
    provides different identifiers for a row.
    <ul>
    <li>Select an item by index
    <li>Select an item by value (its string representation)
    </ul>
    Note that {@link JList} uses "index" and "value" in its API.  For
    convenience, the <code>JListTester</code> API also provides "row" and
    "item" as synonyms for "index".

    @see JListLocation
 */
// TODO multi-select

public class JListTester extends JComponentTester {

    /** Convert the value in the list at the given index into a reasonable
        string representation, or null if one can not be obtained.
     */
    public static String valueToString(final JList list, final int index) {

        // Ensure this work is done on the EDT, TODO move this
        // code up to Robot if we are sure we need to reuse it
        //

        Callable renderedToString = new Callable() {
            public Object call() {
                Object value = list.getModel().getElementAt(index);
                Component cr =
                    list.getCellRenderer().getListCellRendererComponent(list,
                                                                        value,
                                                                        index,
                                                                        false,
                                                                        false);
                String string = convertRendererToString(cr);
                return string;
            }
        };


        try {
            if (EventQueue.isDispatchThread()) {
                return (String)renderedToString.call();
            }
            else {
                
                // TODO make sure we use the correct event queue, this method cannot be static
                //
                
                FutureTask ft = new FutureTask(renderedToString);
                EventQueue.invokeLater(ft);
                return (String)ft.get();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failure to query string value", e);
        }
    }

    /** JList doesn't provide direct access to its contents, so make up for
     * that oversight.
     */
    public Object getElementAt(JList list, int index) {
        return list.getModel().getElementAt(index);
    }

    /** Return the size of the given list. */
    public int getSize(JList list) {
        return list.getModel().getSize();
    }

    /** Return an array of strings that represents the list's contents. */
    public String[] getContents(JList list) {
        ListModel model = list.getModel();
        String[] values = new String[model.getSize()];
        for (int i=0;i < values.length;i++) {
            values[i] = model.getElementAt(i).toString();
        }
        return values;
    }

    /** Select the given index.
        Equivalent to actionSelectRow(c, new JListLocation(index), delay).
    */
    public void actionSelectIndex(Component c, int index, long delay) {
        actionSelectRow(c, new JListLocation(index), delay);
    }

    /** Select the given index.
        Equivalent to actionSelectRow(c, new JListLocation(index)).
    */
    public void actionSelectIndex(Component c, int index) {
        actionSelectRow(c, new JListLocation(index));
    }

    /** Select the first item in the list matching the given String
        representation of the item.<p>
        Equivalent to actionSelectRow(c, new JListLocation(item), delay).
    */
    public void actionSelectItem(Component c, String item,long delay) {
        actionSelectRow(c, new JListLocation(item), delay);
    }

    /** Select the first item in the list matching the given String
        representation of the item.<p>
        Equivalent to actionSelectRow(c, new JListLocation(item)).
    */
    public void actionSelectItem(Component c, String item) {
        actionSelectRow(c, new JListLocation(item));
    }

    /** Select the first value in the list matching the given String
        representation of the value.<p>
        Equivalent to actionSelectRow(c, new JListLocation(value)).
    */
    public void actionSelectValue(Component c, String value) {
        actionSelectRow(c, new JListLocation(value));
    }

    /** Select the given row.  Does nothing if the index is already
     * selected.
     */
    public void actionSelectRow(Component c, final JListLocation location) {
        actionSelectRow(c,location, componentDelay);
    }
    
    /** Select the given row.  Does nothing if the index is already
     * selected.
     */
    public void actionSelectRow(Component c, final JListLocation location, long delay) {
        final JList list = (JList)c;

        // Wait for the selected location to become avaliable
        //

        wait(new ConditionEDTDecorator(c,new Condition() {
                    public boolean test() {
                        int index = location.getIndex(list);
                        return index >= 0 && index < list.getModel().getSize();
                    }

                    public String toString() {
                        return  Strings.get("tester.JList.item_not_found", 
                               new Object[] { location.toString(), JListTester.dumpList(list) });
                    }
                }), delay);

        // Select the location
        // 

        final int targetIndex = location.getIndex(list);
        if (targetIndex < 0 || targetIndex >= list.getModel().getSize()) {
            String msg = Strings.get("tester.JList.invalid_index",
                                     new Object[] { new Integer(targetIndex) });
            throw new ActionFailedException(msg);
        }
        if (list.getSelectedIndex() != targetIndex) {
            Log.debug("Click on index=" + targetIndex);
            
            final TemporarySelectionListener tsl = new TemporarySelectionListener(targetIndex);

            invokeAndWait(list, new Runnable()
            {
                @Override
                public void run() {
                    list.addListSelectionListener(tsl);
                }
            });

            try {            
                super.actionClick(c, location);

                // Wait for the selection to have been made, other stuff might happen
                // after that so all we can say for sure is that for one moment that
                // the list held that selection, so polling the selected value might
                // not be enough as the model might be altered by the time we get
                // to look at it
                //

                final Robot.ConditionEDTDecorator condition = new Robot.ConditionEDTDecorator(c, new Condition() {
                    public boolean test() {
                        return tsl.matched;
                    }
    
                    public String toString() {
                        return Strings.get("tester.JList.item_not_selected", new Object[] { location.toString(), JListTester.dumpList(list),
                        list.getSelectedIndex() });
                    }
                });
                try {
                    wait(condition, delay/4);
                }
                catch (WaitTimedOutException we) {
                    
                    // Now we have a solution for the mouse click issue...
                    
//                    Log.warn("Problem selecting item in a list, going to retry then throw original exception");
//                    
//                    // We are seeing some intermitttent failures since JDK6u35 so if the selection 
//                    // didn't work try again
//                    super.actionClick(c, location);
//                    try {
//                        wait(condition,delay/4);
//                    }
//                    catch (WaitTimedOutException we2) {
//    
//                        Log.warn("Problem selecting item in a list, going to retry for a second time then throw original exception");
//                        
//                        // We are seeing some intermitttent failures since JDK6u35 so if the selection 
//                        // didn't work try again
//                        super.actionClick(c, location);
//                        try {
//                            wait(condition,delay/4);
//                        }
//                        catch (WaitTimedOutException we3) {
                            throw we;
//                        }
//                    }
                }
                
            }
            finally {

                // Make sure we remove the temporary listener
                invokeAndWait(list, new Runnable()
                {
                    @Override
                    public void run() {
                        list.removeListSelectionListener(tsl);
                    }
                });
            }
            
            
        }
    }

    /** Parse the String representation of a JListLocation into the actual
        JListLocation object.
    */
    public ComponentLocation parseLocation(String encoded) {
        return new JListLocation().parse(encoded);
    }

    /** Return the value, row, or coordinate location. */
    public ComponentLocation getLocation(Component c, Point p) {
        JList list = (JList)c;
        int index = list.locationToIndex(p);
        String value = valueToString(list, index);
        if (value != null) {
            return new JListLocation(value);
        }
        else if (index != -1) {
            return new JListLocation(index);
        }
        return new JListLocation(p);
    } 
    
    
    public static String dumpList(final JList list) {
        String mid = "[";
        StringBuffer contents = new StringBuffer();
        ListModel lm = list.getModel();

        for (int i=0;i < lm.getSize();i++) {

            contents.append(mid);
            contents.append(
                valueToString(list, i));
            mid = ", ";
        }
        contents.append("]");
        return contents.toString();
    }



    private static class TemporarySelectionListener implements ListSelectionListener {
        
        private volatile boolean matched = false;
        private int targetIndex;
            
        public TemporarySelectionListener(int targetIndex) {
            this.targetIndex = targetIndex;
        }
        
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (e.getFirstIndex() <= targetIndex && targetIndex <= e.getLastIndex()) {
                matched = true;
            }
        }
    }
    
}
