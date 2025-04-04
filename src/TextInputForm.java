import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/**
 * Text input form for search
 * This provides a system text input interface which is more usable
 * than trying to handle text input directly in a Canvas
 */
public class TextInputForm extends Form implements CommandListener {
    private TextField textField;
    private Command okCommand;
    private Command cancelCommand;
    private SearchPage searchPage;
    private MIDlet midlet;
    
    /**
     * Constructor
     */
    public TextInputForm(String title, String initialText, SearchPage searchPage) {
        super(title);
        
        this.searchPage = searchPage;
        this.midlet = getMIDletFromSearchPage();
        
        // Create text field with initial text
        textField = new TextField("Search Term:", initialText, 100, TextField.ANY);
        append(textField);
        
        // Create commands
        okCommand = new Command("OK", Command.OK, 1);
        cancelCommand = new Command("Cancel", Command.CANCEL, 2);
        
        addCommand(okCommand);
        addCommand(cancelCommand);
        
        setCommandListener(this);
    }
    
    /**
     * Get the MIDlet instance from the parent application
     */
    private MIDlet getMIDletFromSearchPage() {
        // Get the MIDlet by finding the current Display's MIDlet
        try {
            Display display = Display.getDisplay(null); // This will throw an exception
            return null; // Never reached
        } catch (Exception e) {
            // The exception tells us we need to pass a valid MIDlet
            // Let's get it from the current active display
            return getMIDletFromActiveDisplay();
        }
    }
    
    /**
     * Get the MIDlet instance from the currently active display
     */
    private MIDlet getMIDletFromActiveDisplay() {
        // In a real application, we'd need a reference to the MIDlet
        // For now, we'll use a workaround to handle the case in the commandAction
        return null;
    }
    
    /**
     * Command handler
     */
    public void commandAction(Command c, Displayable d) {
        if (c == okCommand) {
            // Return the entered text to search page
            searchPage.setSearchQuery(textField.getString());
            
            // Return to search page - workaround for the missing MIDlet reference
            searchPage.returnToSearchPage(this);
            //Display current = Display.getDisplay(null);
            try {
                // Try to get current display, which will throw an exception
                // The exception will be caught and we'll use a different approach
            } catch (Exception e) {
                // Use the display that this form is currently shown on
                searchPage.returnToSearchPage(this);
            }
        } else if (c == cancelCommand) {
            // Just return to search page without changing anything
            searchPage.returnToSearchPage(this);
        }
    }
}
