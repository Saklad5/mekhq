package mekhq.gui.dialog;

import megamek.client.ui.swing.lobby.ChatLounge;
import megamek.client.ui.swing.lobby.LobbyUtility;
import megamek.client.ui.swing.minimap.Minimap;
import megamek.common.Board;
import megamek.common.BoardDimensions;
import megamek.common.Configuration;
import megamek.common.MapSettings;
import megamek.common.util.fileUtils.MegaMekFile;
import megamek.server.GameManager;
import mekhq.campaign.mission.Scenario;
import org.apache.logging.log4j.LogManager;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageProducer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static megamek.client.ui.swing.lobby.LobbyUtility.drawMinimapLabel;
import static megamek.client.ui.swing.util.UIUtil.scaleStringForGUI;

public class EditMapSettingsDialog extends JDialog {

    private JFrame frame;
    private int mapSizeX;
    private int mapSizeY;
    private String map;
    private boolean usingFixedMap;
    private int boardType;

    private JCheckBox checkFixed;
    private JComboBox<String> comboBoardType;
    private JComboBox<BoardDimensions> comboMapSize;
    private JSpinner spnMapX;
    private JSpinner spnMapY;
    private JScrollPane scrChooseMap;
    private JList<String> listMapGenerators;
    private JList<String> listFixedMaps;
    DefaultListModel<String> generatorModel = new DefaultListModel<>();
    DefaultListModel<String> fixedMapModel = new DefaultListModel<>();

    JPanel panSizeRandom;
    JPanel panSizeFixed;

    private Map<String, ImageIcon> mapIcons = new HashMap<>();
    private Map<String, Image> baseImages = new HashMap<>();

    private ImageLoader loader;


    public EditMapSettingsDialog(JFrame parent, boolean modal, int boardType, boolean usingFixedMap, String map,
                                 int mapSizeX, int mapSizeY) {

        super(parent, modal);
        this.boardType = boardType;
        this.usingFixedMap = usingFixedMap;
        this.map = map;
        this.mapSizeX = mapSizeX;
        this.mapSizeY = mapSizeY;
        loader = new ImageLoader();
        loader.execute();

        initComponents();
        setLocationRelativeTo(parent);
        pack();
    }

    public int getBoardType() {
        return boardType;
    }

    public boolean getUsingFixedMap() {
        return usingFixedMap;
    }

    public String getMap() {
        return map;
    }

    public int getMapSizeX() {
        return mapSizeX;
    }

    public int getMapSizeY() {
        return mapSizeY;
    }

    private void initComponents() {

        getContentPane().setLayout(new BorderLayout());
        JPanel panMain = new JPanel(new GridBagLayout());
        panSizeRandom = new JPanel(new GridBagLayout());
        panSizeFixed = new JPanel(new BorderLayout());
        JPanel panButtons = new JPanel(new GridLayout(0, 2));

        scrChooseMap = new JScrollPane();
        scrChooseMap.setMinimumSize(new Dimension(600, 800));
        scrChooseMap.setPreferredSize(new Dimension(600, 800));


        checkFixed = new JCheckBox("Use fixed map");
        checkFixed.setSelected(usingFixedMap);
        checkFixed.addActionListener(evt -> changeMapType());

        spnMapX = new JSpinner(new SpinnerNumberModel(mapSizeX, 0, null, 1));
        spnMapY = new JSpinner(new SpinnerNumberModel(mapSizeY, 0, null, 1));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(5, 5, 5, 5);
        panSizeRandom.add(spnMapX, gbc);
        gbc.gridx++;
        panSizeRandom.add(new JLabel("x"));
        gbc.gridx++;
        gbc.weightx = 1.0;
        panSizeRandom.add(spnMapY);

        comboMapSize = new JComboBox<>();
        for (BoardDimensions size : GameManager.getBoardSizes()) {
            comboMapSize.addItem(size);
        }
        if(mapSizeX > 0 & mapSizeY > 0) {
            comboMapSize.setSelectedItem(new BoardDimensions(mapSizeX, mapSizeY));
        } else {
            // if no board size yet set, use the default
            comboMapSize.setSelectedItem(new BoardDimensions(16, 17));
        }
        comboMapSize.addActionListener(evt -> refreshBoardList());
        panSizeFixed.add(comboMapSize, BorderLayout.CENTER);

        listMapGenerators = new JList<>(generatorModel);
        listMapGenerators.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        generatorModel.addElement("None");
        File dir = new File("data/mapgen/");
        File[] directoryListing = dir.listFiles();
        ArrayList<String> generators = new ArrayList<>();
        if (directoryListing != null) {
            for (File child : directoryListing) {
                if(child.isFile()) {
                    String s = child.getName().replace(".xml", "");
                    generators.add(s);
                }
            }
        }
        Collections.sort(generators);
        generatorModel.addAll(generators);

        listFixedMaps = new JList<>(fixedMapModel);
        listFixedMaps.setCellRenderer(new BoardNameRenderer());
        listFixedMaps.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        listFixedMaps.setVisibleRowCount(-1);
        listFixedMaps.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refreshBoardList();

        if(usingFixedMap) {
            listFixedMaps.setSelectedValue(map, true);
            scrChooseMap.setViewportView(listFixedMaps);
        }  else {
            listMapGenerators.setSelectedValue(map, true);
            scrChooseMap.setViewportView(listMapGenerators);
        }

        comboBoardType = new JComboBox();
        for (int i = Scenario.T_GROUND; i <= Scenario.T_SPACE; i++) {
            comboBoardType.addItem(Scenario.getBoardTypeName(i));
        }
        comboBoardType.addActionListener(evt -> changeBoardType());
        comboBoardType.setSelectedIndex(boardType);

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(5, 5, 5, 5);
        panMain.add(new JLabel("Board Type:"), gbc);
        gbc.weightx = 1.0;
        gbc.gridx++;
        panMain.add(comboBoardType, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0.0;
        panMain.add(new JLabel("Map Size:"), gbc);
        gbc.gridx++;
        gbc.weightx = 1.0;
        panMain.add(panSizeRandom, gbc);
        panMain.add(panSizeFixed, gbc);
        if(usingFixedMap) {
            panSizeRandom.setVisible(false);
        } else {
            panSizeFixed.setVisible(false);
        }

        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.gridy++;
        panMain.add(checkFixed, gbc);

        gbc.gridy++;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        panMain.add(scrChooseMap, gbc);

        JButton btnOK = new JButton("Done");
        btnOK.addActionListener(evt -> done());
        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(evt -> cancel());
        panButtons.add(btnOK);
        panButtons.add(btnCancel);

        getContentPane().add(panMain, BorderLayout.CENTER);
        getContentPane().add(panButtons, BorderLayout.PAGE_END);
    }

    private void changeBoardType() {
        if(comboBoardType.getSelectedIndex() == Scenario.T_SPACE) {
            checkFixed.setSelected(false);
            checkFixed.setEnabled(false);
            panSizeRandom.setVisible(true);
            panSizeFixed.setVisible(false);
            listMapGenerators.setSelectedIndex(0);
            listMapGenerators.setEnabled(false);
            listFixedMaps.setEnabled(false);
        } else {
            checkFixed.setEnabled(true);
            listMapGenerators.setEnabled(true);
            listFixedMaps.setEnabled(true);
        }
    }

    private void changeMapType() {
        if(checkFixed.isSelected()) {
            panSizeRandom.setVisible(false);
            panSizeFixed.setVisible(true);
            scrChooseMap.setViewportView(listFixedMaps);
        } else {
            panSizeRandom.setVisible(true);
            panSizeFixed.setVisible(false);
            scrChooseMap.setViewportView(listMapGenerators);
        }
    }

    private void refreshBoardList() {
        listFixedMaps.setFixedCellHeight(-1);
        listFixedMaps.setFixedCellWidth(-1);
        List<String> boards = scanForBoards();
        fixedMapModel.removeAllElements();
        fixedMapModel.addAll(boards);
        listFixedMaps.clearSelection();
    }

    public void done() {
        boardType = comboBoardType.getSelectedIndex();
        usingFixedMap = checkFixed.isSelected();
        if(usingFixedMap) {
            map = listFixedMaps.getSelectedValue();
            BoardDimensions boardSize = (BoardDimensions) comboMapSize.getSelectedItem();
            mapSizeX = boardSize.width();
            mapSizeY = boardSize.height();
        } else {
            map = listMapGenerators.getSelectedValue();
            if(listMapGenerators.getSelectedIndex() == 0) {
                map = null;
            }
            mapSizeX = (int) spnMapX.getValue();
            mapSizeY = (int) spnMapY.getValue();
        }
        setVisible(false);
    }

    public void cancel() {
        setVisible(false);
    }

    /**
     * Returns a list of path names of available boards of the size set in the given
     * mapSettings. The path names are minus the '.board' extension and relative to
     * the boards data directory.
     */
    private List<String> scanForBoards() {
        BoardDimensions boardSize = (BoardDimensions) comboMapSize.getSelectedItem();
        java.util.List<String> result = new ArrayList<>();

        // Scan the Megamek boards directory
        File boardDir = Configuration.boardsDir();
        scanForBoardsInDir(boardDir, "", boardSize, result);

        result.sort(String::compareTo);
        return result.stream().map(this::backToForwardSlash).collect(Collectors.toList());
    }

    private String backToForwardSlash(String path) {
        return path.replace("\\", "/");
    }

    /**
     * Scans the given boardDir directory for map boards of the given size and
     * returns them by adding them to the given boards list. Removes the .board extension.
     */
    private void scanForBoardsInDir(final File boardDir, final String basePath, final BoardDimensions dimensions,
                                    List<String> boards) {
        if (boardDir == null) {
            throw new IllegalArgumentException("must provide searchDir");
        } else if (basePath == null) {
            throw new IllegalArgumentException("must provide basePath");
        } else if (dimensions == null) {
            throw new IllegalArgumentException("must provide dimensions");
        } else if (boards == null) {
            throw new IllegalArgumentException("must provide boards");
        }

        String[] fileList = boardDir.list();
        if (fileList != null) {
            for (String filename : fileList) {
                File filePath = new MegaMekFile(boardDir, filename).getFile();
                if (filePath.isDirectory()) {
                    scanForBoardsInDir(filePath, basePath + File.separator + filename, dimensions, boards);
                } else {
                    if (filename.endsWith(".board")) {
                        if (Board.boardIsSize(filePath, dimensions)) {
                            boards.add(basePath + File.separator + filename.substring(0, filename.lastIndexOf(".")));
                        }
                    }
                }
            }
        }
    }

    private class BoardNameRenderer extends DefaultListCellRenderer  {

        private Image image;
        private ImageIcon icon;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {

            String board = (String) value;
            // For generated boards, add the size to have different images for different sizes
            //if (board.startsWith(MapSettings.BOARD_GENERATED)) {
            //    board += comboMapSize.getSelectedItem();
            //}

            // If an icon is present for the current board, use it
            icon = mapIcons.get(board);
            if (icon != null) {
                setIcon(icon);
            } else {
                // The icon is not present, see if there's a base image
                synchronized (baseImages) {
                    image = baseImages.get(board);
                }
                if (image == null) {
                    // There's no base image: trigger loading it and, for now, return the base list's panel
                    // The [GENERATED] entry will always land here as well
                    loader.add(board);
                    setToolTipText(null);
                    return super.getListCellRendererComponent(list, new File(board).getName(), index, isSelected, cellHasFocus);
                } else {
                    // There is a base image: make it into an icon, store it and use it
                    //if (!listFixedMaps.isEnabled()) {
                    //    ImageFilter filter = new GrayFilter(true, 50);
                    //    ImageProducer producer = new FilteredImageSource(image.getSource(), filter);
                    //    image = Toolkit.getDefaultToolkit().createImage(producer);
                    //}
                    icon = new ImageIcon(image);

                    mapIcons.put(board, icon);
                    setIcon(icon);
                }
            }

            // Found or created an icon; finish the panel
            setText("");
            if (listFixedMaps.isEnabled()) {
                //setToolTipText(scaleStringForGUI(createBoardTooltip(board)));
            } else {
                setToolTipText(null);
            }

            if (isSelected) {
                setForeground(list.getSelectionForeground());
                setBackground(list.getSelectionBackground());
            } else {
                setForeground(list.getForeground());
                setBackground(list.getBackground());
            }

            return this;
        }
    }

    private class ImageLoader extends SwingWorker<Void, Image> {

        private BlockingQueue<String> boards = new LinkedBlockingQueue<>();

        private synchronized void add(String name) {
            if (!boards.contains(name)) {
                try {
                    boards.put(name);
                } catch (Exception e) {
                    LogManager.getLogger().error("", e);
                }
            }
        }

        private Image prepareImage(String boardName) {
            MapSettings mapSettings = MapSettings.getInstance();
            BoardDimensions boardSize = (BoardDimensions) comboMapSize.getSelectedItem();
            mapSettings.setBoardSize(boardSize.width(), boardSize.height());
            File boardFile = new MegaMekFile(Configuration.boardsDir(), boardName + ".board").getFile();
            Board board;
            StringBuffer errs = new StringBuffer();
            if (boardFile.exists()) {
                board = new Board();
                try (InputStream is = new FileInputStream(boardFile)) {
                    board.load(is, errs, true);
                } catch (IOException ex) {
                    board = Board.createEmptyBoard(mapSettings.getBoardWidth(), mapSettings.getBoardHeight());
                }
            } else {
                board = Board.createEmptyBoard(mapSettings.getBoardWidth(), mapSettings.getBoardHeight());
            }

            // Determine a minimap zoom from the board size and gui scale.
            // This is very magic numbers but currently the minimap has only fixed zoom states.
            int largerEdge = Math.max(board.getWidth(), board.getHeight());
            int zoom = 3;
            if (largerEdge < 17) {
                zoom = 4;
            }
            if (largerEdge > 20) {
                zoom = 2;
            }
            if (largerEdge > 30) {
                zoom = 1;
            }
            if (largerEdge > 40) {
                zoom = 0;
            }
            if (board.getWidth() < 25) {
                zoom = Math.max(zoom, 3);
            }
            float scale = 1;
            zoom = (int) (scale*zoom);
            if (zoom > 6) {
                zoom = 6;
            }
            if (zoom < 0) {
                zoom = 0;
            }
            BufferedImage bufImage = Minimap.getMinimapImage(board, zoom);

            // Add the board name label and the server-side board label if necessary
            String text = LobbyUtility.cleanBoardName(boardName, mapSettings);
            Graphics g = bufImage.getGraphics();
            LobbyUtility.drawMinimapLabel(text, bufImage.getWidth(), bufImage.getHeight(), g, errs.length() != 0);
            g.dispose();

            synchronized(baseImages) {
                baseImages.put(boardName, bufImage);
            }
            return bufImage;
        }


        @Override
        protected Void doInBackground() throws Exception {
            Image image;
            while (!isCancelled()) {
                // Create thumbnails for the MapSettings boards
                String boardName = boards.poll(1, TimeUnit.SECONDS);
                if (boardName != null && !baseImages.containsKey(boardName)) {
                    image = prepareImage(boardName);
                    redrawMapTable(image);
                }
            }
            return null;
        }

        private void redrawMapTable(Image image) {
            if (image != null) {
                if (listFixedMaps.getFixedCellHeight() != image.getHeight(null)
                        || listFixedMaps.getFixedCellWidth() != image.getWidth(null)) {
                    listFixedMaps.setFixedCellHeight(image.getHeight(null));
                    listFixedMaps.setFixedCellWidth(image.getWidth(null));
                }
                listFixedMaps.repaint();
            }
        }
    }
}
