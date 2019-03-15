// Initialization script for bsh

import abbot.script.ComponentReference;
import abbot.finder.matchers.*;
import abbot.finder.AWTHierarchy;
import abbot.AssertionFailedError;
import java.awt.Component;
import java.io.File;
import java.io.FileReader;

// Allow assert methods to be used without any qualifiers
import static junit.framework.Assert.*;

Component component(referenceID) {
    return reference(referenceID).getComponent();
}

ComponentReference reference(referenceID) {
    return resolver.getComponentReference(referenceID);
}

Component find(matcher) {
    finder.find(matcher);
}

Component find(root, matcher) {
    finder.find(root, matcher);
}

Object source(path) {
    sourcePath = new File(script.getFile().getParentFile(), path);
    engine.eval(new FileReader(sourcePath)); 
}

