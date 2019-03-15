package abbot.tester;

import abbot.WaitTimedOutException;

import abbot.i18n.Strings;

import java.awt.*;

import javax.swing.JTable;
import abbot.script.ArgumentParser;

import abbot.util.Condition;

import java.util.concurrent.Callable;

/** Provide user actions on a JTable.
    The JTable substructure is a "Cell", and JTableLocation provides different
    identifiers for a cell.
    <ul>
    <li>Select a cell by row, column index
    <li>Select a cell by value (its string representation)
    </ul>

    @see abbot.tester.JTableLocation
 */
// TODO: multi-select
public class JTableTester extends JComponentTester {

    /** Convert the value in the list at the given index into a reasonable
        string representation, or null if one can not be obtained.
    */
    public static String valueToString(final JTable table, final int row, final int col) {
        
        
        // Ensure this work is done on the EDT, TODO move this
        // code up to Robot if we are sure we need to reuse it
        // 

        return callAndWait(table,new Callable<String>() {
            public String call() {
                Object value = table.getValueAt(row, col);
                Component cr = table.getCellRenderer(row, col).
                    getTableCellRendererComponent(table, value, false, false,
                                                  row, col);
                String string = convertRendererToString(cr);
                return string;
            }
        });
        
        
    }

    /** Select the given cell, if not already. */
    public void actionSelectCell(Component c, final JTableLocation loc) {
        actionSelectCell(c,loc,componentDelay);
    }

    /** Select the given cell, if not already. */
    public void actionSelectCell(Component c, final JTableLocation loc, int timeout) {
        final JTable table = (JTable)c; 
        
        final JTableLocation.Cell found[] = new JTableLocation.Cell[1];
        try {
            wait(new Robot.ConditionEDTDecorator(table, new Condition()
                {
                    public boolean test() {
                        try{
                            found[0] = loc.getCell(table);
                            return found[0]!=null;
                        }
                        catch (LocationUnavailableException lue) {
                            return false;
                        }
                    }
                    public String toString() {
                        return Strings.get("tester.Component.show_wait",
                                           new Object[] { loc.toString() });
                    }
                }), timeout);
        } catch(WaitTimedOutException e) {
            throw new LocationUnavailableException(e.getMessage());
        }
              
        // If we have found something select it
        //
        
        JTableLocation.Cell cell = found[0];
        if (table.isRowSelected(cell.row)
            && table.isColumnSelected(cell.col)
            && table.getSelectedRowCount() == 1) {
            return;
        }
        actionClick(c, loc);
    }

    /**
     * @param c The table to test
     * @param loc The location to assert exists
     */
    public boolean assertCellExists(Component c, final JTableLocation loc) {
        return assertCellExists(c, loc, componentDelay);
    }

    /**
     * @param c The table to test
     * @param loc The location to assert
     * @param timeout The timeout to use, tends to be low in negative cases
     */
    public boolean assertCellExists(Component c, final JTableLocation loc, int timeout) {
       final JTable table = (JTable)c; 
       
       final JTableLocation.Cell found[] = new JTableLocation.Cell[1];
       try {
           wait(new Robot.ConditionEDTDecorator(table, new Condition()
               {
                   public boolean test() {
                       try{
                           found[0] = loc.getCell(table);
                           return found[0]!=null;
                       }
                       catch (LocationUnavailableException lue) {
                           return false;
                       }
                   }
                   public String toString() {
                       return Strings.get("tester.Component.show_wait",
                                          new Object[] { loc.toString() });
                   }
               }), timeout);
           return true;
       } catch(WaitTimedOutException e) {
           return false;
       }
    }


    /** Select the given cell, if not already.
        Equivalent to actionSelectCell(c, new JTableLocation(row, col)).
     */
    public void actionSelectCell(Component c, int row, int col) {
        actionSelectCell(c, new JTableLocation(row, col));
    }

    /** Parse the String representation of a JTableLocation into the actual
        JTableLocation object.
    */
    public ComponentLocation parseLocation(String encoded) {
        return new JTableLocation().parse(encoded);
    }

    /** Return (in order of preference) the location corresponding to value,
     * cell, or coordinate.
     */
    public ComponentLocation getLocation(Component c, Point p) {
        JTable table = (JTable)c;
        int row = table.rowAtPoint(p);
        int col = table.columnAtPoint(p);
        if (row != -1 && col != -1) {
            String value = valueToString(table, row, col);
            if (value != null) {
                return new JTableLocation(value);
            }
            return new JTableLocation(row, col);
        }
        return new JTableLocation(p);
    }
}
