/*
 * Copyright (c) 2006 Timothy Wall, All Rights Reserved
 */
package abbot.util;

import java.io.PrintStream;

/** Splits a {@link PrintStream} into two targets.  Useful for making
 * a copy of stream go somewhere else, such as redirecting stdout/stderr
 * to a file.
 */
public class Tee extends PrintStream {
    private PrintStream tee;
    private PrintStream tee2;
    private boolean splitting;
    public Tee(PrintStream p1, PrintStream p2) {
        super(p1);
        tee2 = p1;
        tee = p2;
    }
    public synchronized void write(int b) {
        if (splitting) {
            super.write(b);
        }
        else {
            splitting = true;
            super.write(b);
            tee.write(b);
        }
    }
    public synchronized void write(byte[] b, int o, int l) {
        if (splitting) {
            super.write(b, o, l);
        }
        else {
            splitting = true;
            super.write(b, o, l);
            tee.write(b, o, l);
            splitting = false;
        }
    }
    public synchronized void flush() {
        super.flush();
        tee.flush();
    }
    public synchronized void close() {
        super.close();
        tee.close();
    }
    public synchronized void print(boolean b) {
        if (splitting) {
            super.print(b);
        }
        else {
            splitting = true;
            super.print(b);
            tee.print(b);
            splitting = false;
        }
    }
    public synchronized void print(char b) {
        if (splitting) {
            super.print(b);
        }
        else {
            splitting = true;
            super.print(b);
            tee.print(b);
            splitting = false;
        }
    }
    public synchronized void print(int b) {
        if (splitting) {
            super.print(b);
        }
        else {
            splitting = true;
            super.print(b);
            tee.print(b);
            splitting = false;
        }
    }
    public synchronized void print(long b) {
        if (splitting) {
            super.print(b);
        }
        else {
            splitting = true;
            super.print(b);
            tee.print(b);
            splitting = false;
        }
    }
    public synchronized void print(float b) {
        if (splitting) {
            super.print(b);
        }
        else {
            splitting = true;
            super.print(b);
            tee.print(b);
            splitting = false;
        }
    }
    public synchronized void print(double b) {
        if (splitting) {
            super.print(b);
        }
        else {
            splitting = true;
            super.print(b);
            tee.print(b);
            splitting = false;
        }
    }
    public synchronized void print(char[] b) {
        if (splitting) {
            super.print(b);
        }
        else {
            splitting = true;
            super.print(b);
            tee.print(b);
            splitting = false;
        }
    }
    public synchronized void print(String b) {
        if (splitting) {
            super.print(b);
        }
        else {
            splitting = true;
            super.print(b);
            tee.print(b);
            splitting = false;
        }
    }
    public synchronized void print(Object b) {
        if (splitting) {
            super.print(b);
        }
        else {
            splitting = true;
            super.print(b);
            tee.print(b);
            splitting = false;
        }
    }
    public synchronized void println() {
        if (splitting) {
            super.println();
        }
        else {
            splitting = true;
            super.println();
            tee.println();
            splitting = false;
        }
    }
    public synchronized void println(boolean x) {
        if (splitting) {
            super.println(x);
        }
        else {
            splitting = true;
            super.println(x);
            tee.println(x);
            splitting = false;
        }
    }
    public synchronized void println(char x) {
        if (splitting) {
            super.println(x);
        }
        else {
            splitting = true;
            super.println(x);
            tee.println(x);
            splitting = false;
        }
    }
    public synchronized void println(int x) {
        if (splitting) {
            super.println(x);
        }
        else {
            splitting = true;
            super.println(x);
            tee.println(x);
            splitting = false;
        }
    }
    public synchronized void println(long x) {
        if (splitting) {
            super.println(x);
        }
        else {
            splitting = true;
            super.println(x);
            tee.println(x);
            splitting = false;
        }
    }
    public synchronized void println(float x) {
        if (splitting) {
            super.println(x);
        }
        else {
            splitting = true;
            super.println(x);
            tee.println(x);
            splitting = false;
        }
    }
    public synchronized void println(double x) {
        if (splitting) {
            super.println(x);
        }
        else {
            splitting = true;
            super.println(x);
            tee.println(x);
            splitting = false;
        }
    }
    public synchronized void println(byte[] x) {
        if (splitting) {
            super.println(x);
        }
        else {
            splitting = true;
            super.println(x);
            tee.println(x);
            splitting = false;
        }
    }
    public synchronized void println(String x) {
        if (splitting) {
            super.println(x);
        }
        else {
            splitting = true;
            super.println(x);
            tee.println(x);
            splitting = false;
        }
    }
    public synchronized void println(Object x) {
        if (splitting) {
            super.println(x);
        }
        else {
            splitting = true;
            super.println(x);
            tee.println(x);
            splitting = false;
        }
    }
    public String toString() {
        return "Tee " + tee2 + " and " + tee;
    }
}
