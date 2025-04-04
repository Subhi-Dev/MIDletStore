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
    private static final int VOTE_BUTTON_WIDTH = 70;
    
    // Colors
    private static final int COLOR_BACKGROUND = 0xF0F0F0;
    private static final int COLOR_HEADER = 0xEC3750;
    private static final int COLOR_TEXT = 0x2C3E50;
    private static final int COLOR_HIGHLIGHT = 0xE83F56;
    private static final int COLOR_WHITE = 0xFFFFFF;
    private static final int COLOR_LIGHT_GRAY = 0xD3D3D3;
    private static final int COLOR_GRAY = 0x7F8C8D;
    private static final int COLOR_UPVOTE = 0x27AE60;  // Green for upvotes
    private static final int COLOR_DOWNVOTE = 0xC0392B; // Red for downvotes
    
    // Layout
    private int width, height;
    private int contentHeight;
    private int scrollPosition = 0;
    private static final int MAX_SCROLL_SPEED = 20;
    
    // State
    private boolean isLoading = false;
    private boolean moreDetailsLoaded = false;
    private boolean isVoting = false;
    private String additionalDescription = null;
    private String version = null;
    private String size = null;
    private String developer = null;
    private String category = null;
    private int votes = 0; // Votes instead of rating
    
    // App data and callbacks
    private MIDletStore midletStore;
    private MIDletStore.MidletInfo app;
    private Image appIcon;
    private CommandListener commandListener;
    
    // Commands
    private Command backCommand;
    private Command installCommand;
    private Command upvoteCommand;
    private Command downvoteCommand;
    
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
        
        // Set up commands
        backCommand = new Command("Back", Command.BACK, 1);
        installCommand = new Command("Install", Command.ITEM, 1);
        upvoteCommand = new Command("Upvote", Command.ITEM, 2);
        downvoteCommand = new Command("Downvote", Command.ITEM, 3);
        
        this.addCommand(backCommand);
        this.addCommand(installCommand);
        this.addCommand(upvoteCommand);
        this.addCommand(downvoteCommand);
        
        // Initialize votes from app info
        this.votes = app.getVotes();
        
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
                    // Format URL for app details using app ID instead of index
                    String appId = app.getId();
                    String detailsUrl = "http://localhost:3000/storeapi/apps/" + appId;
                    
                    // Include device info in the request
                    if (midletStore != null) {
                        String deviceInfo = midletStore.getDeviceQueryString();
                        if (deviceInfo != null && deviceInfo.length() > 0) {
                            detailsUrl += (detailsUrl.indexOf('?') >= 0 ? "&" : "?") + deviceInfo;
                        }
                    }
                    
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
                } else if (header.equals("votes")) {
                    try {
                        votes = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        // Ignore parsing errors
                        votes = 0;
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
        StringBuffer field = new StringBuffer();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                // Toggle quote state
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                // End of field
                result.addElement(field.toString().trim());
                field = new StringBuffer();
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
     * Draw upvote/downvote display
     */
    private void drawVotes(Graphics g, int votes, int x, int y) {
        // Format the votes count with a + sign for positive numbers
        String votesText = (votes > 0 ? "+" : "") + String.valueOf(votes);
        
        int iconSize = 12;
        int textWidth = g.getFont().stringWidth(votesText);
        int totalWidth = iconSize + 4 + textWidth;
        
        // Draw the vote count
        if (votes > 0) {
            // Positive votes (upvotes)
            g.setColor(COLOR_UPVOTE);
            
            // Draw an up arrow icon
            int[] xPoints = {x + iconSize/2, x + iconSize, x};
            int[] yPoints = {y, y + iconSize, y + iconSize};
            g.fillTriangle(xPoints[0], yPoints[0], xPoints[1], yPoints[1], xPoints[2], yPoints[2]);
            
        } else if (votes < 0) {
            // Negative votes (downvotes)
            g.setColor(COLOR_DOWNVOTE);
            
            // Draw a down arrow icon
            int[] xPoints = {x, x + iconSize, x + iconSize/2};
            int[] yPoints = {y, y, y + iconSize};
            g.fillTriangle(xPoints[0], yPoints[0], xPoints[1], yPoints[1], xPoints[2], yPoints[2]);
            
        } else {
            // Zero votes
            g.setColor(COLOR_GRAY);
            g.drawRect(x, y, iconSize, iconSize);
        }
        
        // Draw the number
        g.drawString(votesText, x + iconSize + 4, y, Graphics.LEFT | Graphics.TOP);
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
        int textX = PADDING * 2 + 48; // icon width + padding
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
        
        // Draw votes instead of stars
        drawVotes(g, votes, textX, y + 45);
        
        // GET / Download/INSTALL button
        g.setColor(COLOR_HIGHLIGHT);
        g.fillRoundRect(width - 85 - PADDING, y + 10, 70, 40, 10, 10);
        g.setColor(COLOR_WHITE);
        g.setFont(javax.microedition.lcdui.Font.getFont(
            javax.microedition.lcdui.Font.FACE_SYSTEM,
            javax.microedition.lcdui.Font.STYLE_BOLD,
            javax.microedition.lcdui.Font.SIZE_MEDIUM));
        drawText(g, "GET", width - 50 - PADDING, y + 30, true);
        
        y += 80; // Move down past the top section
        
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
        g.drawString("Compatibility", leftColX, infoY + 40, Graphics.TOP | Graphics.LEFT);
        g.drawString("Votes", leftColX, infoY + 60, Graphics.TOP | Graphics.LEFT); // Add votes row
        
        // Right column
        g.drawString(size != null ? size : "Unknown", rightColX, infoY, Graphics.TOP | Graphics.LEFT);
        g.drawString(version != null ? version : "1.0", rightColX, infoY + 20, Graphics.TOP | Graphics.LEFT);
        
        // Draw the supported status with appropriate color based on the value
        String status = app.getSupportedStatus();
        if (status == null) status = "Unknown";
        
        if (status.equals("fully_supported")) {
            g.setColor(0x00AA00); // Green for fully supported
            g.drawString("Fully Supported", rightColX, infoY + 40, Graphics.TOP | Graphics.LEFT);
        } else if (status.equals("partially_supported")) {
            g.setColor(0xFFAA00); // Orange for partially supported
            g.drawString("Partially Supported", rightColX, infoY + 40, Graphics.TOP | Graphics.LEFT);
        } else if (status.equals("not_supported")) {
            g.setColor(0xAA0000); // Red for not supported
            g.drawString("Not Supported", rightColX, infoY + 40, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(COLOR_GRAY); // Gray for unknown
            g.drawString(status, rightColX, infoY + 40, Graphics.TOP | Graphics.LEFT);
        }
        
        // Draw votes count with color
        if (votes > 0) {
            g.setColor(COLOR_UPVOTE);
            g.drawString("+" + votes, rightColX, infoY + 60, Graphics.TOP | Graphics.LEFT);
        } else if (votes < 0) {
            g.setColor(COLOR_DOWNVOTE);
            g.drawString(String.valueOf(votes), rightColX, infoY + 60, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(COLOR_GRAY);
            g.drawString("0", rightColX, infoY + 60, Graphics.TOP | Graphics.LEFT);
        }
        
        g.setColor(COLOR_TEXT); // Reset color
        
        y += 180; // Increase height to accommodate the new row
        
        // Description section
        g.setColor(COLOR_WHITE);
        g.fillRect(0, y, width, 200); // Flexible height
        
        g.setColor(COLOR_TEXT);
        g.setFont(javax.microedition.lcdui.Font.getFont(
            javax.microedition.lcdui.Font.FACE_SYSTEM,
            javax.microedition.lcdui.Font.STYLE_BOLD,
            javax.microedition.lcdui.Font.SIZE_MEDIUM));
        g.drawString("Description", PADDING, y + PADDING, Graphics.TOP | Graphics.LEFT);
        
        // Word-wrapped description text
        String fullDescription = app.getDescription();
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
        
        // Loading or voting indicator
        if (isLoading || isVoting) {
            g.setColor(0x80000000);
            g.fillRect(0, HEADER_HEIGHT, width, height - HEADER_HEIGHT);
            g.setColor(COLOR_WHITE);
            g.setFont(javax.microedition.lcdui.Font.getFont(
                javax.microedition.lcdui.Font.FACE_SYSTEM,
                javax.microedition.lcdui.Font.STYLE_BOLD,
                javax.microedition.lcdui.Font.SIZE_MEDIUM));
            drawText(g, isVoting ? "Submitting vote..." : "Loading...", width / 2, height / 2, true);
        }
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
     * Handle pointer pressed events for detecting vote button clicks
     */
    protected void pointerPressed(int x, int y) {
        // Check if we clicked on vote buttons (adjust y coordinates to match your layout)
        int buttonY = HEADER_HEIGHT + PADDING + 70;
        
        // Handle INSTALL button click
        int installY = height - BUTTON_HEIGHT - PADDING * 3;
        if (y >= installY && y <= installY + BUTTON_HEIGHT && 
            x >= PADDING && x <= width - PADDING) {
            // Install button at bottom clicked
            midletStore.installMidlet(app);
            return;
        }
        
        // Store last pointer position for dragging
        lastPointerY = y;
    }
    
    // Store last pointer Y position for drag calculations
    private int lastPointerY = 0;
    
    /**
     * Get last pointer Y position for calculating drag distance
     */
    protected int getLastPointerY() {
        return lastPointerY;
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
     * Get the current app info
     */
    public MIDletStore.MidletInfo getAppInfo() {
        return app;
    }
    
    /**
     * Command action handler
     */
    public void commandAction(Command c, Displayable d) {
        if (c == backCommand) {
            if (commandListener != null) {
                commandListener.commandAction(backCommand, this);
            }
        } else if (c == installCommand) {
            midletStore.installMidlet(app);
        } else if (c == upvoteCommand) {
            submitVote(true);
        } else if (c == downvoteCommand) {
            submitVote(false);
        }
    }
    
    /**
     * Submit a vote (upvote or downvote) to the server
     */
    private void submitVote(final boolean isUpvote) {
        if (isVoting) return; // Prevent multiple simultaneous vote requests
        
        isVoting = true;
        repaint();
        
        Thread t = new Thread() {
            public void run() {
                HttpConnection connection = null;
                InputStream is = null;
                
                try {
                    // Format URL for voting
                    String appId = app.getId();
                    String voteUrl = "http://localhost:3000/storeapi/vote?appId=" + appId + "&voteType=" + (isUpvote ? "upvote" : "downvote");
                    
                    // Include device info in the request to prevent duplicate votes
                    if (midletStore != null) {
                        String deviceInfo = midletStore.getDeviceQueryString();
                        if (deviceInfo != null && deviceInfo.length() > 0) {
                            voteUrl += "&" + deviceInfo;
                        }
                    }
                    
                    connection = (HttpConnection) Connector.open(voteUrl);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("User-Agent", "MIDletStore/1.0");
                    
                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpConnection.HTTP_OK) {
                        is = connection.openInputStream();
                        
                        // Parse response to get updated vote count
                        String response = readResponse(is);
                        int newVotes = parseVoteResponse(response);
                        
                        // Update displayed votes
                        votes = newVotes;
                        app.setVotes(newVotes);
                        
                        // Show confirmation
                        showVoteConfirmation(isUpvote);
                    } else {
                        // Show error
                        showAlert("Vote Failed", "Could not submit vote (HTTP " + responseCode + ")", AlertType.ERROR);
                    }
                } catch (Exception e) {
                    showAlert("Vote Error", "Error: " + e.getMessage(), AlertType.ERROR);
                } finally {
                    try {
                        if (is != null) is.close();
                        if (connection != null) connection.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                    
                    isVoting = false;
                    repaint();
                }
            }
        };
        t.start();
    }
    
    /**
     * Parse vote response to get updated vote count
     */
    private int parseVoteResponse(String response) {
        try {
            // Simple parsing - expect a JSON response like {"votes": 42}
            return Integer.parseInt(response);
            
        } catch (Exception e) {
            System.out.println("Error parsing vote response: " + e.toString());
        }
        return votes; // Return existing vote count on error
    }
    
    /**
     * Read response from input stream
     */
    private String readResponse(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        
        while ((bytesRead = is.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        
        return new String(baos.toByteArray());
    }
    
    /**
     * Show a vote confirmation message
     */
    private void showVoteConfirmation(boolean isUpvote) {
        String title = isUpvote ? "Upvoted!" : "Downvoted!";
        String message = isUpvote ? 
            "You gave " + app.getName() + " an upvote. New score: " + votes :
            "You gave " + app.getName() + " a downvote. New score: " + votes;
        AlertType type = isUpvote ? AlertType.INFO : AlertType.WARNING;
        
        showAlert(title, message, type);
    }
    
    /**
     * Show an alert message
     */
    private void showAlert(String title, String message, AlertType type) {
        Alert alert = new Alert(title, message, null, type);
        alert.setTimeout(2000); // 2 seconds
        
        Display.getDisplay(midletStore).setCurrent(alert, this);
    }
}
