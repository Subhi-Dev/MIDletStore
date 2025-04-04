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
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;

/**
 * Search page for MIDlet Store
 * Allows users to search for apps by name, developer, or category
 */
public class SearchPage extends Canvas implements CommandListener {
    // Constants
    private static final int HEADER_HEIGHT = 30;
    private static final int PADDING = 10;
    private static final int SEARCH_BOX_HEIGHT = 30;
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
    
    // Layout
    private int width, height;
    private int contentHeight;
    private int scrollPosition = 0;
    private static final int MAX_SCROLL_SPEED = 20;
    
    // State
    private boolean isSearching = false;
    private String searchQuery = "";
    private int selectedResultIndex = -1;
    private int lastPointerY = 0;
    private boolean pointerPressed = false;
    private boolean searchBoxActive = false;
    
    // API URL
    private static final String SEARCH_API_URL = "http://localhost:3000/storeapi/search?q=";
    
    // Search results
    private Vector searchResults;
    private Vector resultIcons;
    private MIDletStore.MidletInfo selectedMidlet;
    
    // Data references
    private MIDletStore midletStore;
    private Vector midletsList;
    
    // Commands
    private Command backCommand;
    private Command searchCommand;
    private Command selectCommand;
    private Command clearCommand;
    private Command inputCommand;
    
    /**
     * Constructor
     */
    public SearchPage(MIDletStore midletStore, Vector midletsList) {
        this.midletStore = midletStore;
        this.midletsList = midletsList;
        
        // Initialize collections
        this.searchResults = new Vector();
        this.resultIcons = new Vector();
        
        // Initialize dimensions
        this.width = getWidth();
        this.height = getHeight();
        
        // Set up commands
        backCommand = new Command("Back", Command.BACK, 1);
        searchCommand = new Command("Search", Command.OK, 1);
        selectCommand = new Command("Select", Command.ITEM, 1);
        clearCommand = new Command("Clear", Command.ITEM, 2);
        inputCommand = new Command("Input", Command.ITEM, 3);
        
        this.addCommand(backCommand);
        this.addCommand(searchCommand);
        this.addCommand(clearCommand);
        this.addCommand(inputCommand);
        
        // For devices with physical keyboards
        setCommandListener(this);
    }
    
    /**
     * Process search query and display results
     */
    private void performSearch() {
        if (searchQuery == null || searchQuery.trim().length() == 0) {
            // Empty search, don't perform
            Alert alert = new Alert("Error", "Please enter a search term", null, AlertType.WARNING);
            alert.setTimeout(2000);
            Display.getDisplay(midletStore).setCurrent(alert, this);
            return;
        }
        
        isSearching = true;
        searchResults.removeAllElements();
        resultIcons.removeAllElements();
        scrollPosition = 0;
        repaint();
        
        // Start search in background thread
        Thread t = new Thread() {
            public void run() {
                HttpConnection connection = null;
                InputStream is = null;
                
                try {
                    // Create URL with search term and device info
                    String url = SEARCH_API_URL + encode(searchQuery);
                    
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
                        parseSearchResults(is);
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
                    
                    isSearching = false;
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
     * Parse search results from CSV response
     */
    private void parseSearchResults(InputStream is) throws IOException {
        InputStreamReader reader = new InputStreamReader(is);
        StringBuffer sb = new StringBuffer();
        int c;
        
        while ((c = reader.read()) != -1) {
            if (c == '\n') {
                processResultLine(sb.toString());
                sb = new StringBuffer();
            } else {
                sb.append((char) c);
            }
        }
        
        // Process last line if any
        if (sb.length() > 0) {
            processResultLine(sb.toString());
        }
        
        // Load icons for search results
        loadResultIcons();
    }
    
    /**
     * Process a single line of search results CSV
     */
    private void processResultLine(String line) {
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
            
            searchResults.addElement(info);
            resultIcons.addElement(null);
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
     * Handle errors in search
     */
    private void handleError(String message) {
        Alert alert = new Alert("Search Error", message, null, AlertType.ERROR);
        alert.setTimeout(Alert.FOREVER);
        Display.getDisplay(midletStore).setCurrent(alert, this);
    }
    
    /**
     * Load icons for search results in background
     */
    private void loadResultIcons() {
        Thread t = new Thread() {
            public void run() {
                for (int i = 0; i < searchResults.size(); i++) {
                    MIDletStore.MidletInfo info = (MIDletStore.MidletInfo)searchResults.elementAt(i);
                    loadIconForResult(info, i);
                }
            }
        };
        t.start();
    }
    
    /**
     * Load icon for a search result
     */
    private void loadIconForResult(MIDletStore.MidletInfo info, int index) {
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
                        resultIcons.setElementAt(icon, index);
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
     * Paint the search page
     */
    protected void paint(Graphics g) {
        width = getWidth();
        height = getHeight();
        
        // Background
        g.setColor(COLOR_BACKGROUND);
        g.fillRect(0, 0, width, height);
        
        // Draw header
        drawHeader(g);
        
        // Draw search box
        drawSearchBox(g);
        
        // Draw results
        if (isSearching) {
            drawSearchingIndicator(g);
        } else {
            drawSearchResults(g);
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
        drawCenteredText(g, "Search Apps", width / 2, HEADER_HEIGHT / 2);
        
        // Back button in header
        g.setColor(COLOR_WHITE);
        g.fillTriangle(10, HEADER_HEIGHT / 2, 18, HEADER_HEIGHT / 2 - 8, 18, HEADER_HEIGHT / 2 + 8);
        drawText(g, "Back", 30, HEADER_HEIGHT / 2, false);
    }
    
    /**
     * Draw the search input box
     */
    private void drawSearchBox(Graphics g) {
        int searchBoxY = HEADER_HEIGHT + PADDING;
        
        // Search box background
        g.setColor(COLOR_WHITE);
        g.fillRect(PADDING, searchBoxY, width - PADDING * 2 - 70, SEARCH_BOX_HEIGHT);
        
        // Search box border - highlight if active
        if (searchBoxActive) {
            g.setColor(COLOR_HIGHLIGHT);
            g.drawRect(PADDING, searchBoxY, width - PADDING * 2 - 70, SEARCH_BOX_HEIGHT);
        } else {
            g.setColor(COLOR_LIGHT_GRAY);
            g.drawRect(PADDING, searchBoxY, width - PADDING * 2 - 70, SEARCH_BOX_HEIGHT);
        }
        
        // Search text
        g.setColor(COLOR_TEXT);
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM));
        
        String displayText = searchQuery;
        if (displayText.length() == 0) {
            g.setColor(COLOR_GRAY);
            displayText = "Search for apps...";
        }
        
        g.drawString(displayText, PADDING * 2, searchBoxY + SEARCH_BOX_HEIGHT/2 - g.getFont().getHeight()/2, 
                    Graphics.LEFT | Graphics.TOP);
        
        // Draw blinking cursor if searchBoxActive
        if (searchBoxActive && System.currentTimeMillis() % 1000 < 500) {
            g.setColor(COLOR_HIGHLIGHT);
            int cursorX = PADDING * 2;
            if (searchQuery.length() > 0) {
                cursorX += g.getFont().stringWidth(searchQuery);
            }
            g.drawLine(cursorX, searchBoxY + 5, cursorX, searchBoxY + SEARCH_BOX_HEIGHT - 5);
        }
        
        // Search button
        g.setColor(COLOR_HIGHLIGHT);
        g.fillRoundRect(width - PADDING - 60, searchBoxY, 60, SEARCH_BOX_HEIGHT, 5, 5);
        g.setColor(COLOR_WHITE);
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
        drawText(g, "Search", width - PADDING - 30, searchBoxY + SEARCH_BOX_HEIGHT/2, true);
    }
    
    /**
     * Draw search results list
     */
    private void drawSearchResults(Graphics g) {
        int resultsStartY = HEADER_HEIGHT + PADDING + SEARCH_BOX_HEIGHT + PADDING;
        
        // Apply scrolling
        g.translate(0, -scrollPosition);
        
        if (searchResults.size() == 0) {
            // No results
            g.setColor(COLOR_TEXT);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM));
            drawCenteredText(g, searchQuery.length() > 0 ? "No matching apps found" : "Enter a search term above", 
                            width / 2, resultsStartY + 40);
            drawCenteredText(g, searchQuery.length() > 0 ? "" : "or use the Input command", 
                            width / 2, resultsStartY + 50);
                          
            return;
        }
        
        // Draw result items
        int y = resultsStartY;
        for (int i = 0; i < searchResults.size(); i++) {
            MIDletStore.MidletInfo info = (MIDletStore.MidletInfo)searchResults.elementAt(i);
            
            // Calculate item rect
            int itemY = y;
            int itemHeight = ITEM_HEIGHT;
            
            // Highlight selected item
            if (i == selectedResultIndex) {
                g.setColor(COLOR_LIGHT_GRAY);
                g.fillRect(0, itemY, width, itemHeight);
            }
            
            // Draw app icon
            Image icon = null;
            if (i < resultIcons.size()) {
                icon = (Image)resultIcons.elementAt(i);
            }
            
            if (icon != null) {
                g.drawImage(icon, PADDING, itemY + itemHeight/2, Graphics.VCENTER | Graphics.LEFT);
            } else {
                // Placeholder for icon
                g.setColor(COLOR_LIGHT_GRAY);
                g.fillRect(PADDING, itemY + 5, 32, 32);
            }
            
            // Draw app name and description
            int textX = PADDING * 2 + 40; // icon width + padding
            g.setColor(COLOR_TEXT);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
            g.drawString(info.getName(), textX, itemY + 10, Graphics.TOP | Graphics.LEFT);
            
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
            
            // Truncate description if too long
            String desc = info.getDescription();
            if (desc.length() > 50) {
                desc = desc.substring(0, 47) + "...";
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
        contentHeight = y - resultsStartY;
        
        // Reset translation
        g.translate(0, scrollPosition);
        
        // Draw scroll indicators if needed
        if (contentHeight > (height - resultsStartY)) {
            // Top indicator
            if (scrollPosition > 0) {
                g.setColor(COLOR_GRAY);
                int arrowX = width / 2;
                int arrowY = resultsStartY + 10;
                g.fillTriangle(arrowX, arrowY, arrowX - 10, arrowY + 10, arrowX + 10, arrowY + 10);
            }
            
            // Bottom indicator
            if (scrollPosition < contentHeight - (height - resultsStartY)) {
                g.setColor(COLOR_GRAY);
                int arrowX = width / 2;
                int arrowY = height - 10;
                g.fillTriangle(arrowX, arrowY, arrowX - 10, arrowY - 10, arrowX + 10, arrowY - 10);
            }
        }
    }
    
    /**
     * Draw searching indicator
     */
    private void drawSearchingIndicator(Graphics g) {
        int centerY = (height - HEADER_HEIGHT - SEARCH_BOX_HEIGHT) / 2 + HEADER_HEIGHT + SEARCH_BOX_HEIGHT;
        
        g.setColor(COLOR_TEXT);
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        drawCenteredText(g, "Searching...", width / 2, centerY);
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
     * Show text input for search query
     * This opens a system text input dialog specific to the device
     */
    private void showTextInput() {
        // Create the text input form
        Display display = Display.getDisplay(midletStore);
        TextInputForm inputForm = new TextInputForm("Search", searchQuery, this);
        display.setCurrent(inputForm);
    }
    
    /**
     * Set the search query from the text input
     */
    public void setSearchQuery(String query) {
        searchQuery = query;
        searchBoxActive = false;
        repaint();
    }
    
    /**
     * Handle returning from text input form
     */
    public void returnToSearchPage(Displayable current) {
        if (midletStore != null) {
            Display.getDisplay(midletStore).setCurrent(this);
        }
        searchBoxActive = false;
        performSearch();
    }
    
    /**
     * Handle key events
     */
    protected void keyPressed(int keyCode) {
        int gameAction = getGameAction(keyCode);
        
        if (searchBoxActive) {
            // If search box is active, pressing FIRE should start search
            if (gameAction == Canvas.FIRE) {
                searchBoxActive = false;
                performSearch();
                return;
            }
        } else {
            // Handle results navigation
            if (gameAction == Canvas.UP) {
                if (selectedResultIndex > 0) {
                    selectedResultIndex--;
                    ensureSelectedVisible();
                    repaint();
                } else if (selectedResultIndex == -1 && searchResults.size() > 0) {
                    selectedResultIndex = 0;
                    repaint();
                }
            } else if (gameAction == Canvas.DOWN) {
                if (selectedResultIndex < searchResults.size() - 1) {
                    selectedResultIndex++;
                    ensureSelectedVisible();
                    repaint();
                }
            } else if (gameAction == Canvas.FIRE && selectedResultIndex >= 0) {
                // View selected app
                openSelectedApp();
            }
        }
    }
    
    /**
     * Command action handler
     */
    public void commandAction(Command c, Displayable d) {
        if (c == backCommand) {
            // Return to store canvas
            midletStore.showStoreCanvas();
        } else if (c == searchCommand) {
            // Start search
            searchBoxActive = false;
            performSearch();
        } else if (c == clearCommand) {
            // Clear search query
            searchQuery = "";
            repaint();
        } else if (c == inputCommand) {
            // Open text input dialog
            showTextInput();
        } else if (c == selectCommand && selectedResultIndex >= 0) {
            // View selected app
            openSelectedApp();
        }
    }
    
    /**
     * Open the details view for the selected app
     */
    private void openSelectedApp() {
        if (selectedResultIndex >= 0 && selectedResultIndex < searchResults.size()) {
            MIDletStore.MidletInfo selected = (MIDletStore.MidletInfo)searchResults.elementAt(selectedResultIndex);
            
            // Find icon
            Image icon = null;
            if (selectedResultIndex < resultIcons.size()) {
                icon = (Image)resultIcons.elementAt(selectedResultIndex);
            }
            
            // Show details
            midletStore.showAppDetails(selected, icon);
        }
    }
    
    /**
     * Ensure the selected result is visible by scrolling if needed
     */
    private void ensureSelectedVisible() {
        if (selectedResultIndex < 0) return;
        
        int resultsStartY = HEADER_HEIGHT + PADDING + SEARCH_BOX_HEIGHT + PADDING;
        int itemTop = resultsStartY + selectedResultIndex * ITEM_HEIGHT;
        int itemBottom = itemTop + ITEM_HEIGHT;
        
        if (itemTop - scrollPosition < resultsStartY) {
            // Item is above visible area - scroll up
            scrollPosition = itemTop - resultsStartY;
        } else if (itemBottom - scrollPosition > height) {
            // Item is below visible area - scroll down
            scrollPosition = itemBottom - height;
        }
        
        // Ensure scroll is within bounds
        if (scrollPosition < 0) scrollPosition = 0;
        int maxScroll = Math.max(0, contentHeight - (height - resultsStartY));
        if (scrollPosition > maxScroll) scrollPosition = maxScroll;
    }
    
    /**
     * Handle pointer pressed events
     */
    protected void pointerPressed(int x, int y) {
        pointerPressed = true;
        lastPointerY = y;
        
        // Check if search box was clicked
        int searchBoxY = HEADER_HEIGHT + PADDING;
        if (y >= searchBoxY && y <= searchBoxY + SEARCH_BOX_HEIGHT) {
            if (x >= width - PADDING - 60 && x <= width - PADDING) {
                // Search button clicked
                searchBoxActive = false;
                performSearch();
            } else if (x >= PADDING && x <= width - PADDING * 2 - 70) {
                // Search text field clicked - show text input
                showTextInput();
            }
            return;
        }
        
        // Check if a search result was clicked
        if (searchResults.size() > 0) {
            int resultsStartY = HEADER_HEIGHT + PADDING + SEARCH_BOX_HEIGHT + PADDING;
            int clickedY = y + scrollPosition;
            
            if (clickedY >= resultsStartY) {
                int clickedIndex = (clickedY - resultsStartY) / ITEM_HEIGHT;
                if (clickedIndex >= 0 && clickedIndex < searchResults.size()) {
                    if (selectedResultIndex == clickedIndex) {
                        // Double click - open the app
                        openSelectedApp();
                    } else {
                        // Single click - select the app
                        selectedResultIndex = clickedIndex;
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
        
        int resultsStartY = HEADER_HEIGHT + PADDING + SEARCH_BOX_HEIGHT + PADDING;
        int maxScroll = Math.max(0, contentHeight - (height - resultsStartY));
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
     * Reset search state
     */
    public void reset() {
        searchQuery = "";
        searchBoxActive = false;
        selectedResultIndex = -1;
        scrollPosition = 0;
        searchResults.removeAllElements();
        resultIcons.removeAllElements();
        repaint();
    }
}
