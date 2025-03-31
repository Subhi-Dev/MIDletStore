package com.sun.svg.util;

import javax.microedition.m2g.SVGImage;
import javax.microedition.m2g.SVGAnimator;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m2g.ScalableGraphics;

/**
 * A default implementation of SVGAnimator for displaying SVG content.
 * This class creates a Canvas that renders an SVG image and handles animations.
 */
public class DefaultSVGAnimator {
    /**
     * Creates an SVGAnimator for the given SVGImage.
     * 
     * @param svgImage The SVG image to animate
     * @return An SVGAnimator instance
     */
    public static SVGAnimator createAnimator(SVGImage svgImage) {
        SVGAnimatorImpl animator = new SVGAnimatorImpl(svgImage);
        return animator;
    }
    
    /**
     * Implementation of SVGAnimator that wraps our custom canvas
     */
    private static class SVGAnimatorImpl extends javax.microedition.m2g.SVGAnimator {
        private SVGAnimatorCanvas canvas;
        private boolean started = false;
        
        public SVGAnimatorImpl(SVGImage svgImage) {
            canvas = new SVGAnimatorCanvas(svgImage);
        }
        
        public Object getTargetComponent() {
            return canvas;
        }
        
        public void play() {
            started = true;
            canvas.startAnimating();
        }
        
        public void stop() {
            started = false;
            canvas.stopAnimating();
        }
        
        public void pause() {
            // Implement pause functionality
            if (started) {
                canvas.stopAnimating();
                // We don't set started = false to remember that it was started
            }
        }
        
        public void setTimeMillis(long ms) {
            // Not implementing detailed time control for this example
        }
        
        public void invokeLater(Runnable runnable) {
            // Schedule the runnable to be executed later
            new Thread(runnable).start();
        }
        
        public void invokeAndWait(Runnable runnable) {
            // Execute the runnable and wait for it to complete
            try {
                Thread thread = new Thread(runnable);
                thread.start();
                thread.join();
            } catch (InterruptedException e) {
                // Handle interruption
            }
        }
        
        public float getTimeIncrement() {
            // Return a default time increment (in milliseconds)
            // This determines how much time to advance during each animation step
            return 50.0f; // 50ms matches our animation frame rate
        }
        
        public void setTimeIncrement(float increment) {
            // Implementation for setting the time increment
            // This method is required to override the abstract method in SVGAnimator
        }
        
        public void setSVGEventListener(javax.microedition.m2g.SVGEventListener listener) {
            // Implementation for setting the SVG event listener
            // This method is required to override the abstract method in SVGAnimator
        }
    }
    
    /**
     * Canvas that displays an SVG image with animation support
     */
    private static class SVGAnimatorCanvas extends Canvas implements Runnable {
        private SVGImage svgImage;
        private ScalableGraphics sg;
        private boolean animating = false;
        private Thread animationThread;
        private int width, height;
        
        public SVGAnimatorCanvas(SVGImage svgImage) {
            this.svgImage = svgImage;
            this.sg = ScalableGraphics.createInstance();
            
            // Start animating by default
            startAnimating();
        }
        
        public void startAnimating() {
            if (!animating) {
                animating = true;
                animationThread = new Thread(this);
                animationThread.start();
            }
        }
        
        public void stopAnimating() {
            animating = false;
            if (animationThread != null) {
                animationThread = null;
            }
        }
        
        protected void paint(Graphics g) {
            width = getWidth();
            height = getHeight();
            
            // Clear background (optional - SVG may have its own background)
            g.setColor(0xFFFFFF);
            g.fillRect(0, 0, width, height);
            
            // Draw SVG
            sg.bindTarget(g);
            svgImage.setViewportWidth(width);
            svgImage.setViewportHeight(height);
            sg.render(0, 0, svgImage);
            sg.releaseTarget();
        }
        
        public void run() {
            try {
                while (animating) {
                    repaint();
                    Thread.sleep(50); // 20 FPS animation rate
                }
            } catch (InterruptedException e) {
                // Thread interrupted, stop animating
                animating = false;
            }
        }
        
        // Prevent canvas from stealing focus with keys
        protected void keyPressed(int keyCode) {}
        protected void keyReleased(int keyCode) {}
    }
}
