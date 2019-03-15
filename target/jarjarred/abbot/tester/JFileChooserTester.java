package abbot.tester;

import java.awt.*;
import java.io.File;
import javax.swing.*;

import abbot.finder.*;
import abbot.finder.matchers.*;
import abbot.i18n.Strings;

/**
 * Tester for the {@link JFileChooser}.  
 * Note: only a small subset of JFileChooser functionality is exposed here.
 * @author twall@users.sf.net
 */
public class JFileChooserTester extends JComponentTester {

    private JTextComponentTester tester = new JTextComponentTester();
    private ComponentFinder finder = BasicFinder.getDefault();
    private Component find(Container chooser, Matcher m) {
        try {
            return finder.find(chooser, m);
        }
        catch(ComponentSearchException e) {
            return null;
        }
    }

    private JButton findButton(Container chooser, final String text) {
        JButton button = (JButton)find(chooser, new ClassMatcher(JButton.class) {
            public boolean matches(Component c) {
                return super.matches(c)
                    && text.equals(((JButton)c).getText());
            }
        });
        return button;
    }

    /** Sets the selected file. */
    public void actionSetSelectedFile(Component c, final File file) {
        final JFileChooser chooser = (JFileChooser)c;
        int mode = chooser.getFileSelectionMode();
        if (mode == JFileChooser.FILES_ONLY && file.isDirectory()) {
            String msg = Strings.get("tester.JFileChooser.files_only");
            throw new ActionFailedException(msg); 
        }
        if (mode == JFileChooser.DIRECTORIES_ONLY && !file.isDirectory()) {
            String msg = Strings.get("tester.JFileChooser.dirs_only");
            throw new ActionFailedException(msg);
        }
        invokeAndWait(new Runnable() { public void run() {
            chooser.setSelectedFile(file);
        }});
    }


    /** Sets the text in the file name/path text field.  Note that the 
     * {@link JFileChooser} may behave differently depending on what
     * you actually feed into that field.
     */
    public void actionSetFilename(Component c, String filename) {
        JTextField tf = (JTextField)
            find((JFileChooser)c, new ClassMatcher(JTextField.class));
        if (tf == null) {
            String msg = Strings.get("tester.JFileChooser.filename_not_found");
            throw new ActionFailedException(msg);
        }
        tester.actionEnterText(tf, filename);
    }

    /** Sets the current directory from which a file or directory may
     * be selected.  This is not the same thing as setting the 
     * <em>selected</em> directory when in directory selection mode.
     */
    public void actionSetDirectory(Component c, final String path) {
        final JFileChooser chooser = (JFileChooser)c;
        invokeAndWait(new Runnable() { public void run() {
            chooser.setCurrentDirectory(new File(path));
        }});
        waitForIdle();
    }

    /** Press the approve button.  Fails if the button is disabled.
     */
    public void actionApprove(Component c) {
        // Could invoke chooser.approveSelection, but that doesn't actually
        // fire the approve button.
        JFileChooser chooser = (JFileChooser)c;
        String text = chooser.getApproveButtonText();
        if (text == null) {
            text = chooser.getUI().getApproveButtonText(chooser);
        }
        JButton approve = findButton(chooser, text);
        if (approve == null) {
            String msg = Strings.get("tester.JFileChooser.approve_not_found");
            throw new ActionFailedException(msg);
        }
        if (!approve.isEnabled()) {
            String msg = Strings.get("tester.JFileChooser.approve_not_enabled");
            throw new ActionFailedException(msg);
        }
        actionClick(approve);
    }

    /** Press the cancel button.  Fails if the button is disabled. */
    public void actionCancel(Component c) {
        // We could invoke chooser.cancelSelection, but that wouldn't actually
        // fire the cancel button...
        JFileChooser chooser = (JFileChooser)c;
        JButton cancel = findButton(chooser, UIManager.getString("FileChooser.cancelButtonText"));
        if (cancel == null) {
            String msg = Strings.get("tester.JFileChooser.cancel_not_found");
            throw new ActionFailedException(msg);
        }
        if (!cancel.isEnabled()) {
            String msg = Strings.get("tester.JFileChooser.cancel_not_enabled");
            throw new ActionFailedException(msg);
        }
        actionClick(cancel);
    }
}
