package abbot.tester;

import abbot.InterruptedAbbotException;

import java.awt.Image;
import java.awt.image.*;
import java.io.*;
import javax.swing.ImageIcon;

import abbot.Log;


import java.net.URL;

import javax.imageio.ImageIO;

/**
   This code expects the availability of the com.sun.image.codec.jpeg
   extensions from the Sun JDK 1.3 or JRE.

   Original comparison code contributed by asmithmb.

   author: asmithmontebello@aol.com, twall
 */
public class ImageComparator implements java.util.Comparator {
    
    public static String IMAGE_SUFFIX;
    
    static {
        // TODO: figure out how to do PNG stuff under 1.3 (w/o ImageIO)
        try {
            Class.forName("javax.imageio.ImageIO");
            IMAGE_SUFFIX = ".png";
        }
        catch(ClassNotFoundException e) {
            IMAGE_SUFFIX = ".jpg";
        }
    }

    private static Image convertToImage(Object obj) throws IOException {
        if (obj instanceof String) {
            obj = new File((String)obj);
        }
        if (obj instanceof BufferedImage) {
            // Convert to file and back to avoid unexplained 
            // memory-only BufferedImage differences
            File tmp = File.createTempFile("ImageComparator", IMAGE_SUFFIX);
            tmp.deleteOnExit();
            writeImage(tmp, (BufferedImage)obj);
            obj = tmp;
        }
        if (obj instanceof File) {
            obj = new ImageIcon(((File)obj).toURI().toURL()).getImage();
        }
        if (obj instanceof URL) {
            obj = new ImageIcon((URL)obj).getImage();
        }
        if (obj instanceof Image) {
            return (Image)obj;
        }
        return null;
    }
    
    public static void writeImage(File file, BufferedImage img) 
        throws IOException {
        if (".png".equals(IMAGE_SUFFIX)) 
            writePNG(file, img);
        else
            writeJPEG(file, img);
    }
    
    public static void writePNG(File file, BufferedImage img) 
        throws IOException {
        javax.imageio.ImageIO.write(img, "png", file);
    }
    
    /** Write the given buffered image to disk. */
    public static void writeJPEG(File file, BufferedImage img)
        throws IOException {
        FileOutputStream os = new FileOutputStream(file);

        // Replace code with JDK clean code        
        ImageIO.write(img, "jpeg", os);
        
        os.close();
    }

    /**
       Compare two images.  May be BufferedImages or File arguments.
    */
    public int compare(Object obj1, Object obj2) {
        try {
            obj1 = convertToImage(obj1);
        }
        catch(IOException io) {
            throw new IllegalArgumentException("Object is not convertable to an Image: " + obj1);
        }
        try {
            obj2 = convertToImage(obj2);
        }
        catch(IOException io) {
            throw new IllegalArgumentException("Object is not convertable to an Image: " + obj2);
        }
        Log.debug("Comparing " + obj1 + " and " + obj2);
        Image image1 = (Image)obj1;
        int w = image1.getWidth(null);
        int h = image1.getHeight(null);
        Image image2 = (Image)obj2;
        int w2 = image2.getWidth(null);
        int h2 = image2.getHeight(null);
        if (w*h != w2*h2) {
            return w*h - w2*h2;
        }
        int[] pixels1 = new int[w*h];
        int[] pixels2 = new int[w*h];
        PixelGrabber pg1 = new PixelGrabber(image1, 0, 0, w, h, pixels1, 0, w);
        PixelGrabber pg2 = new PixelGrabber(image2, 0, 0, w, h, pixels2, 0, w);
        try {
            pg1.grabPixels();
            pg2.grabPixels();
            for (int i=0;i < w*h;i++) {
                if (pixels1[i] != pixels2[i]) {
                    return pixels1[i] - pixels2[i];
                }
            }
        }
        catch(InterruptedException e) { 
           throw new InterruptedAbbotException("Interrupted when comparing images");
        }
        return 0;
    }

    /** Comparators are equal if they're the same class. */
    public boolean equals(Object obj) {
        return obj == this
            || (obj != null && obj.getClass().equals(getClass()));
    }
}
