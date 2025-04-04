import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Vector;

import javax.microedition.midlet.MIDlet;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import javax.microedition.m2g.SVGImage;
import javax.microedition.m2g.SVGAnimator;
import javax.microedition.m2g.ScalableImage;

import com.sun.svg.component.LoadingScreen;
import com.sun.svg.util.DefaultSVGAnimator;

public class MIDletStore extends MIDlet implements CommandListener {
    private Display display;
    private StoreCanvas storeCanvas;
    private Vector midletsList;
    private Vector featuredApps;
    private Command exitCommand;
    private Command selectCommand;
    private Command backCommand;
    private boolean isLoading;
    private String csvUrl = "http://localhost:3000/storeapi"; // Default URL, can be configured
    private static final String CLIENT_VERSION = "1.0"; // Add client version constant
    private AppDetailView appDetailView;
    private String deviceId = null;
    private String supportedAPIs = null;
    private SearchPage searchPage;
    private Vector appIcons; // Store app icons
    private TopChartsPage topChartsPage;

    public void startApp() {
        display = Display.getDisplay(this);

        if (storeCanvas == null) {
            // Initialize device information
            initDeviceInfo();

            // Initialize app icons vector
            appIcons = new Vector();

            // Initialize commands
            exitCommand = new Command("Exit", Command.EXIT, 1);
            selectCommand = new Command("Select", Command.ITEM, 1);
            backCommand = new Command("Back", Command.BACK, 1);

            // Show loading screen
            showLoadingScreen();

            // Check version first, then load MIDlets data
            checkVersion();
        } else {
            display.setCurrent(storeCanvas);
        }
    }

    public void pauseApp() {
        // Nothing to do
    }

    public void destroyApp(boolean unconditional) {
        // Clean up resources
    }

    public void commandAction(Command c, Displayable d) {
        if (c == exitCommand) {
            destroyApp(true);
            notifyDestroyed();
        } else if (c == selectCommand && d == storeCanvas) {
            MidletInfo selected = storeCanvas.getSelectedMidlet();
            if (selected != null) {
                showAppDetails(selected, null);
            }
        } else if (c == backCommand || 
                  (c.getCommandType() == Command.BACK && d instanceof AppDetailView)) {
            // Fix back button handling from app details
            display.setCurrent(storeCanvas);
        } else if (c.getCommandType() == Command.ITEM && d instanceof AppDetailView) {
        // Handle commands from AppDetailView
            AppDetailView detailView = (AppDetailView)d;
            
            if (c.getLabel().equals("Install")) {
                // Install app
                MidletInfo appInfo = detailView.getAppInfo();
                if (appInfo != null) {
                    installMidlet(appInfo);
                }
            } else if (c.getLabel().equals("Upvote")) {
                // Let AppDetailView handle the upvote
                detailView.commandAction(c, d);
            } else if (c.getLabel().equals("Downvote")) {
                // Let AppDetailView handle the downvote
                detailView.commandAction(c, d);
            }
        } else if (c.getCommandType() == Command.ITEM && c.getLabel().equals("Search") && d == storeCanvas) {
            // Show search page
            showSearchPage();
        } else if (c.getCommandType() == Command.ITEM && c.getLabel().equals("Top Charts") && d == storeCanvas) {
            // Show top charts page
            showTopChartsPage();
        }
    }

    /**
     * Check the current app version against server version
     * This is the first network request made by the app
     */
    private void checkVersion() {
        Thread t = new Thread() {
            public void run() {
                HttpConnection connection = null;
                InputStream is = null;

                try {
                    // Build version check URL with device info
                    String versionUrl = csvUrl + "/version";

                    connection = (HttpConnection) Connector.open(versionUrl);
                    connection.setRequestProperty("User-Agent", "MIDletStore/" + CLIENT_VERSION);
                    
                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpConnection.HTTP_OK) {
                        is = connection.openInputStream();
                        String serverVersion = readResponseAsString(is);
                        
                        // Compare versions
                        if (isNewVersionAvailable(CLIENT_VERSION, serverVersion)) {
                            // Prompt for upgrade
                            promptForUpgrade(serverVersion);
                        } else {
                            // Continue with app loading
                            loadMidletsFromCSV();
                        }
                    } else {
                        // Error checking version, just continue
                        System.out.println("Error checking version: HTTP " + responseCode);
                        loadMidletsFromCSV();
                    }
                } catch (IOException ioe) {
                    // Error checking version, just continue
                    System.out.println("Error checking version: " + ioe.getMessage());
                    loadMidletsFromCSV();
                } finally {
                    try {
                        if (is != null) is.close();
                        if (connection != null) connection.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            }
        };
        t.start();
    }
    
    /**
     * Determine if a new version is available by comparing version strings
     */
    private boolean isNewVersionAvailable(String clientVersion, String serverVersion) {
        // Parse version strings (assuming format like "1.0", "1.1", "2.0", etc.)
        try {
            // Split version into major and minor components
            int[] clientParts = parseVersionParts(clientVersion);
            int[] serverParts = parseVersionParts(serverVersion);
            
            // Compare major version first
            if (serverParts[0] > clientParts[0]) {
                return true;
            } else if (serverParts[0] == clientParts[0]) {
                // If major versions are equal, compare minor version
                return serverParts[1] > clientParts[1];
            }
            
            return false;
        } catch (Exception e) {
            // If parsing fails, assume no new version
            System.out.println("Error parsing version: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Parse version string into major and minor components
     */
    private int[] parseVersionParts(String version) {
        int[] parts = {0, 0}; // Default [major, minor]
        
        if (version != null && version.length() > 0) {
            int dotIndex = version.indexOf('.');
            if (dotIndex > 0 && dotIndex < version.length() - 1) {
                // Has both major and minor parts
                try {
                    parts[0] = Integer.parseInt(version.substring(0, dotIndex));
                    parts[1] = Integer.parseInt(version.substring(dotIndex + 1));
                } catch (NumberFormatException e) {
                    // Use defaults
                }
            } else {
                // Just a single number
                try {
                    parts[0] = Integer.parseInt(version);
                } catch (NumberFormatException e) {
                    // Use defaults
                }
            }
        }
        
        return parts;
    }
    
    /**
     * Prompt the user to upgrade the app
     */
    private void promptForUpgrade(final String newVersion) {
        final Command upgradeCommand = new Command("Upgrade", Command.OK, 1);
        final Command skipCommand = new Command("Skip", Command.CANCEL, 2);
        
        final Alert upgradeAlert = new Alert("Update Available", 
            "A new version (" + newVersion + ") of the MIDlet Store is available. " +
            "Would you like to upgrade now?", null, AlertType.INFO);
        
        upgradeAlert.addCommand(upgradeCommand);
        upgradeAlert.addCommand(skipCommand);
        upgradeAlert.setTimeout(Alert.FOREVER);
        
        upgradeAlert.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c == upgradeCommand) {
                    // User chose to upgrade - open download URL
                    try {
                        // Construct upgrade URL
                        String upgradeUrl = csvUrl + "/download?version=" + encode(newVersion);
                        
                        if (platformRequest(upgradeUrl)) {
                            // Platform request will terminate this app
                            destroyApp(false);
                            notifyDestroyed();
                        } else {
                            // Continue normal app flow
                            loadMidletsFromCSV();
                        }
                    } catch (Exception e) {
                        // Handle error in upgrade process
                        Alert errorAlert = new Alert("Error", 
                            "Failed to start upgrade: " + e.getMessage(), 
                            null, AlertType.ERROR);
                        errorAlert.setTimeout(2000);
                        display.setCurrent(errorAlert);
                        
                        // Continue with normal app flow
                        loadMidletsFromCSV();
                    }
                } else {
                    // User skipped upgrade, continue with normal app flow
                    loadMidletsFromCSV();
                }
            }
        });
        
        display.setCurrent(upgradeAlert);
    }
    
    /**
     * Read response stream as a string
     */
    private String readResponseAsString(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        
        while ((bytesRead = is.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        
        return baos.toString().trim();
    }

    /**
     * Initialize device information including unique ID and supported JSRs
     */
    private void initDeviceInfo() {
        // Get unique device identifier
        // Try several common system properties to find a suitable device ID
        deviceId = getSystemProperty("com.nokia.mid.imei"); // Nokia IMEI

        if (deviceId == null || deviceId.length() == 0) {
            deviceId = getSystemProperty("com.sonyericsson.imei"); // Sony Ericsson IMEI
        }

        if (deviceId == null || deviceId.length() == 0) {
            deviceId = getSystemProperty("phone.imei"); // Generic IMEI
        }

        if (deviceId == null || deviceId.length() == 0) {
            deviceId = getSystemProperty("com.motorola.IMEI"); // Motorola IMEI
        }

        if (deviceId == null || deviceId.length() == 0) {
            deviceId = getSystemProperty("device.id"); // Generic device ID
        }

        if (deviceId == null || deviceId.length() == 0) {
            deviceId = getSystemProperty("microedition.platform"); // Platform info as fallback
        }

        if (deviceId == null || deviceId.length() == 0) {
            deviceId = "unknown"; // Last resort fallback
        }

        // Build list of supported JSR APIs
        StringBuffer apiBuffer = new StringBuffer();

        // Check for common JSRs (Mobile Service Architecture specifications)
        checkJSR(apiBuffer, "JSR30", "javax.microedition.io.file.FileConnection"); // File Connection API
        checkJSR(apiBuffer, "JSR75", "javax.microedition.io.file.FileConnection"); // PDA Optional Packages
        checkJSR(apiBuffer, "JSR82", "javax.bluetooth.LocalDevice"); // Bluetooth API
        checkJSR(apiBuffer, "JSR118", "javax.microedition.midlet.MIDlet"); // MIDP 2.0
        checkJSR(apiBuffer, "JSR120", "javax.wireless.messaging.Message"); // Wireless Messaging API
        checkJSR(apiBuffer, "JSR135", "javax.microedition.media.Player"); // Mobile Media API
        checkJSR(apiBuffer, "JSR172", "javax.xml.parsers.SAXParser"); // Web Services
        checkJSR(apiBuffer, "JSR177", "javax.microedition.securityservice.CMSMessageSignatureService"); // Security and Trust
        checkJSR(apiBuffer, "JSR179", "javax.microedition.location.Location"); // Location API
        checkJSR(apiBuffer, "JSR180", "javax.microedition.sip.SipConnection"); // SIP API
        checkJSR(apiBuffer, "JSR184", "javax.microedition.m3g.Graphics3D"); // Mobile 3D Graphics
        checkJSR(apiBuffer, "JSR205", "javax.wireless.messaging.MessageConnection"); // Wireless Messaging API 2.0
        checkJSR(apiBuffer, "JSR211", "javax.microedition.content.Invocation"); // Content Handler API
        checkJSR(apiBuffer, "JSR226", "javax.microedition.m2g.SVGImage"); // Scalable 2D Vector Graphics
        checkJSR(apiBuffer, "JSR234", "javax.microedition.amms.control.audioeffect.EqualizerControl"); // Advanced Multimedia Supplements
        checkJSR(apiBuffer, "JSR238", "javax.microedition.global.Formatter"); // Mobile Internationalization API
        checkJSR(apiBuffer, "JSR239", "javax.microedition.khronos.egl.EGL"); // Java Binding for OpenGL ES
        checkJSR(apiBuffer, "JSR256", "javax.microedition.sensor.SensorConnection"); // Mobile Sensor API
        checkJSR(apiBuffer, "JSR280", "javax.microedition.contactless.ContactlessException"); // Contactless Communication API
        checkJSR(apiBuffer, "JSR281", "javax.microedition.contactless.ndef.NDEFMessage"); // IMS Services API
        checkJSR(apiBuffer, "JSR293", "javax.microedition.location.LandmarkStore"); // Location API 2.0

        // Store detected JSRs
        supportedAPIs = apiBuffer.toString();
        if (supportedAPIs.length() > 0) {
            // Remove trailing comma
            supportedAPIs = supportedAPIs.substring(0, supportedAPIs.length() - 1);
        } else {
            supportedAPIs = "none";
        }

        System.out.println("Device ID: " + deviceId);
        System.out.println("Supported APIs: " + supportedAPIs);
    }

    /**
     * Check if a specific JSR is supported and add it to the buffer if found
     */
    private void checkJSR(StringBuffer buffer, String jsrName, String testClass) {
        try {
            Class.forName(testClass);
            buffer.append(jsrName).append(",");
        } catch (ClassNotFoundException e) {
            // JSR not supported, do nothing
        } catch (Exception e) {
            // Other error, assume not supported
        }
    }

    /**
     * Safely get a system property with fallback to empty string
     */
    private String getSystemProperty(String key) {
        try {
            return System.getProperty(key);
        } catch (SecurityException e) {
            // Access to the property might be restricted
            return "";
        }
    }

    /**
     * Get device information as a query string
     */
    public String getDeviceQueryString() {
        if (deviceId != null && supportedAPIs != null) {
            return "device=" + encode(deviceId) + "&jsrs=" + encode(supportedAPIs);
        }
        return "";
    }

    private void showLoadingScreen() {
        isLoading = true;
        try {
            InputStream is = getClass().getResourceAsStream("/loading.svg");
            if (is == null) {
                destroyApp(false);
                notifyDestroyed();
                return;
            }
            SVGImage loadingImage;
            try {
                try {
                    loadingImage = 
                            (SVGImage)SVGImage.createImage(is, null);
                } finally {
                    try {
                        is.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            } catch (IOException e) {
                destroyApp(false);
                notifyDestroyed();
                return;
            }
            if (is != null) {
                //ScalableImage svgImage = ScalableImage.createImage(is, null);
                SVGAnimator animator = SVGAnimator.createAnimator(loadingImage);
                Displayable loadingCanvas = (Displayable)animator.getTargetComponent();
                display.setCurrent(loadingCanvas);
                animator.play(); // Ensure animations play
                is.close();
            } else {
                // Fallback if SVG loading fails
                LoadingScreen loadingScreen = new LoadingScreen("Hack MIDlet Store", "Loading Applications...");
                display.setCurrent(loadingScreen);
            }
        } catch (IOException ioe) {
            // If SVG loading fails, fallback to LoadingScreen
            LoadingScreen loadingScreen = new LoadingScreen();
            display.setCurrent(loadingScreen);
        }
    }

    private void loadMidletsFromCSV() {
        midletsList = new Vector();
        featuredApps = new Vector();

        Thread t = new Thread() {
            public void run() {
                HttpConnection connection = null;
                InputStream is = null;

                try {
                    // Append device info and supported JSRs to request URL
                    String registerUrl = csvUrl + "/register";
                    if (registerUrl.indexOf('?') >= 0) {
                        registerUrl += "&device=" + encode(deviceId) + "&jsrs=" + encode(supportedAPIs);
                    } else {
                        registerUrl += "?device=" + encode(deviceId) + "&jsrs=" + encode(supportedAPIs);
                    }
                    String apiUrl = csvUrl + "/apps";
                    if (apiUrl.indexOf('?') >= 0) {
                        apiUrl += "&device=" + encode(deviceId);
                    } else {
                        apiUrl += "?device=" + encode(deviceId);
                    }
                    connection = (HttpConnection) Connector.open(registerUrl);
                    if (connection.getResponseCode() == HttpConnection.HTTP_OK) {
                        is = connection.openInputStream();
                    } else {
                        handleError("HTTP Error: " + connection.getResponseCode());
                    }
                    if (is != null) is.close();
                    if (connection != null) connection.close();

                    connection = (HttpConnection) Connector.open(apiUrl);
                    if (connection.getResponseCode() == HttpConnection.HTTP_OK) {
                        is = connection.openInputStream();
                        parseCsvData(is);
                        initializeUI();
                    } else {
                        handleError("HTTP Error: " + connection.getResponseCode());
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

    private void parseCsvData(InputStream is) throws IOException {
        InputStreamReader isr = new InputStreamReader(is);
        StringBuffer sb = new StringBuffer();
        int c;

        while ((c = isr.read()) != -1) {
            if (c == '\n') {
                processCsvLine(sb.toString());
                sb = new StringBuffer();
            } else {
                sb.append((char) c);
            }
        }

        // Process last line if any
        if (sb.length() > 0) {
            processCsvLine(sb.toString());
        }
    }

    private void processCsvLine(String line) {
        // Expected CSV format: id,name,description,iconUrl,downloadUrl,isFeatured,supportedStatus,votes
        String[] parts = split(line, ',');
        if (parts.length >= 8) {  // Updated to check for at least 8 fields including votes
            String id = parts[0];
            String name = parts[1];
            String description = parts[2];
            String iconUrl = parts[3];
            String downloadUrl = parts[4];
            boolean isFeatured = "true".equalsIgnoreCase(parts[5]);
            String supportedStatus = parts[6];
            int votes = 0;

            try {
                votes = Integer.parseInt(parts[7]); // Parse votes as integer
            } catch (NumberFormatException e) {
                // If parsing fails, default to 0 votes
                votes = 0;
            }

            MidletInfo info = new MidletInfo(id, name, description, iconUrl, downloadUrl, 
                                              isFeatured, supportedStatus, votes);
            midletsList.addElement(info);

            if (isFeatured) {
                featuredApps.addElement(info);
            }
        }
    }

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

    private void initializeUI() {
        try {
            // Create the store canvas directly, without SVG
            storeCanvas = new StoreCanvas(this, midletsList, featuredApps);
            storeCanvas.addCommand(exitCommand);
            storeCanvas.addCommand(selectCommand);

            // Add search command to store canvas
            Command searchCommand = new Command("Search", Command.ITEM, 2);
            storeCanvas.addCommand(searchCommand);

            // Add top charts command to store canvas
            Command topChartsCommand = new Command("Top Charts", Command.ITEM, 3);
            storeCanvas.addCommand(topChartsCommand);

            storeCanvas.setCommandListener(this);
            display.setCurrent(storeCanvas);
        } catch (Exception e) {
            handleError("Error initializing UI: " + e.getMessage());
        }

        isLoading = false;
    }

    /**
     * Show the search page
     */
    public void showSearchPage() {
        if (searchPage == null) {
            searchPage = new SearchPage(this, midletsList);
        } else {
            searchPage.reset(); // Reset search state
        }
        display.setCurrent(searchPage);
    }

    /**
     * Show the store canvas
     */
    public void showStoreCanvas() {
        display.setCurrent(storeCanvas);
    }

    /**
     * Show the top charts page
     */
    public void showTopChartsPage() {
        if (topChartsPage == null) {
            topChartsPage = new TopChartsPage(this);
        } else {
            topChartsPage.reset();
        }
        display.setCurrent(topChartsPage);
    }

    /**
     * Get icon for an app by index
     */
    public Image getIconForApp(int index) {
        if (index >= 0 && index < appIcons.size()) {
            return (Image)appIcons.elementAt(index);
        }
        return null;
    }

    /**
     * Set an icon for an app
     */
    public void setAppIcon(int index, Image icon) {
        // Ensure appIcons is initialized and big enough
        while (appIcons.size() <= index) {
            appIcons.addElement(null);
        }
        appIcons.setElementAt(icon, index);
    }

    /**
     * Show Apple-style app details view
     */
    public void showAppDetails(MidletInfo info, Image appIcon) {
        appDetailView = new AppDetailView(this, info, appIcon);
        appDetailView.setCommandListener(this);
        display.setCurrent(appDetailView);
    }

    /**
     * Install a midlet
     */
    public void installMidlet(final MidletInfo info) {
        Alert alert = new Alert("Installing", "Starting download of " + info.getName() + "...", null, AlertType.INFO);
        alert.setTimeout(3000); // Set a 3-second timeout instead of FOREVER

        // Fix: Return to store canvas after installation instead of detail view
        display.setCurrent(alert, storeCanvas);

        // Start a thread to handle the download and installation
        Thread downloader = new Thread() {
            public void run() {
                try {
                    // Try to launch the device's browser with the download URL
                    String platformRequest = info.getDownloadUrl();
                    if (platformRequest(platformRequest)) {
                        // platformRequest returns true if the MIDlet will be terminated
                        destroyApp(false);
                        notifyDestroyed();
                    }
                } catch (Exception e) {
                    // Handle errors during the download process
                    Alert errorAlert = new Alert("Error", "Failed to open download: " + e.getMessage(), null, AlertType.ERROR);
                    errorAlert.setTimeout(Alert.FOREVER);
                    display.setCurrent(errorAlert, storeCanvas);
                }
            }
        };
        downloader.start();
    }

    private void handleError(String message) {
        Alert alert = new Alert("Error", message, null, AlertType.ERROR);
        alert.setTimeout(Alert.FOREVER);
        display.setCurrent(alert);
    }

    // Inner class to hold MIDlet information
    class MidletInfo {
        private String id;
        private String name;
        private String description;
        private String iconUrl;
        private String downloadUrl;
        private boolean featured;
        private String supportedStatus;
        private int votes; // Changed from float rating to int votes

        public MidletInfo(String id, String name, String description, String iconUrl, 
                          String downloadUrl, boolean featured, String supportedStatus, int votes) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.iconUrl = iconUrl;
            this.downloadUrl = downloadUrl;
            this.featured = featured;
            this.supportedStatus = supportedStatus;
            this.votes = votes;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getIconUrl() { return iconUrl; }
        public String getDownloadUrl() { return downloadUrl; }
        public boolean isFeatured() { return featured; }
        public String getSupportedStatus() { return supportedStatus; }
        public int getVotes() { return votes; } // Changed from getRating to getVotes

        public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
        public void setVotes(int votes) { this.votes = votes; }
    }
}




