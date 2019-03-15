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

/** Indicates that a Robot.callAndWait has failed to execute properly. */
public class CallTimoutFailedException extends FailedException
{


   public CallTimoutFailedException(String msg, Throwable cause) {
        super(msg, cause);
   }

}
