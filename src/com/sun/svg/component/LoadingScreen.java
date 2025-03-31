package com.sun.svg.component;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Image;

/**
 * A simple loading screen component that can be used when SVG loading fails.
 * Displays a spinning animation and loading text.
 */
public class LoadingScreen extends Canvas implements Runnable {
    private static final int COLOR_BACKGROUND = 0xF0F0F0;
    private static final int COLOR_TEXT = 0x2980B9;
    private static final int COLOR_SPINNER = 0x3498DB;
    
    private String title;
    private String loadingText;
    private boolean animating;
    private Thread animationThread;
    private int spinnerPosition = 0;
    private int maxSpinnerPositions = 8;
    
    private Image loadingImage;
    
    /**
     * Creates a loading screen with default text
     */
    public LoadingScreen() {
        this("Hack MIDlet Store", "Loading...");
    }
    
    /**
     * Creates a loading screen with specified title and loading text
     */
    public LoadingScreen(String title, String loadingText) {
        this.title = title;
        this.loadingText = loadingText;
        
        try {
            // Try to load a custom loading image
            loadingImage = Image.createImage("/loading_icon.png");
        } catch (Exception e) {
            // Will use drawn spinner if image fails to load
            loadingImage = null;
        }
        
        // Start animation
        startAnimation();
    }
    
    /**
     * Starts the loading animation
     */
    public void startAnimation() {
        if (!animating) {
            animating = true;
            animationThread = new Thread(this);
            animationThread.start();
        }
    }
    
    /**
     * Stops the loading animation
     */
    public void stopAnimation() {
        animating = false;
        if (animationThread != null) {
            animationThread = null;
        }
    }
    
    /**
     * Draws the loading screen
     */
    protected void paint(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        
        // Background
        g.setColor(COLOR_BACKGROUND);
        g.fillRect(0, 0, width, height);
        
        // Title
        g.setColor(COLOR_TEXT);
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE));
        g.drawString(title, width / 2, height / 3, Graphics.HCENTER | Graphics.BASELINE);
        
        // Loading text
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM));
        g.drawString(loadingText, width / 2, height / 2, Graphics.HCENTER | Graphics.BASELINE);
        
        // Draw spinner or loading image
        if (loadingImage != null) {
            // Draw and rotate the loading image
            g.drawImage(loadingImage, width / 2, height * 2 / 3, Graphics.HCENTER | Graphics.VCENTER);
        } else {
            // Draw a simple spinner animation
            drawSpinner(g, width / 2, height * 2 / 3, 20);
        }
    }
    
    /**
     * Draws a simple animated spinner
     */
    private void drawSpinner(Graphics g, int centerX, int centerY, int radius) {
        g.setColor(COLOR_SPINNER);
        
        // Draw spinner dots
        for (int i = 0; i < maxSpinnerPositions; i++) {
            int alpha = (i == spinnerPosition) ? 255 : 100 + (150 * i / maxSpinnerPositions);
            int dotRadius = (i == spinnerPosition) ? 5 : 3;
            
            // Calculate position based on angle
            double angle = 2 * Math.PI * i / maxSpinnerPositions;
            int x = centerX + (int)(Math.cos(angle) * radius);
            int y = centerY + (int)(Math.sin(angle) * radius);
            
            g.fillArc(x - dotRadius, y - dotRadius, dotRadius * 2, dotRadius * 2, 0, 360);
        }
    }
    
    /**
     * Animation thread
     */
    public void run() {
        try {
            while (animating) {
                // Update spinner position
                spinnerPosition = (spinnerPosition + 1) % maxSpinnerPositions;
                
                // Redraw
                repaint();
                
                // Wait for next frame
                Thread.sleep(150);
            }
        } catch (InterruptedException e) {
            // Thread interrupted, stop animating
            animating = false;
        }
    }
    
    /**
     * Prevents the canvas from handling key events
     */
    protected void keyPressed(int keyCode) {}
    protected void keyReleased(int keyCode) {}
}
