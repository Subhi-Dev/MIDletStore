import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;

/**
 * Top Charts page for MIDlet Store
 * Shows top apps by category using numpad for navigation
 */
public class TopChartsPage extends Canvas implements CommandListener {
    // Constants
    private static final int HEADER_HEIGHT = 30;
    private static final int CATEGORY_SELECTOR_HEIGHT = 45;
    private static final int PADDING = 10;
    private static final int ITEM_HEIGHT = 60;
    
    // Colors
    private static final int COLOR_BACKGROUND = 0xF0F0F0;
    private static final int COLOR_HEADER = 0xEC3750;
    private static final int COLOR_TEXT = 0x2C3E50;
    private static final int COLOR_HIGHLIGHT = 0xE83F56;
    private static final int COLOR_WHITE = 0xFFFFFF;
    private static final int COLOR_LIGHT_GRAY = 0xD3D3D3;
    private static final int COLOR_GRAY = 0x7F8C8D;
    private static final int COLOR_UPVOTE = 0x27AE60;
    private static final int COLOR_DOWNVOTE = 0xC0392B;
    
    // API URL
    private static final String TOP_CHARTS_API_URL = "http://localhost:3000/storeapi/topchart?category=";
    
    // Categories
    private static final String[] CATEGORIES = {
        "games", "utilities", "entertainment", "productivity", 
        "education", "social", "lifestyle", "finance", 
        "health", "news"
    };
    
    private static final String[] CATEGORY_NAMES = {
        "Games", "Utilities", "Entertainment", "Productivity", 
        "Education", "Social", "Lifestyle", "Finance", 
        "Health", "News"
    };
    
    // Layout
    private int width, height;
    private int contentHeight;
    private int scrollPosition = 0;
    private static final int MAX_SCROLL_SPEED = 20;
    
    // State
    private boolean isLoading = false;
    private int selectedCategoryIndex = 0;
    private int selectedAppIndex = -1;
    private int lastPointerY = 0;
    private boolean pointerPressed = false;
    
    // Chart data
    private Vector chartApps;
    private Vector appIcons;
    
    // Data references
    private MIDletStore midletStore;
    
    // Commands
    private Command backCommand;
    private Command selectCommand;
    private Command refreshCommand;
    
    /**
     * Constructor
     */
    public TopChartsPage(MIDletStore midletStore) {
        this.midletStore = midletStore;
        
        // Initialize collections
        this.chartApps = new Vector();
        this.appIcons = new Vector();
        
        // Initialize dimensions
        this.width = getWidth();
        this.height = getHeight();
        
        // Set up commands
        backCommand = new Command("Back", Command.BACK, 1);
        selectCommand = new Command("Select", Command.ITEM, 1);
        refreshCommand = new Command("Refresh", Command.ITEM, 2);
        
        addCommand(backCommand);
        addCommand(selectCommand);
        addCommand(refreshCommand);
        
        setCommandListener(this);
        
        // Load initial category
        loadCategoryApps(selectedCategoryIndex);
    }
    
    /**
     * Load apps for the selected category
     */
    private void loadCategoryApps(int categoryIndex) {
        isLoading = true;
        chartApps.removeAllElements();
        appIcons.removeAllElements();
        scrollPosition = 0;
        selectedAppIndex = -1;
        repaint();
        
        final String category = CATEGORIES[categoryIndex];
        
        Thread t = new Thread() {
            public void run() {
                HttpConnection connection = null;
                InputStream is = null;
                
                try {
                    // Create URL with category and device info
                    String url = TOP_CHARTS_API_URL + encode(category);
                    
                    // Add device information if available
                    String deviceInfo = midletStore.getDeviceQueryString();
                    if (deviceInfo != null && deviceInfo.length() > 0) {
                        url += "&" + deviceInfo;
                    }
                    
                    connection = (HttpConnection) Connector.open(url);
                    connection.setRequestProperty("User-Agent", "MIDletStore/1.0");
                    
                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpConnection.HTTP_OK) {
                        is = connection.openInputStream();
                        parseChartResults(is);
                    } else {
                        handleError("HTTP Error: " + responseCode);
                    }
                } catch (IOException ioe) {
                    handleError("Connection Error: " + ioe.getMessage());
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
     * Simple URL encoding method (J2ME doesn't have java.net.URLEncoder)
     */
    private String encode(String input) {
        if (input == null) return "";
        
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || 
                c == '-' || c == '_' || c == '.' || c == '~') {
                // These characters are allowed in a URL without encoding
                result.append(c);
            } else if (c == ' ') {
                result.append('+');
            } else {
                // Encode other characters as %XX hex values
                result.append('%');
                result.append(toHex((c >> 4) & 0xF));
                result.append(toHex(c & 0xF));
            }
        }
        return result.toString();
    }
    
    /**
     * Convert a nibble to a hex character
     */
    private char toHex(int nibble) {
        return "0123456789ABCDEF".charAt(nibble);
    }
    
    /**
     * Parse chart results from CSV response
     */
    private void parseChartResults(InputStream is) throws IOException {
        InputStreamReader reader = new InputStreamReader(is);
        StringBuffer sb = new StringBuffer();
        int c;
        
        while ((c = reader.read()) != -1) {
            if (c == '\n') {
                processChartLine(sb.toString());
                sb = new StringBuffer();
            } else {
                sb.append((char) c);
            }
        }
        
        // Process last line if any
        if (sb.length() > 0) {
            processChartLine(sb.toString());
        }
        
        // Load icons for chart apps
        loadAppIcons();
    }
    
    /**
     * Process a single line of chart results CSV
     */
    private void processChartLine(String line) {
        // Expected CSV format: id,name,description,iconUrl,downloadUrl,isFeatured,supportedStatus,votes
        String[] parts = split(line, ',');
        if (parts.length >= 8) {
            String id = parts[0];
            String name = parts[1];
            String description = parts[2];
            String iconUrl = parts[3];
            String downloadUrl = parts[4];
            boolean isFeatured = "true".equalsIgnoreCase(parts[5]);
            String supportedStatus = parts[6];
            int votes = 0;
            
            try {
                votes = Integer.parseInt(parts[7]);
            } catch (NumberFormatException e) {
                // If parsing fails, default to 0 votes
                votes = 0;
            }
            
            MIDletStore.MidletInfo info = midletStore.new MidletInfo(id, name, description, iconUrl, downloadUrl, isFeatured, supportedStatus, votes);
            
            chartApps.addElement(info);
            appIcons.addElement(null);
        }
    }
    
    /**
     * Split string by delimiter (J2ME doesn't have String.split)
     */
    private String[] split(String str, char separator) {
        Vector parts = new Vector();
        StringBuffer sb = new StringBuffer();
        
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == separator) {
                parts.addElement(sb.toString());
                sb = new StringBuffer();
            } else {
                sb.append(c);
            }
        }
        
        parts.addElement(sb.toString());
        
        String[] result = new String[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            result[i] = (String) parts.elementAt(i);
        }
        
        return result;
    }
    
    /**
     * Handle errors in loading charts
     */
    private void handleError(String message) {
        Alert alert = new Alert("Chart Error", message, null, AlertType.ERROR);
        alert.setTimeout(Alert.FOREVER);
        Display.getDisplay(midletStore).setCurrent(alert, this);
    }
    
    /**
     * Load icons for chart apps in background
     */
    private void loadAppIcons() {
        Thread t = new Thread() {
            public void run() {
                for (int i = 0; i < chartApps.size(); i++) {
                    MIDletStore.MidletInfo info = (MIDletStore.MidletInfo)chartApps.elementAt(i);
                    loadIconForApp(info, i);
                }
            }
        };
        t.start();
    }
    
    /**
     * Load icon for a chart app
     */
    private void loadIconForApp(MIDletStore.MidletInfo info, int index) {
        HttpConnection connection = null;
        InputStream is = null;
        
        try {
            // Check if the URL is valid
            if (info.getIconUrl() == null || info.getIconUrl().trim().length() == 0) {
                return;
            }
            
            connection = (HttpConnection) Connector.open(info.getIconUrl());
            connection.setRequestProperty("Accept", "image/png, image/gif, image/jpeg");
            connection.setRequestProperty("User-Agent", "MIDletStore/1.0");
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpConnection.HTTP_OK) {
                is = connection.openInputStream();
                byte[] imageData = readFully(is);
                
                if (imageData != null && imageData.length > 0) {
                    try {
                        Image icon = Image.createImage(imageData, 0, imageData.length);
                        appIcons.setElementAt(icon, index);
                        repaint();
                    } catch (Exception e) {
                        System.out.println("Error loading icon: " + e.toString());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error loading icon: " + e.toString());
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
     * Read an entire input stream into a byte array
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
     * Paint the top charts page
     */
    protected void paint(Graphics g) {
        width = getWidth();
        height = getHeight();
        
        // Background
        g.setColor(COLOR_BACKGROUND);
        g.fillRect(0, 0, width, height);
        
        // Draw header
        drawHeader(g);
        
        // Draw category selector
        drawCategorySelector(g);
        
        // Draw content
        if (isLoading) {
            drawLoadingIndicator(g);
        } else {
            drawChartList(g);
        }
    }
    
    /**
     * Draw the header bar
     */
    private void drawHeader(Graphics g) {
        g.setColor(COLOR_HEADER);
        g.fillRect(0, 0, width, HEADER_HEIGHT);
        
        g.setColor(COLOR_WHITE);
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        drawCenteredText(g, "Top Charts", width / 2, HEADER_HEIGHT / 2);
        
        // Back button in header
        g.setColor(COLOR_WHITE);
        g.fillTriangle(10, HEADER_HEIGHT / 2, 18, HEADER_HEIGHT / 2 - 8, 18, HEADER_HEIGHT / 2 + 8);
        drawText(g, "Back", 30, HEADER_HEIGHT / 2, false);
    }
    
    /**
     * Draw the category selector bar
     */
    private void drawCategorySelector(Graphics g) {
        int selectorY = HEADER_HEIGHT;
        
        // Selector background
        g.setColor(COLOR_WHITE);
        g.fillRect(0, selectorY, width, CATEGORY_SELECTOR_HEIGHT);
        
        // Draw category name
        g.setColor(COLOR_HIGHLIGHT);
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        drawCenteredText(g, CATEGORY_NAMES[selectedCategoryIndex], width / 2, selectorY + CATEGORY_SELECTOR_HEIGHT / 2 - 8);
        
        // Draw navigation hint
        g.setColor(COLOR_GRAY);
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
        drawCenteredText(g, "Press numpad keys 1-" + CATEGORIES.length + " to change category", 
                        width / 2, selectorY + CATEGORY_SELECTOR_HEIGHT - 12);
    }
    
    /**
     * Draw the app chart list
     */
    private void drawChartList(Graphics g) {
        int chartStartY = HEADER_HEIGHT + CATEGORY_SELECTOR_HEIGHT;
        
        // Apply scrolling
        g.translate(0, -scrollPosition);
        
        if (chartApps.size() == 0) {
            // No apps in this category
            g.setColor(COLOR_TEXT);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM));
            drawCenteredText(g, "No apps in this category", width / 2, chartStartY + 50);
            return;
        }
        
        // Draw apps in chart order
        int y = chartStartY + PADDING;
        for (int i = 0; i < chartApps.size(); i++) {
            MIDletStore.MidletInfo info = (MIDletStore.MidletInfo)chartApps.elementAt(i);
            
            // Calculate item rect
            int itemY = y;
            int itemHeight = ITEM_HEIGHT;
            
            // Highlight selected item
            if (i == selectedAppIndex) {
                g.setColor(COLOR_LIGHT_GRAY);
                g.fillRect(0, itemY, width, itemHeight);
            }
            
            // Draw rank number
            g.setColor(COLOR_HIGHLIGHT);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
            drawText(g, "#" + (i + 1), PADDING, itemY + itemHeight/2, false);
            
            // Draw app icon
            Image icon = null;
            if (i < appIcons.size()) {
                icon = (Image)appIcons.elementAt(i);
            }
            
            if (icon != null) {
                g.drawImage(icon, PADDING + 15, itemY + itemHeight/2, Graphics.VCENTER | Graphics.LEFT);
            } else {
                // Placeholder for icon
                g.setColor(COLOR_LIGHT_GRAY);
                g.fillRect(PADDING + 1, itemY + itemHeight/2 - 16, 32, 32);
            }
            
            // Draw app name and description
            int textX = PADDING + 30 + 40; // rank width + icon width + padding
            g.setColor(COLOR_TEXT);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
            g.drawString(info.getName(), textX, itemY + 10, Graphics.TOP | Graphics.LEFT);
            
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
            
            // Truncate description if too long
            String desc = info.getDescription();
            if (desc.length() > 40) {
              desc = desc.substring(0, 37) + "...";
            }
            g.drawString(desc, textX, itemY + 30, Graphics.TOP | Graphics.LEFT);
            
            // Draw vote count in a visible way with background
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
            int votes = info.getVotes();
            String voteText = String.valueOf(votes);
            if (votes > 0) {
                voteText = "+" + voteText;
                g.setColor(COLOR_UPVOTE);
            } else if (votes < 0) {
                g.setColor(COLOR_DOWNVOTE);
            } else {
                g.setColor(COLOR_GRAY);
            }
            
            // Draw vote count with background for better visibility
            int voteX = width - PADDING - 30;
            int voteY = itemY + itemHeight/2;
            int voteWidth = g.getFont().stringWidth(voteText) + 10;
            
            // Draw background
            g.fillRoundRect(voteX - voteWidth/2, voteY - 10, voteWidth, 20, 6, 6);
            
            // Draw text in contrasting color
            g.setColor(COLOR_WHITE);
            g.drawString(voteText, voteX - g.getFont().stringWidth(voteText)/2, voteY - g.getFont().getHeight()/2, Graphics.TOP | Graphics.LEFT);
            // Draw separator line
            g.setColor(COLOR_LIGHT_GRAY);
            g.drawLine(0, itemY + itemHeight, width, itemY + itemHeight);
            
            y += itemHeight;
        }
        
        // Calculate total content height for scrolling
        contentHeight = y - chartStartY;
        
        // Reset translation
        g.translate(0, scrollPosition);
        
        // Draw scroll indicators if needed
        if (contentHeight > (height - chartStartY)) {
            // Top indicator
            if (scrollPosition > 0) {
                g.setColor(COLOR_GRAY);
                int arrowX = width / 2;
                int arrowY = chartStartY + 10;
                g.fillTriangle(arrowX, arrowY, arrowX - 10, arrowY + 10, arrowX + 10, arrowY + 10);
            }
            
            // Bottom indicator
            if (scrollPosition < contentHeight - (height - chartStartY)) {
                g.setColor(COLOR_GRAY);
                int arrowX = width / 2;
                int arrowY = height - 10;
                g.fillTriangle(arrowX, arrowY, arrowX - 10, arrowY - 10, arrowX + 10, arrowY - 10);
            }
        }
    }
    
    /**
     * Draw loading indicator
     */
    private void drawLoadingIndicator(Graphics g) {
        int loadingY = HEADER_HEIGHT + CATEGORY_SELECTOR_HEIGHT + 50;
        
        g.setColor(COLOR_TEXT);
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        drawCenteredText(g, "Loading Top Charts...", width / 2, loadingY);
    }
    
    /**
     * Draw text centered or aligned
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
     * Draw centered text
     */
    private void drawCenteredText(Graphics g, String text, int x, int y) {
        Font font = g.getFont();
        int textWidth = font.stringWidth(text);
        int textHeight = font.getHeight();
        
        g.drawString(text, x - textWidth / 2, y - textHeight / 2, Graphics.TOP | Graphics.LEFT);
    }
    
    /**
     * Handle key events
     */
    protected void keyPressed(int keyCode) {
        int gameAction = getGameAction(keyCode);
        
        // Check for numpad keys to select category
        if (keyCode >= 49 && keyCode <= 57) { // Keys 1-9
            int categoryIndex = keyCode - 49; // 0-based index
            if (categoryIndex < CATEGORIES.length) {
                selectedCategoryIndex = categoryIndex;
                loadCategoryApps(selectedCategoryIndex);
            }
        } else if (keyCode == 48) { // Key 0 (maps to category 10)
            if (9 < CATEGORIES.length) { // 0-based index 9 for the 10th category
                selectedCategoryIndex = 9;
                loadCategoryApps(selectedCategoryIndex);
            }
        } else if (gameAction == Canvas.UP) {
            // Scroll up or move selection
            if (selectedAppIndex > 0) {
                selectedAppIndex--;
                ensureSelectedVisible();
                repaint();
            } else if (selectedAppIndex == -1 && chartApps.size() > 0) {
                selectedAppIndex = 0;
                repaint();
            } else {
                // Just scroll
                scrollPosition = Math.max(0, scrollPosition - 20);
                repaint();
            }
        } else if (gameAction == Canvas.DOWN) {
            // Scroll down or move selection
            if (selectedAppIndex < chartApps.size() - 1) {
                selectedAppIndex++;
                ensureSelectedVisible();
                repaint();
            } else {
                // Just scroll
                int maxScroll = Math.max(0, contentHeight - (height - HEADER_HEIGHT - CATEGORY_SELECTOR_HEIGHT));
                scrollPosition = Math.min(maxScroll, scrollPosition + 20);
                repaint();
            }
        } else if (gameAction == Canvas.FIRE && selectedAppIndex >= 0) {
            // Select the app
            openSelectedApp();
        } else if (gameAction == Canvas.LEFT) {
            // Previous category
            selectedCategoryIndex = (selectedCategoryIndex + CATEGORIES.length - 1) % CATEGORIES.length;
            loadCategoryApps(selectedCategoryIndex);
        } else if (gameAction == Canvas.RIGHT) {
            // Next category
            selectedCategoryIndex = (selectedCategoryIndex + 1) % CATEGORIES.length;
            loadCategoryApps(selectedCategoryIndex);
        }
    }
    
    /**
     * Command action handler
     */
    public void commandAction(Command c, Displayable d) {
        if (c == backCommand) {
            // Return to store canvas
            midletStore.showStoreCanvas();
        } else if (c == selectCommand && selectedAppIndex >= 0) {
            // Select the app
            openSelectedApp();
        } else if (c == refreshCommand) {
            // Reload the current category
            loadCategoryApps(selectedCategoryIndex);
        }
    }
    
    /**
     * Open the details view for the selected app
     */
    private void openSelectedApp() {
        if (selectedAppIndex >= 0 && selectedAppIndex < chartApps.size()) {
            MIDletStore.MidletInfo selected = (MIDletStore.MidletInfo)chartApps.elementAt(selectedAppIndex);
            
            // Find icon
            Image icon = null;
            if (selectedAppIndex < appIcons.size()) {
                icon = (Image)appIcons.elementAt(selectedAppIndex);
            }
            
            // Show details
            midletStore.showAppDetails(selected, icon);
        }
    }
    
    /**
     * Ensure the selected app is visible by scrolling if needed
     */
    private void ensureSelectedVisible() {
        if (selectedAppIndex < 0) return;
        
        int chartStartY = HEADER_HEIGHT + CATEGORY_SELECTOR_HEIGHT + PADDING;
        int itemTop = chartStartY + selectedAppIndex * ITEM_HEIGHT;
        int itemBottom = itemTop + ITEM_HEIGHT;
        
        if (itemTop - scrollPosition < chartStartY) {
            // Item is above visible area - scroll up
            scrollPosition = itemTop - chartStartY;
        } else if (itemBottom - scrollPosition > height) {
            // Item is below visible area - scroll down
            scrollPosition = itemBottom - height;
        }
        
        // Ensure scroll is within bounds
        if (scrollPosition < 0) scrollPosition = 0;
        int maxScroll = Math.max(0, contentHeight - (height - HEADER_HEIGHT - CATEGORY_SELECTOR_HEIGHT));
        if (scrollPosition > maxScroll) scrollPosition = maxScroll;
    }
    
    /**
     * Handle pointer pressed events
     */
    protected void pointerPressed(int x, int y) {
        pointerPressed = true;
        lastPointerY = y;
        
        // Check if an app was clicked
        if (!isLoading && chartApps.size() > 0) {
            int chartStartY = HEADER_HEIGHT + CATEGORY_SELECTOR_HEIGHT + PADDING;
            int clickedY = y + scrollPosition;
            
            if (clickedY >= chartStartY) {
                int clickedIndex = (clickedY - chartStartY) / ITEM_HEIGHT;
                if (clickedIndex >= 0 && clickedIndex < chartApps.size()) {
                    if (selectedAppIndex == clickedIndex) {
                        // Double click - open the app
                        openSelectedApp();
                    } else {
                        // Single click - select the app
                        selectedAppIndex = clickedIndex;
                        repaint();
                    }
                }
            }
        }
    }
    
    /**
     * Handle pointer dragged for scrolling
     */
    protected void pointerDragged(int x, int y) {
        if (!pointerPressed) return;
        
        int delta = lastPointerY - y;
        lastPointerY = y;
        
        // Ignore small movements
        if (Math.abs(delta) < 2) return;
        
        // Update scroll position
        scrollPosition += delta;
        
        // Keep within bounds
        if (scrollPosition < 0) scrollPosition = 0;
        
        int chartStartY = HEADER_HEIGHT + CATEGORY_SELECTOR_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - (height - chartStartY));
        if (scrollPosition > maxScroll) scrollPosition = maxScroll;
        
        repaint();
    }
    
    /**
     * Handle pointer released
     */
    protected void pointerReleased(int x, int y) {
        pointerPressed = false;
    }
    
    /**
     * Reset state when shown
     */
    public void reset() {
        // Don't reload if we have content
        if (chartApps.size() == 0) {
            loadCategoryApps(selectedCategoryIndex);
        }
    }
}
