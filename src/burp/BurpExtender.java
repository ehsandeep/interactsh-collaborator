package burp;

import burp.gui.InteractshListener;
import burp.gui.PollTimeListener;
import interactsh.Client;
import interactsh.InteractEntry;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

public class BurpExtender extends AbstractTableModel implements IBurpExtender, IContextMenuFactory, ITab, IExtensionStateListener
{
    public static int pollTime = 60;
    private static IBurpExtenderCallbacks callbacks;
    private static IExtensionHelpers helpers;

    private JSplitPane splitPane;
    private JScrollPane scrollPane;
    private JSplitPane tableSplitPane;
    private JPanel resultsPanel;
    private static JTextField pollField;
    private static Table logTable;
    private static List<InteractEntry> log = new ArrayList<InteractEntry>();
    private static ArrayList<Client> clients = new ArrayList<Client>();
    private InteractshListener listener;

    //
    // implement IBurpExtender
    //

    @Override
    public void registerExtenderCallbacks(final IBurpExtenderCallbacks callbacks)
    {
        // keep a reference to our callbacks object
        this.callbacks = callbacks;

        // obtain an extension helpers object
        helpers = callbacks.getHelpers();

        // set our extension name
        callbacks.setExtensionName("Interactsh Collaborator");
        callbacks.printOutput("Starting Interactsh Collaborator!");

        // Register this as a IExtensionStateListener
        callbacks.registerExtensionStateListener(BurpExtender.this);

        // Register this as a IContextMenuFactory
        callbacks.registerContextMenuFactory(BurpExtender.this);

        // create our UI
        SwingUtilities.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

                resultsPanel = new JPanel();
                tableSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
                logTable = new Table(BurpExtender.this);
                scrollPane = new JScrollPane(logTable);

                tableSplitPane.setTopComponent(scrollPane);
                tableSplitPane.setBottomComponent(resultsPanel);
                splitPane.setBottomComponent(tableSplitPane);

                JPanel panel = new JPanel();
                JButton CollaboratorButton = new JButton("Generate Interactsh url");
                JLabel pollLabel = new JLabel("Poll Time: ");
                pollField = new JTextField("60", 4);
                pollField.getDocument().addDocumentListener(new PollTimeListener());

                listener = new InteractshListener();
                CollaboratorButton.addActionListener(listener);
                panel.add(CollaboratorButton);
                panel.add(pollLabel);
                panel.add(pollField);
                splitPane.setTopComponent(panel);

                // customize our UI components
                callbacks.customizeUiComponent(resultsPanel);
                callbacks.customizeUiComponent(tableSplitPane);
                callbacks.customizeUiComponent(splitPane);
                callbacks.customizeUiComponent(logTable);
                callbacks.customizeUiComponent(scrollPane);
                callbacks.customizeUiComponent(CollaboratorButton);

                // add the custom tab to Burp's UI
                callbacks.addSuiteTab(BurpExtender.this);
            }
        });
    }

    public static IBurpExtenderCallbacks getCallbacks() {
        return callbacks;
    }

    public static IExtensionHelpers getHelpers() {
        return helpers;
    }

    @Override
    public void extensionUnloaded() {
        // Get all threads and stop them.
        listener.running = false;
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {}
        for (int i = 0; i < listener.pollers.size(); i++){
            listener.pollers.get(i).stop();
        }

        // Tell all clients to deregister
        for (int i = 0; i < clients.size(); i++){
            clients.get(i).deregister();
        }
        callbacks.printOutput("Thanks for collaborating!");
    }

    public static ArrayList<Client> getClients(){
        return clients;
    }

    public static void addClient(Client c){
        clients.add(c);
    }

    public static int getPollTime(){
        try{
            return Integer.parseInt(pollField.getText());
        }catch (Exception ex) {}
        return 60;
    }

    public static void updatePollTime(int poll){
        pollTime = poll;
    }

    public static void addToTable(InteractEntry i){
        log.add(i);
        logTable.revalidate();
    }

    //
    // implement ITab
    //

    @Override
    public String getTabCaption()
    {
        return "Interactsh";
    }

    @Override
    public Component getUiComponent()
    {
        return splitPane;
    }

    //
    // extend IContextMenuFactory
    //

    @Override
    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
        List<JMenuItem> menuList = new ArrayList<JMenuItem>();
        JMenuItem item = new JMenuItem("Generate Interactsh url");
        item.addActionListener(new InteractshListener());
        menuList.add(item);

        return menuList;
    }

    //
    // extend AbstractTableModel
    //

    @Override
    public int getRowCount()
    {
        return log.size();
    }

    @Override
    public int getColumnCount()
    {
        return 4;
    }

    @Override
    public String getColumnName(int columnIndex)
    {
        switch (columnIndex)
        {
            case 0:
                return "Entry";
            case 1:
                return "Type";
            case 2:
                return "Address";
            case 3:
                return "Time";
            default:
                return "";
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex)
    {
        return String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex)
    {
        InteractEntry ie = log.get(rowIndex);

        switch (columnIndex)
        {
            case 0:
                return ie.uid;
            case 1:
                return ie.protocol;
            case 2:
                return ie.address;
            case 3:
                return ie.timestamp;
            default:
                return "";
        }
    }

    //
    // extend JTable to handle cell selection
    //

    private class Table extends JTable
    {
        public TableModel tableModel;
        public Table(TableModel tableModel)
        {
            super(tableModel);
            this.tableModel = tableModel;
        }

        @Override
        public void changeSelection(int row, int col, boolean toggle, boolean extend)
        {
            // show the log entry for the selected row
            InteractEntry ie = log.get(row);

            resultsPanel.removeAll(); // Refresh pane
            resultsPanel.setLayout(new BorderLayout());  //give your JPanel a BorderLayout

            JTextArea text = new JTextArea(ie.details);
            JScrollPane scroll = new JScrollPane(text); //place the JTextArea in a scroll pane
            resultsPanel.add(scroll, BorderLayout.CENTER); //add the JScrollPane to the panel
            tableSplitPane.revalidate();

            super.changeSelection(row, col, toggle, extend);
        }
    }
}