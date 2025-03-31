import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * Canvas for the MIDlet Store application
 * Uses direct drawing rather than SVG DOM manipulation for better J2ME compatibility
 */
public class StoreCanvas extends Canvas {
    // Constants
    private static final int HEADER_HEIGHT = 30;
    private static final int CAROUSEL_HEIGHT = 100; // Increased height for more prominent display
    private static final int ITEM_WIDTH = 80;
    private static final int ITEM_HEIGHT = 80;
    private static final int ITEM_SPACING = 10;
    private static final int ITEMS_PER_ROW = 3;
    private static final int VISIBLE_ROWS = 3;
    
    // Animation-related constants
    private static final int ANIMATION_DURATION = 300; // milliseconds
    private static final int ANIMATION_STEPS = 10;
    
    // Colors
    private static final int COLOR_BACKGROUND = 0xF0F0F0;
    private static final int COLOR_HEADER = 0xEC3750;
    private static final int COLOR_CAROUSEL_BG = 0xFFC7CE;
    private static final int COLOR_TEXT = 0x2C3E50;
    private static final int COLOR_HIGHLIGHT = 0xE83F56;
    private static final int COLOR_WHITE = 0xFFFFFF;
    private static final int COLOR_GRAY = 0xAAAAAA;
    private static final int COLOR_UPVOTE = 0x27AE60;  // Green for upvotes
    private static final int COLOR_DOWNVOTE = 0xC0392B; // Red for downvotes
    
    // Layout variables
    private int width, height;
    private int gridStartY;
    
    // Data
    private MIDletStore midletStore;
    private Vector midletsList;
    private Vector featuredApps;
    private Vector appIcons;
    private MIDletStore.MidletInfo selectedMidlet;
    
    // State
    private int currentFeaturedIndex = 0;
    private int scrollPosition = 0;
    private int selectedIndex = 0;
    private boolean carouselMode = true;
    
    // Animation state variables
    private boolean isAnimating = false;
    private long animationStartTime = 0;
    private int animationStartPosition = 0;
    private int animationTargetPosition = 0;
    private int animationCurrentStep = 0;
    
    // Default font and images
    private Image defaultAppIcon;
    
    /**
     * Constructor
     */
    public StoreCanvas(MIDletStore midletStore, Vector midletsList, Vector featuredApps) {
        this.midletStore = midletStore;
        this.midletsList = midletsList;
        this.featuredApps = featuredApps;
        this.appIcons = new Vector();
        
        // Initialize dimensions
        this.width = getWidth();
        this.height = getHeight();
        this.gridStartY = HEADER_HEIGHT + CAROUSEL_HEIGHT + 20;
        
        // Setup icons placeholder
        for (int i = 0; i < midletsList.size(); i++) {
            appIcons.addElement(null);
        }
        
        // Load default app icon
        try {
            defaultAppIcon = Image.createImage("/default_icon.png");
        } catch (IOException e) {
            // Create a simple default icon if image loading fails
            defaultAppIcon = createDefaultIcon();
        }
        
        // Start loading app icons in background
        loadAppIcons();
    }
    
    /**
     * Creates a simple colored square icon when default icon can't be loaded
     */
    private Image createDefaultIcon() {
        Image img = Image.createImage(32, 32);
        Graphics g = img.getGraphics();
        
        // Create a clean app icon shape
        g.setColor(COLOR_HIGHLIGHT);
        g.fillRect(0, 0, 32, 32);
        g.setColor(COLOR_WHITE);
        g.drawRect(0, 0, 31, 31);
        
        // Draw an "A" to make it look like an app icon
        g.setColor(COLOR_WHITE);
        g.drawLine(11, 22, 16, 8);  // Left diagonal of A
        g.drawLine(16, 8, 21, 22);  // Right diagonal of A
        g.drawLine(13, 16, 19, 16); // Horizontal bar of A
        
        return img;
    }
    
    /**
     * Loads app icons in a background thread
     */
    private void loadAppIcons() {
        Thread t = new Thread() {
            public void run() {
                for (int i = 0; i < midletsList.size(); i++) {
                    MIDletStore.MidletInfo info = (MIDletStore.MidletInfo) midletsList.elementAt(i);
                    loadIconForMidlet(info, i);
                }
            }
        };
        t.start();
    }
    
    /**
     * Loads a single icon for a MIDlet
     */
    private void loadIconForMidlet(MIDletStore.MidletInfo info, int index) {
        HttpConnection connection = null;
        InputStream is = null;
        
        try {
            // Check if the URL is valid before attempting connection
            if (info.getIconUrl() == null || info.getIconUrl().trim().length() == 0) {
                appIcons.setElementAt(defaultAppIcon, index);
                return;
            }
            
            connection = (HttpConnection) Connector.open(info.getIconUrl());
            
            // Set request properties to ensure we get a compatible image format
            connection.setRequestProperty("Accept", "image/png, image/gif, image/jpeg");
            connection.setRequestProperty("User-Agent", "MIDletStore/1.0");
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpConnection.HTTP_OK) {
                is = connection.openInputStream();
                
                // Check content type to ensure it's an image
                String contentType = connection.getHeaderField("Content-Type");
                if (contentType != null && contentType.startsWith("image/")) {
                    try {
                        // Try loading the image with better error handling
                        byte[] imageData = readFully(is);
                        if (imageData != null && imageData.length > 0) {
                            try {
                                Image icon = Image.createImage(imageData, 0, imageData.length);
                                appIcons.setElementAt(icon, index);
                                repaint();
                            } catch (IllegalArgumentException iae) {
                                System.out.println("Invalid image format: " + iae.toString());
                                appIcons.setElementAt(defaultAppIcon, index);
                            }
                        } else {
                            System.out.println("Empty image data received");
                            appIcons.setElementAt(defaultAppIcon, index);
                        }
                    } catch (Exception e) {
                        System.out.println("Error processing image: " + e.toString());
                        appIcons.setElementAt(defaultAppIcon, index);
                    }
                } else {
                    System.out.println("Non-image content type: " + contentType);
                    appIcons.setElementAt(defaultAppIcon, index);
                }
            } else {
                System.out.println("Failed to load icon: HTTP " + responseCode);
                appIcons.setElementAt(defaultAppIcon, index);
            }
        } catch (IOException ioe) {
            System.out.println("Icon loading failed: " + ioe.toString());
            appIcons.setElementAt(defaultAppIcon, index);
        } catch (Exception e) {
            System.out.println("Unexpected error loading icon: " + e.toString());
            appIcons.setElementAt(defaultAppIcon, index);
        } finally {
            try {
                if (is != null) is.close();
                if (connection != null) connection.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    /**
     * Helper method to read an entire input stream into a byte array
     */
    private byte[] readFully(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        
        while ((bytesRead = is.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        
        return baos.toByteArray();
    }
    
    /**
     * Paint the canvas
     */
    protected void paint(Graphics g) {
        width = getWidth();
        height = getHeight();
        
        // Background
        g.setColor(COLOR_BACKGROUND);
        g.fillRect(0, 0, width, height);
        
        // If we have a selectedMidlet, don't draw details here anymore
        // Just show a "Loading..." message until AppDetailView takes over

        
        // Draw header
        drawHeader(g);
        
        // Draw carousel
        drawCarousel(g);
        
        // Draw grid
        drawAppGrid(g);
        
        // Draw navigation hint
        drawNavigationHint(g);
    }
    
    /**
     * Draw the header bar
     */
    private void drawHeader(Graphics g) {
        g.setColor(COLOR_HEADER);
        g.fillRect(0, 0, width, HEADER_HEIGHT);
        
        g.setColor(COLOR_WHITE);
        g.setFont(deriveFont(Font.STYLE_BOLD));
        drawCenteredText(g, "Hack MIDlet Store", width / 2, HEADER_HEIGHT / 2);
    }
    
    /**
     * Draw the featured apps carousel
     */
    private void drawCarousel(Graphics g) {
        int carouselY = HEADER_HEIGHT;
        
        // Carousel background
        g.setColor(COLOR_CAROUSEL_BG);
        g.fillRect(0, carouselY, width, CAROUSEL_HEIGHT);
        
        // Section title
        g.setColor(COLOR_TEXT);
        g.setFont(deriveFont(Font.STYLE_BOLD));
        g.drawString("Featured Apps", 10, carouselY + 5, Graphics.TOP | Graphics.LEFT);
        
        // No featured apps
        if (featuredApps.size() == 0) {
            drawCenteredText(g, "No featured apps", width / 2, carouselY + CAROUSEL_HEIGHT / 2);
            return;
        }
        
        // Calculate animation progress if animating
        int offsetX = 0;
        if (isAnimating) {
            long currentTime = System.currentTimeMillis();
            float progress = (float)(currentTime - animationStartTime) / ANIMATION_DURATION;
            
            if (progress >= 1.0f) {
                // Animation complete
                isAnimating = false;
                currentFeaturedIndex = animationTargetPosition;
            } else {
                // Calculate eased position (using simple ease-out)
                progress = 1.0f - ((1.0f - progress) * (1.0f - progress));
                
                // FIXED: We need to swap the direction of the animation
                // If target is to the right (increasing index), we need to slide leftward (negative offset)
                // If target is to the left (decreasing index), we need to slide rightward (positive offset)
                int direction = -1; // Default direction (sliding left)
                
                // Calculate shortest path direction around the carousel
                int directStep = animationTargetPosition - animationStartPosition;
                int wrapStep = 0;
                
                if (directStep > 0) {
                    // Moving forward (right key)
                    wrapStep = directStep - featuredApps.size();
                } else {
                    // Moving backward (left key)
                    wrapStep = directStep + featuredApps.size();
                }
                
                // Choose the smallest absolute step (direct or wrap-around)
                if (Math.abs(wrapStep) < Math.abs(directStep)) {
                    direction = (wrapStep > 0) ? 1 : -1;
                } else {
                    direction = (directStep > 0) ? -1 : 1;
                }
                
                // Apply the direction to the offset
                offsetX = (int)(direction * width * progress);
            }
        }
        
        // Draw carousel items in Apple-style with full width slides
        int itemHeight = CAROUSEL_HEIGHT - 30; // Leave space for title and pagination
        int itemY = carouselY + 25;
        
        // Draw visible featured apps (current, previous and next)
        for (int i = -1; i <= 1; i++) {
            int index = (currentFeaturedIndex + i + featuredApps.size()) % featuredApps.size();
            MIDletStore.MidletInfo info = (MIDletStore.MidletInfo) featuredApps.elementAt(index);
            
            // Calculate position with animation offset
            int itemX = width * i + width / 2 + offsetX;
            
            // Only draw if item will be visible
            if (itemX + width / 2 >= 0 && itemX - width / 2 <= width) {
                // Draw item background/card
                g.setColor(COLOR_WHITE);
                g.fillRoundRect(itemX - width / 2 + 20, 
                                itemY, 
                                width - 40, 
                                itemHeight, 
                                15, 15);
                
                // Draw app icon
                int iconIndex = midletsList.indexOf(info);
                if (iconIndex >= 0) {
                    Image icon = (Image) appIcons.elementAt(iconIndex);
                    if (icon == null) icon = defaultAppIcon;
                    
                    // Draw larger icon
                    int iconSize = Math.min(icon.getWidth(), icon.getHeight());
                    int iconX = itemX;
                    int iconY = itemY + itemHeight / 2 - 10;
                    g.drawImage(icon, iconX, iconY, Graphics.VCENTER | Graphics.HCENTER);
                }
                
                // Draw app name
                g.setColor(COLOR_TEXT);
                g.setFont(deriveFont(Font.STYLE_BOLD));
                drawCenteredText(g, info.getName(), itemX, itemY + itemHeight - 15);
            }
        }
        
        // Draw pagination indicators
        drawPaginationDots(g, featuredApps.size(), currentFeaturedIndex, 
                           width / 2, carouselY + CAROUSEL_HEIGHT - 10);
    }
    
    /**
     * Draw pagination dots
     */
    private void drawPaginationDots(Graphics g, int totalDots, int currentDot, int centerX, int y) {
        int dotSize = 6;
        int dotSpacing = 10;
        int totalWidth = totalDots * dotSpacing;
        int startX = centerX - totalWidth / 2 + dotSpacing / 2;
        
        for (int i = 0; i < totalDots; i++) {
            int dotX = startX + i * dotSpacing;
            if (i == currentDot) {
                // Current dot is filled
                g.setColor(COLOR_HIGHLIGHT);
                g.fillRoundRect(dotX - dotSize / 2, y - dotSize / 2, dotSize, dotSize, dotSize, dotSize);
            } else {
                // Other dots are hollow
                g.setColor(COLOR_TEXT);
                g.drawRoundRect(dotX - dotSize / 2, y - dotSize / 2, dotSize, dotSize, dotSize, dotSize);
            }
        }
    }
    
    /**
     * Draw the app grid
     */
    private void drawAppGrid(Graphics g) {
        // Section title
        g.setColor(COLOR_TEXT);
        g.setFont(deriveFont(Font.STYLE_PLAIN));
        g.drawString("All Applications", 10, gridStartY - 15, Graphics.TOP | Graphics.LEFT);
        
        // No apps
        if (midletsList.size() == 0) {
            drawCenteredText(g, "No applications available", width / 2, gridStartY + 40);
            return;
        }
        
        // Calculate grid layout
        int cellWidth = width / ITEMS_PER_ROW;
        int cellHeight = ITEM_HEIGHT + 20; // Space for icon + label
        int startIndex = scrollPosition * ITEMS_PER_ROW;
        int endIndex = Math.min(midletsList.size(), startIndex + ITEMS_PER_ROW * VISIBLE_ROWS);
        
        // Add debug info to help troubleshoot selection issues
        g.setColor(COLOR_TEXT);
        g.setFont(deriveFont(Font.STYLE_PLAIN));
        String selInfo = "Sel: " + selectedIndex + " Row: " + (selectedIndex / ITEMS_PER_ROW) + 
                         " Col: " + (selectedIndex % ITEMS_PER_ROW) + " Scroll: " + scrollPosition;
        g.drawString(selInfo, 5, gridStartY + VISIBLE_ROWS * cellHeight + 5, Graphics.TOP | Graphics.LEFT);
        
        for (int i = startIndex; i < endIndex; i++) {
            int gridIndex = i - startIndex;
            int row = gridIndex / ITEMS_PER_ROW;
            int col = gridIndex % ITEMS_PER_ROW;
            
            int x = col * cellWidth + cellWidth / 2;
            int y = gridStartY + row * cellHeight;
            
            MIDletStore.MidletInfo info = (MIDletStore.MidletInfo) midletsList.elementAt(i);
            
            // Draw highlight for selected grid item
            if (!carouselMode && gridIndex == selectedIndex) {
                g.setColor(COLOR_HIGHLIGHT);
                g.fillRoundRect(x - ITEM_WIDTH / 2 - 5, 
                                y - 5,
                                ITEM_WIDTH + 10, 
                                ITEM_HEIGHT + 10, 
                                10, 10);
            }
            
            // Draw item background
            g.setColor(COLOR_WHITE);
            g.fillRoundRect(x - ITEM_WIDTH / 2, 
                            y,
                            ITEM_WIDTH, 
                            ITEM_HEIGHT, 
                            5, 5);
            
            // Draw app icon
            Image icon = (Image) appIcons.elementAt(i);
            if (icon == null) icon = defaultAppIcon;
            g.drawImage(icon, x, y + ITEM_HEIGHT / 2, Graphics.VCENTER | Graphics.HCENTER);
            
            // Use a different visual indicator for compatibility status
            // Add a small colored dot or border to indicate compatibility
            if (info.getSupportedStatus() != null) {
                String status = info.getSupportedStatus();
                int indicatorColor = COLOR_GRAY;
                
                if (status.equals("fully_supported")) {
                    indicatorColor = 0x00AA00; // Green for fully supported
                } else if (status.equals("partially_supported")) {
                    indicatorColor = 0xFFAA00; // Orange for partially supported
                } else if (status.equals("not_supported")) {
                    indicatorColor = 0xAA0000; // Red for not supported
                }
                
                // Draw a small indicator dot in the top-right corner
                g.setColor(indicatorColor);
                g.fillRect(x + ITEM_WIDTH / 2 - 5, y, 5, 5);
            }
            
            // Draw vote count indicator in the bottom right of the icon area
            int votes = info.getVotes();
            if (votes != 0) {
                int indicatorSize = 6;
                int indicatorX = x + ITEM_WIDTH / 2 - 8;
                int indicatorY = y + ITEM_HEIGHT - 8;
                
                if (votes > 0) {
                    g.setColor(COLOR_UPVOTE);
                    g.fillTriangle(
                        indicatorX + indicatorSize / 2, indicatorY,
                        indicatorX + indicatorSize, indicatorY + indicatorSize,
                        indicatorX, indicatorY + indicatorSize
                    );
                } else {
                    g.setColor(COLOR_DOWNVOTE);
                    g.fillTriangle(
                        indicatorX, indicatorY,
                        indicatorX + indicatorSize, indicatorY,
                        indicatorX + indicatorSize / 2, indicatorY + indicatorSize
                    );
                }
            }
            
            // Draw app name
            g.setColor(COLOR_TEXT);
            g.setFont(deriveFont(Font.STYLE_PLAIN));
            drawCenteredText(g, info.getName(), x, y + ITEM_HEIGHT + 10);
        }
    }
    
    /**
     * Draw navigation hint at bottom of screen
     */
    private void drawNavigationHint(Graphics g) {
        g.setColor(COLOR_HEADER);
        g.fillRect(0, height - 20, width, 20);
        
        g.setColor(COLOR_WHITE);
        g.setFont(deriveFont(Font.STYLE_PLAIN));
        drawCenteredText(g, "Use arrow keys to navigate, SELECT to choose", width / 2, height - 10);
    }
    
    /**
     * Helper to draw centered text
     */
    private void drawCenteredText(Graphics g, String text, int x, int y) {
        // In J2ME, we can't combine horizontal and vertical anchors with OR
        // We need to calculate the text position manually
        javax.microedition.lcdui.Font font = g.getFont();
        int textWidth = font.stringWidth(text);
        int textHeight = font.getHeight();
        
        // Center the text
        int xPos = x - (textWidth / 2);
        int yPos = y - (textHeight / 2);
        
        g.drawString(text, xPos, yPos, Graphics.TOP | Graphics.LEFT);
    }
    
    /**
     * Handle key presses
     */
    protected void keyPressed(int keyCode) {
        int gameAction = getGameAction(keyCode);
        

        
        if (carouselMode) {
            // Handle carousel navigation
            if (gameAction == LEFT && !isAnimating) {
                if (featuredApps.size() > 1) {
                    // Start animation - FIXED: Left key should move to previous item
                    isAnimating = true;
                    animationStartTime = System.currentTimeMillis();
                    animationStartPosition = currentFeaturedIndex;
                    animationTargetPosition = (currentFeaturedIndex - 1 + featuredApps.size()) % featuredApps.size();
                    repaint();
                    startCarouselAnimation();
                }
            } else if (gameAction == RIGHT && !isAnimating) {
                if (featuredApps.size() > 1) {
                    // Start animation - FIXED: Right key should move to next item
                    isAnimating = true;
                    animationStartTime = System.currentTimeMillis();
                    animationStartPosition = currentFeaturedIndex;
                    animationTargetPosition = (currentFeaturedIndex + 1) % featuredApps.size();
                    repaint();
                    startCarouselAnimation();
                }
            } else if (gameAction == DOWN) {
                // Move focus to grid
                carouselMode = false;
                selectedIndex = 0;
                repaint();
            } else if (gameAction == FIRE) {
                // Show app details immediately when an app is selected
                if (featuredApps.size() > 0) {
                    selectedMidlet = (MIDletStore.MidletInfo) featuredApps.elementAt(currentFeaturedIndex);
                    midletStore.showAppDetails(selectedMidlet, 
                        (Image)appIcons.elementAt(midletsList.indexOf(selectedMidlet)));
                }
            }
        } else {
            // Handle grid navigation
            if (gameAction == LEFT) {
                if (selectedIndex % ITEMS_PER_ROW > 0) {
                    selectedIndex--;
                    repaint();
                }
            } else if (gameAction == RIGHT) {
                // Fix the overly restrictive condition for right navigation
                if (selectedIndex % ITEMS_PER_ROW < ITEMS_PER_ROW - 1 && 
                    (selectedIndex + scrollPosition * ITEMS_PER_ROW + 1) < midletsList.size()) {
                    selectedIndex++;
                    repaint();
                }
            } else if (gameAction == UP) {
                if (selectedIndex >= ITEMS_PER_ROW) {
                    selectedIndex -= ITEMS_PER_ROW;
                    repaint();
                } else if (scrollPosition > 0) {
                    // Scroll up
                    scrollPosition--;
                    repaint();
                } else {
                    // Move focus to carousel
                    carouselMode = true;
                    repaint();
                }
            } else if (gameAction == DOWN) {
                // Fix the condition for down navigation
                int nextRow = selectedIndex + ITEMS_PER_ROW;
                // Remove the restriction on visible rows, just check if item exists
                if (nextRow < ITEMS_PER_ROW * VISIBLE_ROWS && 
                    (nextRow + scrollPosition * ITEMS_PER_ROW) < midletsList.size()) {
                    selectedIndex = nextRow;
                    repaint();
                } else if ((scrollPosition + 1) * ITEMS_PER_ROW + selectedIndex % ITEMS_PER_ROW < midletsList.size()) {
                    // Scroll down
                    scrollPosition++;
                    // Keep the column position but update row
                    int col = selectedIndex % ITEMS_PER_ROW;
                    // Ensure we don't select a non-existent item
                    int adjustedIndex = scrollPosition * ITEMS_PER_ROW + col;
                    if (adjustedIndex >= midletsList.size()) {
                        // Adjust to the last item in that row
                        col = (midletsList.size() - 1) % ITEMS_PER_ROW;
                    }
                    selectedIndex = col;
                    repaint();
                }
            } else if (gameAction == FIRE) {
                int index = selectedIndex + scrollPosition * ITEMS_PER_ROW;
                if (index < midletsList.size()) {
                    selectedMidlet = (MIDletStore.MidletInfo) midletsList.elementAt(index);
                    midletStore.showAppDetails(selectedMidlet, 
                        (Image)appIcons.elementAt(index));
                }
            }
        }
    }
    
    /**
     * Start animation timer for carousel
     */
    private void startCarouselAnimation() {
        Thread animationThread = new Thread() {
            public void run() {
                try {
                    while (isAnimating) {
                        repaint();
                        Thread.sleep(ANIMATION_DURATION / ANIMATION_STEPS);
                    }
                    // Final repaint after animation completes
                    repaint();
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        };
        animationThread.start();
    }
    
    /**
     * Returns the selected MIDlet, or null if none is selected
     * This method would be called by MIDletStore when user chooses to install
     */
    public MIDletStore.MidletInfo getSelectedMidlet() {
        MIDletStore.MidletInfo result = selectedMidlet;
        selectedMidlet = null; // Reset selection
        repaint();
        return result;
    }
    
    /**
     * Inner class to represent font style constants (not available in MIDP 2.0)
     */
    private static class Font {
        public static final int STYLE_PLAIN = 0;
        public static final int STYLE_BOLD = 1;
    }
    
    /**
     * Stub method as J2ME doesn't support font derivation
     * In a real implementation, you'd use different font indices or sizes
     */
    private javax.microedition.lcdui.Font deriveFont(int style) {
        // In J2ME, use different system fonts
        if (style == Font.STYLE_BOLD) {
            return javax.microedition.lcdui.Font.getFont(
                javax.microedition.lcdui.Font.FACE_SYSTEM,
                javax.microedition.lcdui.Font.STYLE_BOLD,
                javax.microedition.lcdui.Font.SIZE_MEDIUM);
        } else {
            return javax.microedition.lcdui.Font.getFont(
                javax.microedition.lcdui.Font.FACE_SYSTEM,
                javax.microedition.lcdui.Font.STYLE_PLAIN,
                javax.microedition.lcdui.Font.SIZE_MEDIUM);
        }
    }
}
