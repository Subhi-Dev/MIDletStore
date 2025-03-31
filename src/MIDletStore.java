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
import javax.microedition.lcdui.Image;

import javax.microedition.m2g.SVGImage;
import javax.microedition.m2g.SVGAnimator;


import javax.microedition.m2g.ScalableImage;
import javax.microedition.m2g.SVGAnimator;

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
    private String csvUrl = "http://localhost:3000/storeapi/apps"; // Default URL, can be configured
    private AppDetailView appDetailView;
    
    public void startApp() {
        display = Display.getDisplay(this);
        
        if (storeCanvas == null) {
            // Initialize commands
            exitCommand = new Command("Exit", Command.EXIT, 1);
            selectCommand = new Command("Select", Command.ITEM, 1);
            backCommand = new Command("Back", Command.BACK, 1);
            
            // Show loading screen
            showLoadingScreen();
            
            // Load MIDlets data from CSV
            loadMidletsFromCSV();
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
            // Handle install command from AppDetailView
            AppDetailView detailView = (AppDetailView)d;
            MidletInfo appInfo = detailView.getAppInfo();
            if (appInfo != null) {
                installMidlet(appInfo);
            }
        }
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
                    connection = (HttpConnection) Connector.open(csvUrl);
                    if (connection.getResponseCode() == HttpConnection.HTTP_OK) {
                        is = connection.openInputStream();
                        parseCsvData(is);
                        ensureCompatibleImageUrls(midletsList);
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
        // Expected CSV format: name,description,iconUrl,downloadUrl,isFeatured
        String[] parts = split(line, ',');
        if (parts.length >= 5) {
            String name = parts[0];
            String description = parts[1];
            String iconUrl = parts[2];
            String downloadUrl = parts[3];
            boolean isFeatured = "true".equalsIgnoreCase(parts[4]);
            
            MidletInfo info = new MidletInfo(name, description, iconUrl, downloadUrl, isFeatured);
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
            storeCanvas.setCommandListener(this);
            display.setCurrent(storeCanvas);
        } catch (Exception e) {
            handleError("Error initializing UI: " + e.getMessage());
        }
        
        isLoading = false;
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
    public void installMidlet(MidletInfo info) {
        Alert alert = new Alert("Installing", "Starting download of " + info.getName() + "...", null, AlertType.INFO);
        alert.setTimeout(3000); // Set a 3-second timeout instead of FOREVER
        
        // Fix: Return to store canvas after installation instead of detail view
        display.setCurrent(alert, storeCanvas);
        
        // In a real app, you would start the actual download and installation here
    }
    
    private void showMidletDetails(MidletInfo info) {
        Form detailsForm = new Form(info.getName());
        detailsForm.append(info.getDescription() + "\n");
        detailsForm.append("URL: " + info.getDownloadUrl());
        detailsForm.addCommand(backCommand);
        detailsForm.addCommand(new Command("Download", Command.ITEM, 1));
        detailsForm.setCommandListener(this);
        display.setCurrent(detailsForm);
    }
    
    private void handleError(String message) {
        Alert alert = new Alert("Error", message, null, AlertType.ERROR);
        alert.setTimeout(Alert.FOREVER);
        display.setCurrent(alert);
    }
    
    /**
     * Set up compatible image URLs for any MIDlet info to ensure proper loading
     */
    private void ensureCompatibleImageUrls(Vector midletsList) {
        for (int i = 0; i < midletsList.size(); i++) {
            MidletInfo info = (MidletInfo) midletsList.elementAt(i);
            String originalUrl = info.getIconUrl();
            
            // If the URL doesn't already have a format parameter, add one
            if (originalUrl != null && originalUrl.indexOf("format=") == -1) {
                if (originalUrl.indexOf('?') == -1) {
                    // No query parameters yet
                    info.setIconUrl(originalUrl + "?format=png8");
                } else {
                    // Append to existing query parameters
                    info.setIconUrl(originalUrl + "&format=png8");
                }
            }
        }
    }
    
    // Inner class to hold MIDlet information
    class MidletInfo {
        private String name;
        private String description;
        private String iconUrl;
        private String downloadUrl;
        private boolean featured;
        
        public MidletInfo(String name, String description, String iconUrl, String downloadUrl, boolean featured) {
            this.name = name;
            this.description = description;
            this.iconUrl = iconUrl;
            this.downloadUrl = downloadUrl;
            this.featured = featured;
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getIconUrl() { return iconUrl; }
        public String getDownloadUrl() { return downloadUrl; }
        public boolean isFeatured() { return featured; }
        
        public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    }
}




