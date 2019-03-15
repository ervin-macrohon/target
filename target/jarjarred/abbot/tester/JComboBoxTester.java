package abbot.tester;

import java.awt.Component;
import java.awt.Container;

import java.util.ArrayList;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;

import javax.swing.*;

import abbot.Log;

import abbot.i18n.Strings;

import abbot.util.ExtendedComparator;
import abbot.util.AWT;

import abbot.finder.*;
import abbot.finder.matchers.*;

import abbot.tester.Robot.ConditionEDTDecorator;

import abbot.util.Condition;

import java.util.concurrent.Callable;

import javax.accessibility.AccessibleSelection;

import javax.swing.plaf.ComboBoxUI;

/**
 * JComboBoxTester for testing JComboBox instances.
 *
 * <p>In the case where you need to create a tester for JComboBox subclasses that
 * use novel components in the popup, for example jtree, you can quickly subclass
 * this tester to deal with those cases without the need to create a seperate
 * Recorder. Simply override the selectItemInPopup, getTargetClass, getValueAsStringFromRenderer
 * and findPopupComponent(Container) to return relavent values for your subclass
 * of JComboBox.</p>
 */

public class JComboBoxTester extends JComponentTester {

    private class PopupNotFoundException extends ActionFailedException {
        public PopupNotFoundException(String m) {
            super(m);
        }
    }
    private JListTester listTester = new JListTester();

    /** Return an array of strings that represent the combo box list.
     * Note that the current selection might not be included, since it's
     * possible to have a custom (edited) entry there that is not included in
     * the default contents.
     */
    public String[] getContents(JComboBox cb) {
        ArrayList list = new ArrayList();
        for (int i = 0; i < cb.getItemCount(); i++) {
            list.add(cb.getItemAt(i).toString());
        }
        return (String[])list.toArray(new String[list.size()]);
    }

    public void actionSelectIndex(Component comp, final int index) {
        final JComboBox cb = (JComboBox)comp;

        Component popup = makePopupVisible(cb);

        //

        try {
            // Not all LAFs use a JList for the popup
            try {
                selectIndexInPopup(popup, index);
            } catch (ComponentNotShowingException cnse) {

                //                Log.warn("Problem selecting item in a combobox, going to retry then throw original exception");
                //
                //                popup = makePopupVisible(cb);
                //                try {
                //                    selectIndexInPopup(popup, index);
                //                }
                //                catch (ComponentNotShowingException cnse2) {
                throw cnse;
                //                }
            }
        } catch (PopupNotFoundException e) {
            invokeAndWait(new Runnable() {
                public void run() {
                    cb.setSelectedIndex(index);
                    if (cb.getUI().isPopupVisible(cb))
                        cb.getUI().setPopupVisible(cb, false);
                }
            });
        }

        // Wait for all the events to processed

        waitForIdle();
    }

    /**
     * @param cb The combobox we are working on.
     * @return The popup componet we have just made visible
     */
    private Component makePopupVisible(final JComboBox cb) {
        // activate it, if not already showing
        if (!cb.getUI().isPopupVisible(cb)) {
            // NOTE: if the index is out of range, the selected item will be
            // one end or the other of the list.
            if (cb.isEditable()) {
                // Location of popup button activator is LAF-dependent
                invokeAndWait(new Runnable() {
                    public void run() {
                        cb.getUI().setPopupVisible(cb, true);
                    }
                });
            } else {
                actionClick(cb);
            }
        }

        // Wait for idle after combo box show

        waitForIdle();

        // Wait for the popup component to be visible
        //

        final Component popup = findPopupComponent(cb);

        wait(new ConditionEDTDecorator(cb, new Condition() {
            public boolean test() {
                return isReadyForInput(popup);
            }

            public String toString() {
                return Strings.get("tester.JComboBox.popup_not_ready");
            }
        }), defaultDelay / 4); // Ramp up delay as still getting
        return popup;
    }

    /** Provide an override point to select the correct index in whatever index
        the recording has specified
     */
    protected void selectIndexInPopup(Component popup, int index) {
        listTester.actionSelectIndex(popup, index);
    }


    /** Find the component in the popup raised by this combo box, if
        the LAF actually uses one.
     */
    public Component findPopupComponent(JComboBox cb) {
        Component popup = AWT.findActivePopupMenu();
        if (popup == null) {
            long now = System.currentTimeMillis();
            while ((popup = AWT.findActivePopupMenu()) == null) {
                if (System.currentTimeMillis() - now > popupDelay)
                    throw new PopupNotFoundException(Strings.get("tester.JComboBox.popup_not_found"));
                sleep();
            }
        }

        final Container popupAsContainer = (Container)popup;
        Component comp = callAndWait(cb, new Callable<Component>() {

            @Override
            public Component call() throws Exception {
                return findPopupComponent((Container)popupAsContainer);
            }
        });


        if (comp == null)
            throw new PopupNotFoundException(Strings.get("tester.JComboBox.popup_not_found"));
        return comp;
    }


    /**
     * @param sb The combo box we are testing
     * @return The JList component used to display the combobox
     */
    public JComponent findPopupFromUI(JComboBox sb) {

        Accessible a = sb.getUI().getAccessibleChild(sb, 0);
        if (a != null && a instanceof javax.swing.plaf.basic.ComboPopup) {

            // get the popup list
            JList list = ((javax.swing.plaf.basic.ComboPopup)a).getList();
            return list;
        }


        return (JList)findPopupComponent(sb);
    }


    /** Find the correct popup component in the container */
    protected Component findPopupComponent(Container parent) {
        try {
            ComponentFinder finder = BasicFinder.getDefault();
            return finder.find(parent, new ClassMatcher(JList.class));
        } catch (ComponentSearchException e) {
            return null;
        }
    }

    /** If the value looks meaningful, return it, otherwise return null. */
    public String getValueAsString(JComboBox combo, JComponent popupComponent, Object item, int index) {
        Component c = combo.getRenderer().getListCellRendererComponent((JList)popupComponent, item, index, true, true);
        return convertRendererToString(c);
    }


    /** Allow the recorder to write out the correct class name.  */
    public Class getTargetClass() {
        return JComboBox.class;
    }


    public void actionSelectItem(Component comp, final String item) {

        final JComboBox cb = (JComboBox)comp;
        final JComponent popup = findPopupFromUI(cb);

        // Get rid of any previous popup
        invokeAndWait(comp, new Runnable() {
            @Override
            public void run() {
                if (cb.isPopupVisible()) {
                    cb.hidePopup();
                }
            }
        });

        // Need to be done in one block otherwise the selection might changed
        boolean alreadySelected = Robot.callAndWait(cb, new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final Object obj = cb.getSelectedItem();
                if ((obj == null && item == null) ||
                    (obj != null &&
                     ExtendedComparator.stringsMatch(item, getValueAsString(cb, popup, obj, cb.getSelectedIndex()))))
                    return true;

                return false;
            }
        });
        if (alreadySelected) {
            return;
        }


        final GetIndexForItem mo = new JComboBoxTester.GetIndexForItem(cb, popup, item);

        // Wait for make sure that the combo is populated
        wait(new ConditionEDTDecorator(cb, new Condition() {
            public boolean test() {
                return mo.call() > -1;
            }

            public String toString() {
                return Strings.get("tester.JComboBox.item_not_found", new Object[] { item, dumpList(cb) });
            }
        }), defaultDelay / 4); // Ramp up delay as still getting


        int matchedIndex = Robot.callAndWait(cb, mo);

        if (matchedIndex != -1) {
            actionSelectIndex(comp, matchedIndex);
            return;
        }

        // While actions are supposed to represent real user actions, it's
        // possible that the current environment does not match sufficiently,
        // so we need to throw an appropriate exception that can be used to
        // diagnose the problem.
        String contents = dumpList(cb);
        throw new ActionFailedException(Strings.get("tester.JComboBox.item_not_found", new Object[] {
                                                    item, contents }));
    }


    protected String dumpList(final JComboBox cb) {
        String mid = "[";
        StringBuffer contents = new StringBuffer();
        JComponent list = findPopupFromUI(cb);

        for (int i = 0; i < cb.getItemCount(); i++) {

            contents.append(mid);
            contents.append(getValueAsString(cb, list, cb.getItemAt(i), i));
            mid = ", ";
        }
        contents.append("]");
        return contents.toString();
    }

    /**
     * Temporary helper interface
     */
    private interface RenderToString {
        public String render(Object obj, int index);
    }

    /**
     * A method object to access the index for particular item, aid in polling
     */
    private class GetIndexForItem implements Callable<Integer> {
        private JComboBox _cb;
        private JComponent _list;
        private String _item;

        GetIndexForItem(JComboBox cb, JComponent list, String item) {
            this._cb = cb;
            this._list = list;
            this._item = item;
        }

        @Override
        public Integer call() {

            RenderToString correctRender = new RenderToString() {
                @Override
                public String render(Object obj, int index) {
                    return getValueAsString(_cb, _list, obj, index);
                }
            };
            int found = find(correctRender);

            // Previously JComboBoxTester use to default to using toString
            // so we are going to fall back where to using this so that
            // the test is some-what backwards'ly compatible
            if (found == -1) {
                found = find(new RenderToString() {

                    @Override
                    public String render(Object item, int index) {
                        String value = item.toString();
                        return value;
                    }
                });

                if (found != -1) {
                    Log.warn("JComboBoxTester falling back to legacy toString code for item matching " +
                             _item.toString() + " but should be " + correctRender.render(_item, found));
                }
            }

            return found;
        }


        private int find(RenderToString render) {

            int found = -1;

            for (int i = 0; i < this._cb.getItemCount(); i++) {
                Object obj = this._cb.getItemAt(i);
                String objString = render.render(obj, i);

                Log.debug("Comparing against '" + objString + "'");
                if ((obj == null && this._item == null) ||
                    (obj != null && ExtendedComparator.stringsMatch(this._item, objString))) {
                    found = i;
                    break;
                }
            }
            return found;
        }
    }
}
