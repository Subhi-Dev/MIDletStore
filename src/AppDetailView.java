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

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Image;

import javax.microedition.m2g.SVGImage;
import javax.microedition.m2g.SVGAnimator;


import javax.microedition.m2g.ScalableImage;
import javax.microedition.m2g.SVGAnimator;

import com.sun.svg.component.LoadingScreen;
import com.sun.svg.util.DefaultSVGAnimator;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
/**
 * Detailed app view in Apple App Store style
 * Supports scrolling and fetching additional details from server
 */
public class AppDetailView extends Canvas {
    // Constants
    private static final int HEADER_HEIGHT = 30;
    private static final int PADDING = 10;
    private static final int BUTTON_HEIGHT = 40;
    
    // Colors
    private static final int COLOR_BACKGROUND = 0xF0F0F0;
    private static final int COLOR_HEADER = 0x2980B9;
    private static final int COLOR_TEXT = 0x2C3E50;
    private static final int COLOR_HIGHLIGHT = 0x3498DB;
    private static final int COLOR_WHITE = 0xFFFFFF;
    private static final int COLOR_LIGHT_GRAY = 0xD3D3D3;
    private static final int COLOR_GRAY = 0x7F8C8D;
    
    // Layout
    private int width, height;
    private int contentHeight;
    private int scrollPosition = 0;
    private static final int MAX_SCROLL_SPEED = 20;
    
    // State
    private boolean isLoading = false;
    private boolean moreDetailsLoaded = false;
    private String additionalDescription = null;
    private String version = null;
    private String size = null;
    private String developer = null;
    private String category = null;
    private float rating = 0.0f;
    
    // App data and callbacks
    private MIDletStore midletStore;
    private MIDletStore.MidletInfo app;
    private Image appIcon;
    private CommandListener commandListener;
    
    // Commands
    private Command backCommand;
    private Command installCommand;
    
    /**
     * Constructor
     */
    public AppDetailView(MIDletStore midletStore, MIDletStore.MidletInfo app, Image appIcon) {
        this.midletStore = midletStore;
        this.app = app;
        this.appIcon = appIcon;
        
        // Initialize dimensions
        this.width = getWidth();
        this.height = getHeight();
        
        // Set up commands - Fix: Make sure both commands are properly added
        backCommand = new Command("Back", Command.BACK, 1);
        installCommand = new Command("Install", Command.ITEM, 1);
        this.addCommand(backCommand);
        this.addCommand(installCommand);
        
        // Start loading additional details
        fetchAppDetails();
    }
    
    public void setCommandListener(CommandListener listener) {
        super.setCommandListener(listener);
        this.commandListener = listener;
    }
    
    /**
     * Fetch additional app details from server
     */
    private void fetchAppDetails() {
        Thread t = new Thread() {
            public void run() {
                isLoading = true;
                repaint();
                
                HttpConnection connection = null;
                InputStream is = null;
                
                try {
                    // Format URL for app details
                    String appId = app.getDownloadUrl().substring(app.getDownloadUrl().lastIndexOf('/') + 1);
                    String detailsUrl = "http://localhost:3000/storeapi/apps/" + appId;
                    
                    connection = (HttpConnection) Connector.open(detailsUrl);
                    
                    if (connection.getResponseCode() == HttpConnection.HTTP_OK) {
                        is = connection.openInputStream();
                        parseAppDetails(is);
                        moreDetailsLoaded = true;
                    }
                } catch (Exception e) {
                    System.out.println("Error fetching app details: " + e.toString());
                } finally {
                    try {
                        if (is != null) is.close();
                        if (connection != null) connection.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                    
                    isLoading = false;
                    repaint();
                }
            }
        };
        t.start();
    }
    
    /**
     * Parse the app details CSV response
     */
    private void parseAppDetails(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        
        while ((bytesRead = is.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        
        String csv = new String(baos.toByteArray());
        
        // Parse CSV format - expect header row followed by data row
        String[] lines = split(csv, "\n");
        if (lines.length >= 2) {
            String headerLine = lines[0];
            String dataLine = lines[1];
            
            String[] headers = split(headerLine, ",");
            String[] values = split(dataLine, ",");
            
            // Process each column
            for (int i = 0; i < headers.length && i < values.length; i++) {
                String header = headers[i].trim();
                String value = values[i].trim();
                
                if (header.equals("description")) {
                    additionalDescription = value;
                } else if (header.equals("version")) {
                    version = value;
                } else if (header.equals("size")) {
                    size = value + " KB";
                } else if (header.equals("developer")) {
                    developer = value;
                } else if (header.equals("category")) {
                    category = value;
                } else if (header.equals("rating")) {
                    try {
                        rating = Float.parseFloat(value);
                    } catch (NumberFormatException e) {
                        // Ignore parsing errors
                    }
                }
            }
        }
        
        repaint();
    }
    
    /**
     * Split string by comma but handle quoted values properly
     * This handles the case where commas appear inside quoted fields
     */
    private String[] splitCSV(String line) {
        Vector result = new Vector();
        
        if (line == null || line.length() == 0) {
            String[] empty = {""};
            return empty;
        }
        
        boolean inQuotes = false;
        StringBuffer field = new StringBuffer();  // Changed from StringBuilder to StringBuffer
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                // Toggle quote state
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                // End of field
                result.addElement(field.toString().trim());
                field = new StringBuffer();  // Changed from StringBuilder to StringBuffer
            } else {
                // Part of the field
                field.append(c);
            }
        }
        
        // Add the last field
        result.addElement(field.toString().trim());
        
        // Convert to array
        String[] array = new String[result.size()];
        for (int i = 0; i < result.size(); i++) {
            array[i] = (String) result.elementAt(i);
        }
        
        return array;
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
        
        // Draw the app details with scrolling
        drawAppDetails(g);
    }
    
    /**
     * Draw app details with scrolling support
     */
    private void drawAppDetails(Graphics g) {
        // Fixed header
        g.setColor(COLOR_HEADER);
        g.fillRect(0, 0, width, HEADER_HEIGHT);
        
        g.setColor(COLOR_WHITE);
        g.setFont(javax.microedition.lcdui.Font.getFont(
            javax.microedition.lcdui.Font.FACE_SYSTEM,
            javax.microedition.lcdui.Font.STYLE_BOLD,
            javax.microedition.lcdui.Font.SIZE_MEDIUM));
        drawText(g, app.getName(), width / 2, HEADER_HEIGHT / 2, true);
        
        // Back button in header
        g.setColor(COLOR_WHITE);
        g.fillTriangle(10, HEADER_HEIGHT / 2, 18, HEADER_HEIGHT / 2 - 8, 18, HEADER_HEIGHT / 2 + 8);
        drawText(g, "Back", 30, HEADER_HEIGHT / 2, false);
        
        // Scroll area starts below header
        g.translate(0, -scrollPosition);
        int y = HEADER_HEIGHT + PADDING;
        
        // Top section - icon, name, and buttons
        g.setColor(COLOR_WHITE);
        g.fillRect(0, y, width, 120);
        
        // App icon
        if (appIcon != null) {
            g.drawImage(appIcon, PADDING, y + 10, Graphics.TOP | Graphics.LEFT);
        }
        
        // App name and developer
        int textX = PADDING * 2 + 32; // icon width + padding
        g.setColor(COLOR_TEXT);
        g.setFont(javax.microedition.lcdui.Font.getFont(
            javax.microedition.lcdui.Font.FACE_SYSTEM,
            javax.microedition.lcdui.Font.STYLE_BOLD,
            javax.microedition.lcdui.Font.SIZE_MEDIUM));
        g.drawString(app.getName(), textX, y + 10, Graphics.TOP | Graphics.LEFT);
        
        g.setFont(javax.microedition.lcdui.Font.getFont(
            javax.microedition.lcdui.Font.FACE_SYSTEM,
            javax.microedition.lcdui.Font.STYLE_PLAIN,
            javax.microedition.lcdui.Font.SIZE_SMALL));
        g.setColor(COLOR_GRAY);
        g.drawString(developer != null ? developer : "Unknown Developer", textX, y + 30, Graphics.TOP | Graphics.LEFT);
        
        // Rating stars
        drawRatingStars(g, rating, textX, y + 50);
        
        // GET / Download/INSTALL button
        g.setColor(COLOR_HIGHLIGHT);
        g.fillRoundRect(width - 85 - PADDING, y + 10, 70, 40, 10, 10);
        g.setColor(COLOR_WHITE);
        g.setFont(javax.microedition.lcdui.Font.getFont(
            javax.microedition.lcdui.Font.FACE_SYSTEM,
            javax.microedition.lcdui.Font.STYLE_BOLD,
            javax.microedition.lcdui.Font.SIZE_MEDIUM));
        drawText(g, "GET", width - 50 - PADDING, y + 30, true);
        
        y += 90; // Move down past the top section
        
        // Information Section - now comes directly after the top section
        g.setColor(COLOR_WHITE);
        g.fillRect(0, y, width, 120);
        
        g.setColor(COLOR_TEXT);
        g.setFont(javax.microedition.lcdui.Font.getFont(
            javax.microedition.lcdui.Font.FACE_SYSTEM,
            javax.microedition.lcdui.Font.STYLE_BOLD,
            javax.microedition.lcdui.Font.SIZE_MEDIUM));
        g.drawString("Information", PADDING, y + PADDING, Graphics.TOP | Graphics.LEFT);
        
        // App details in two columns
        int leftColX = PADDING;
        int rightColX = width / 2 + PADDING;
        int infoY = y + 40;
        
        g.setFont(javax.microedition.lcdui.Font.getFont(
            javax.microedition.lcdui.Font.FACE_SYSTEM,
            javax.microedition.lcdui.Font.STYLE_PLAIN,
            javax.microedition.lcdui.Font.SIZE_SMALL));
        
        // Left column
        g.drawString("Developer", leftColX, infoY, Graphics.TOP | Graphics.LEFT);
        g.drawString("Category", leftColX, infoY + 20, Graphics.TOP | Graphics.LEFT);
        
        // Right column
        g.drawString(developer != null ? developer : "Unknown", rightColX, infoY, Graphics.TOP | Graphics.LEFT);
        g.drawString(category != null ? category : "Utilities", rightColX, infoY + 20, Graphics.TOP | Graphics.LEFT);
        
        // Next row
        infoY += 40;
        
        // Left column
        g.drawString("Size", leftColX, infoY, Graphics.TOP | Graphics.LEFT);
        g.drawString("Version", leftColX, infoY + 20, Graphics.TOP | Graphics.LEFT);
        
        // Right column
        g.drawString(size != null ? size : "Unknown", rightColX, infoY, Graphics.TOP | Graphics.LEFT);
        g.drawString(version != null ? version : "1.0", rightColX, infoY + 20, Graphics.TOP | Graphics.LEFT);
        
        y += 140; // Move down past info section
        
        // Description section
        g.setColor(COLOR_WHITE);
        g.fillRect(0, y, width, 200); // Flexible height
        
        g.setColor(COLOR_TEXT);
        g.setFont(javax.microedition.lcdui.Font.getFont(
            javax.microedition.lcdui.Font.FACE_SYSTEM,
            javax.microedition.lcdui.Font.STYLE_BOLD,
            javax.microedition.lcdui.Font.SIZE_MEDIUM));
        g.drawString("Description", PADDING, y + PADDING, Graphics.TOP | Graphics.LEFT);
        
        // Combine basic and additional description
        String fullDescription = app.getDescription();
        if (additionalDescription != null && additionalDescription.length() > 0) {
            fullDescription += "\n" + additionalDescription;
        }
        
        // Word-wrapped description text
        g.setFont(javax.microedition.lcdui.Font.getFont(
            javax.microedition.lcdui.Font.FACE_SYSTEM,
            javax.microedition.lcdui.Font.STYLE_PLAIN,
            javax.microedition.lcdui.Font.SIZE_SMALL));
        int descY = y + 40;
        descY = drawWrappedText(g, fullDescription, PADDING, descY, width - PADDING * 2);
        
        y = descY + PADDING;
        
        // Bottom install button (scrolls with content)
        g.setColor(COLOR_HIGHLIGHT);
        g.fillRoundRect(PADDING, y, width - PADDING * 2, BUTTON_HEIGHT, 10, 10);
        g.setColor(COLOR_WHITE);
        g.setFont(javax.microedition.lcdui.Font.getFont(
            javax.microedition.lcdui.Font.FACE_SYSTEM,
            javax.microedition.lcdui.Font.STYLE_BOLD,
            javax.microedition.lcdui.Font.SIZE_MEDIUM));
        drawText(g, "INSTALL", width / 2, y + BUTTON_HEIGHT / 2, true);
        
        y += BUTTON_HEIGHT + PADDING * 3;
        
        // Reset translation and draw scroll indicators if needed
        contentHeight = y - HEADER_HEIGHT;
        g.translate(0, scrollPosition);
        
        // Draw scroll indicators if content is taller than screen
        if (contentHeight > (height - HEADER_HEIGHT)) {
            // Scroll indicator at top
            if (scrollPosition > 0) {
                g.setColor(COLOR_GRAY);
                int arrowX = width / 2;
                int arrowY = HEADER_HEIGHT + 10;
                g.fillTriangle(arrowX, arrowY, arrowX - 10, arrowY + 10, arrowX + 10, arrowY + 10);
            }
            
            // Scroll indicator at bottom
            if (scrollPosition < contentHeight - (height - HEADER_HEIGHT)) {
                g.setColor(COLOR_GRAY);
                int arrowX = width / 2;
                int arrowY = height - 10;
                g.fillTriangle(arrowX, arrowY, arrowX - 10, arrowY - 10, arrowX + 10, arrowY - 10);
            }
        }
        
        // Loading indicator
        if (isLoading) {
            g.setColor(0x80000000);
            g.fillRect(0, HEADER_HEIGHT, width, height - HEADER_HEIGHT);
            g.setColor(COLOR_WHITE);
            g.setFont(javax.microedition.lcdui.Font.getFont(
                javax.microedition.lcdui.Font.FACE_SYSTEM,
                javax.microedition.lcdui.Font.STYLE_BOLD,
                javax.microedition.lcdui.Font.SIZE_MEDIUM));
            drawText(g, "Loading...", width / 2, height / 2, true);
        }
    }
    
    /**
     * Draw stars for a rating
     */
    private void drawRatingStars(Graphics g, float rating, int x, int y) {
        int starWidth = 15;
        int starCount = 5;
        
        for (int i = 0; i < starCount; i++) {
            // Filled and unfilled stars based on rating
            if (i < (int)rating) {
                // Filled star
                g.setColor(COLOR_HIGHLIGHT);
                drawSimpleStar(g, x + i * (starWidth + 2), y, starWidth, true);
            } else if (i == (int)rating && rating - (int)rating >= 0.5) {
                // Half-filled star
                g.setColor(COLOR_LIGHT_GRAY);
                drawSimpleStar(g, x + i * (starWidth + 2), y, starWidth, true);
                g.setColor(COLOR_HIGHLIGHT);
                drawHalfStar(g, x + i * (starWidth + 2), y, starWidth);
            } else {
                // Empty star
                g.setColor(COLOR_LIGHT_GRAY);
                drawSimpleStar(g, x + i * (starWidth + 2), y, starWidth, false);
            }
        }
    }
    
    /**
     * Draw a simple star shape using line and fill methods available in J2ME
     */
    private void drawSimpleStar(Graphics g, int x, int y, int size, boolean filled) {
        // Draw a simple square with diagonal lines for the star shape
        // since J2ME doesn't support fillPolygon
        int halfSize = size / 2;
        
        if (filled) {
            // Draw filled square for the star
            g.fillRect(x, y, size, size);
            
            // Draw diagonal lines to create star points
            g.setColor(COLOR_WHITE);
            g.drawLine(x, y, x + size, y + size);
            g.drawLine(x + size, y, x, y + size);
        } else {
            // Draw star outline
            g.drawRect(x, y, size-1, size-1);
            
            // Draw diagonal lines to create star points
            g.drawLine(x, y, x + size-1, y + size-1);
            g.drawLine(x + size-1, y, x, y + size-1);
        }
    }
    
    /**
     * Draw a half-filled star using available J2ME graphics methods
     */
    private void drawHalfStar(Graphics g, int x, int y, int size) {
        // Fill half the star with highlighted color
        g.fillRect(x, y, size/2, size);
        
        // Draw outline to maintain the star shape
        g.setColor(COLOR_LIGHT_GRAY);
        g.drawRect(x, y, size-1, size-1);
        
        // Draw diagonal lines to create star points
        g.drawLine(x, y, x + size-1, y + size-1);
        g.drawLine(x + size-1, y, x, y + size-1);
    }
    
    /**
     * Draw wrapped text and return the new Y position
     */
    private int drawWrappedText(Graphics g, String text, int x, int y, int maxWidth) {
        if (text == null || text.length() == 0) {
            return y;
        }
        
        javax.microedition.lcdui.Font font = g.getFont();
        int lineHeight = font.getHeight();
        
        Vector lines = new Vector();
        String[] paragraphs = split(text, "\n");
        
        for (int p = 0; p < paragraphs.length; p++) {
            String paragraph = paragraphs[p];
            String[] words = split(paragraph, " ");
            
            String currentLine = "";
            for (int i = 0; i < words.length; i++) {
                String testLine = currentLine.length() > 0 ? 
                    currentLine + " " + words[i] : words[i];
                    
                if (font.stringWidth(testLine) <= maxWidth) {
                    currentLine = testLine;
                } else {
                    lines.addElement(currentLine);
                    currentLine = words[i];
                }
            }
            
            if (currentLine.length() > 0) {
                lines.addElement(currentLine);
            }
            
            // Add a blank line between paragraphs (except after the last one)
            if (p < paragraphs.length - 1) {
                lines.addElement("");
            }
        }
        
        for (int i = 0; i < lines.size(); i++) {
            String line = (String) lines.elementAt(i);
            g.drawString(line, x, y, Graphics.TOP | Graphics.LEFT);
            y += lineHeight;
        }
        
        return y;
    }
    
    /**
     * Draw text centered or aligned left
     */
    private void drawText(Graphics g, String text, int x, int y, boolean centered) {
        if (centered) {
            int textWidth = g.getFont().stringWidth(text);
            x -= textWidth / 2;
        }
        
        int textHeight = g.getFont().getHeight();
        y -= textHeight / 2;
        
        g.drawString(text, x, y, Graphics.TOP | Graphics.LEFT);
    }
    
    /**
     * Split a string by delimiter (J2ME doesn't have String.split)
     */
    private String[] split(String str, String delimiter) {
        Vector result = new Vector();
        
        if (str == null || str.length() == 0) {
            String[] empty = {""};
            return empty;
        }
        
        int lastIndex = 0;
        int index = str.indexOf(delimiter);
        
        while (index >= 0) {
            result.addElement(str.substring(lastIndex, index));
            lastIndex = index + delimiter.length();
            index = str.indexOf(delimiter, lastIndex);
        }
        
        if (lastIndex <= str.length()) {
            result.addElement(str.substring(lastIndex));
        }
        
        String[] array = new String[result.size()];
        for (int i = 0; i < result.size(); i++) {
            array[i] = (String) result.elementAt(i);
        }
        
        return array;
    }
    
    /**
     * Handle key presses including scrolling
     */
    protected void keyPressed(int keyCode) {
        int gameAction = getGameAction(keyCode);
        
        if (gameAction == UP) {
            // Scroll up
            scrollPosition = Math.max(0, scrollPosition - 20);
            repaint();
        } else if (gameAction == DOWN) {
            // Scroll down
            int maxScroll = Math.max(0, contentHeight - (height - HEADER_HEIGHT));
            scrollPosition = Math.min(maxScroll, scrollPosition + 20);
            repaint();
        } else if (gameAction == FIRE) {
            // Install the app
            midletStore.installMidlet(app);
        } else if (gameAction == LEFT || keyCode == KEY_NUM0) {
            // Go back - Fix: Handle back key properly
            if (commandListener != null) {
                commandListener.commandAction(backCommand, this);
            }
        }
    }
    
    /**
     * Handle pointer dragged events for scrolling (touchscreens)
     */
    protected void pointerDragged(int x, int y) {
        if (y != getLastPointerY()) {
            int delta = getLastPointerY() - y;
            
            // Limit scroll speed
            if (delta > MAX_SCROLL_SPEED) delta = MAX_SCROLL_SPEED;
            if (delta < -MAX_SCROLL_SPEED) delta = -MAX_SCROLL_SPEED;
            
            // Update scroll position
            scrollPosition += delta;
            
            // Keep within bounds
            if (scrollPosition < 0) scrollPosition = 0;
            int maxScroll = Math.max(0, contentHeight - (height - HEADER_HEIGHT));
            if (scrollPosition > maxScroll) scrollPosition = maxScroll;
            
            repaint();
        }
    }
    
    /**
     * Get last pointer Y position for calculating drag distance
     */
    protected int getLastPointerY() {
        // Needed for pointerDragged to work.
        // In a real app, you would store the last Y position.
        return 0;
    }
    
    /**
     * Get the current app info
     */
    public MIDletStore.MidletInfo getAppInfo() {
        return app;
    }
}
