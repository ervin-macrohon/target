package abbot.tester;

import java.awt.*;

import javax.swing.JList;

import abbot.i18n.Strings;
import abbot.util.ExtendedComparator;

/** Provides encapsulation of the location of a row on a JList (a coordinate,
 * item index or value). 
 */

public class JListLocation extends ComponentLocation {
    private Object value;
    private int row = -1;

    /** The center of the target list. */
    public JListLocation() { }

    /** Specify location by explicit reference to an object. */
    public JListLocation(Object value) {
        this.value = value;
    }

    /** Specify location by string representation of an object. 
     * @see JListTester#valueToString 
     */
    public JListLocation(String value) {
        this((Object)value);
    }

    /** Specify location by row in the list. */
    public JListLocation(int row) {
        if (row < 0) {
            String msg = Strings.get("tester.JList.invalid_index",
                                     new Object[] { new Integer(row) });
            throw new LocationUnavailableException(msg);
        }
        this.row = row;
    }

    /** Specify a location by component-relative coordinate. */
    public JListLocation(Point where) {
        super(where);
    }

    protected String badFormat(String encoded) {
        return Strings.get("location.list.bad_format",
                           new Object[] { encoded });
    }

    /** Convert the given index into a coordinate. */
    protected Point indexToPoint(JList list, int index) {
        if (index < 0 || index >= list.getModel().getSize()) {
            String msg = Strings.get("tester.JList.invalid_index", 
                                     new Object[] { new Integer(index) });
            throw new LocationUnavailableException(msg);
        }
        Rectangle rect = list.getCellBounds(index, index);
        return new Point(rect.x + rect.width/2, rect.y + rect.height/2);
    }

    /** Find the first String match in the list and return the index. */
    private int valueToIndex(JList list, Object value) {
        int size = list.getModel().getSize();
        if (value instanceof String) {
            for (int i=0;i < size;i++) {
                String str = JListTester.valueToString(list, i);
                if (ExtendedComparator.stringsMatch((String)value, str)) {
                    return i;
                }
            }
        }
        else {
            for (int i=0;i < size;i++) {
                Object el = list.getModel().getElementAt(i);
                if (el == null && value == null
                    || el != null && el.equals(value)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public int getIndex(JList list) {
        if (value != null)
            return valueToIndex(list, value);
        if (row != -1) {
            return row;
        }
        return list.locationToIndex(super.getPoint(list));
    }

    /** Return a concrete point for the abstract location. */
    public Point getPoint(Component c) {
        JList list = (JList)c;
        if (value != null || row != -1) {
            int idx = getIndex(list);
            if (idx == -1) {
                throw new LocationUnavailableException(invalidMessage(list));
            }
            return indexToPoint(list, idx);
        }
        return super.getPoint(list);
    }

    private String invalidMessage(JList list) {
        if (value != null) {
            return Strings.get("tester.JList.item_not_found", 
                               new Object[] { value, JListTester.dumpList(list) });
        }
        if (row != -1) {
            return Strings.get("tester.JList.invalid_index", 
                               new Object[] { new Integer(row) });
        }
        return Strings.get("tester.JList.point_not_found", 
                           new Object[] { super.getPoint(list) });
    }

    public Rectangle getBounds(Component c) {
        JList list = (JList)c;
        int index = getIndex(list);
        if (index == -1) {
            throw new LocationUnavailableException(invalidMessage(list));
        }
        return list.getCellBounds(index, index);
    }

    public boolean equals(Object o) {
        if (o instanceof JListLocation) {
            JListLocation loc = (JListLocation)o;
            if (value != null)
                return value.equals(loc.value);
            if (row != -1)
                return row == loc.row;
        }
        return super.equals(o);
    }

    public String toString() {
        if (value != null)
            return encodeValue(value.toString());
        if (row != -1)
            return encodeIndex(row);
        return super.toString();
    }

    public ComponentLocation parse(String encoded) {
        encoded = encoded.trim();
        if (isValue(encoded)) {
            value = parseValue(encoded);
            return this;
        }
        if (isIndex(encoded)) {
            row = parseIndex(encoded);
            return this;
        }
        return super.parse(encoded);
    }
}
