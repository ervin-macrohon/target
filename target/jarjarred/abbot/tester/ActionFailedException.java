package abbot.tester;

import java.awt.Frame;

import java.awt.KeyboardFocusManager;

import java.io.IOException;
import java.io.PrintStream;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

import javax.swing.FocusManager;

/** Indicates that a ComponentTester action failed to execute properly. */
public class ActionFailedException extends FailedException
{


   public ActionFailedException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public ActionFailedException(String msg) {
        super(msg);
    }


}
