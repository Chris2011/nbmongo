/* 
 * Copyright (C) 2015 Yann D'Isanto
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.netbeans.modules.mongodb.ui.components;

import java.awt.CardLayout;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.text.PlainDocument;
import javax.swing.tree.TreePath;
import lombok.Getter;
import lombok.Setter;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import org.netbeans.modules.mongodb.CollectionInfo;
import org.netbeans.modules.mongodb.api.CollectionResult;
import org.netbeans.modules.mongodb.api.CollectionResultPages;
import org.netbeans.modules.mongodb.api.FindCriteria;
import org.netbeans.modules.mongodb.api.FindCriteria.SortOrder;
import org.netbeans.modules.mongodb.api.FindResult;
import org.netbeans.modules.mongodb.resources.Images;
import org.netbeans.modules.mongodb.ui.util.IntegerDocumentFilter;
import org.netbeans.modules.mongodb.ui.actions.*;
import org.netbeans.modules.mongodb.ui.components.result_panel.views.flattable.DocumentsFlatTableModel;
import org.netbeans.modules.mongodb.ui.components.result_panel.views.treetable.DocumentsTreeTableModel;
import org.netbeans.modules.mongodb.ui.util.BsonPropertyEditor;
import static org.netbeans.modules.mongodb.ui.util.BsonPropertyEditor.isQuickEditableBsonValue;
import org.netbeans.modules.mongodb.ui.util.BsonDocumentEditor;
import org.netbeans.modules.mongodb.ui.components.result_panel.actions.*;
import org.netbeans.modules.mongodb.ui.components.result_panel.views.flattable.BsonFlatTableCellRenderer;
import org.netbeans.modules.mongodb.ui.components.result_panel.views.treetable.BsonNodeRenderer;
import org.netbeans.modules.mongodb.ui.components.result_panel.views.treetable.BsonPropertyNode;
import org.netbeans.modules.mongodb.ui.components.result_panel.views.treetable.BsonValueNode;
import org.netbeans.modules.mongodb.ui.components.result_panel.views.treetable.DocumentRootTreeTableHighlighter;
import org.netbeans.modules.mongodb.options.RenderingOptions.PrefsRenderingOptions;
import org.netbeans.modules.mongodb.preferences.Prefs;
import org.netbeans.modules.mongodb.ui.components.result_panel.views.text.ResultsTextView;
import org.netbeans.modules.mongodb.ui.windows.CollectionView;
import org.netbeans.modules.mongodb.util.BsonProperty;
import org.netbeans.modules.mongodb.util.Tasks;
import org.openide.awt.NotificationDisplayer;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.TopComponent;

/**
 *
 * @author Yann D'Isanto
 */
@Messages({
    "displayDocumentTitle=Display document",
    "invalidJson=invalid json",
    "# {0} - total documents count",
    "totalDocuments=Total Documents: {0}      ",
    "# {0} - current page",
    "# {1} - total page count",
    "pageCountLabel=Page {0} of {1}",
    "# {0} - collection namespace",
    "collectionViewTitle={0}",
    "# {0} - connection name",
    "# {1} - view title",
    "collectionViewTooltip={0}: {1}",
    "documentEditionShortcutHintTitle=Use CTRL + doubleclick to edit full document",
    "documentEditionShortcutHintDetails=Click here or use shortcut so this message won't show again.",
    "TASK_refreshResults=refreshing results",
    "MENU_queryWithProperty=Query with this property",
    "ACTION_usePropertySetFindFilter=use as find filter",
    "ACTION_usePropertySetFindProjection=use as find projection",
    "# {0} - sort order",
    "ACTION_usePropertySetFindSort=use as find sort in {0} order",
    "ACTION_usePropertyAddFindFilter=add to current filter",
    "ACTION_usePropertyAddFindProjection=add to current projection",
    "# {0} - sort order",
    "ACTION_usePropertyAddFindSort=add to current sort in {0} order"
})
public final class CollectionResultPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private static final ResultView DEFAULT_RESULT_VIEW = ResultView.TREE_TABLE;

    private static final RequestProcessor REQUEST_PROCESSOR = new RequestProcessor(CollectionResultPanel.class);

    @Getter
    private final Lookup lookup;

    private final Map<ResultView, JToggleButton> resultViewButtons = new EnumMap<>(ResultView.class);

    private ResultView resultView = DEFAULT_RESULT_VIEW;

    private final Map<ResultView, View> resultViews = new EnumMap<>(ResultView.class);

    @Getter
    private final DocumentsTreeTableModel treeTableModel;

    @Getter
    private final DocumentsFlatTableModel flatTableModel;

    @Getter
    private final ResultsTextView textView;

    @Getter
    @Setter
    private boolean displayDocumentEditionShortcutHint = true;

    @Getter
    private final boolean readOnly;

    @Getter
    private CollectionResult currentResult;

    private final Runnable resultRefresh = new Runnable() {

        @Override
        public void run() {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            getResultPages().refresh();
        }
    };

    private final Runnable resultUpdate = new Runnable() {

        @Override
        public void run() {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            getResultPages().setQueryResult(currentResult);
        }
    };

    private final CollectionResultPages.Listener pagesListener = new CollectionResultPages.Listener() {

        @Override
        public void pageChanged(CollectionResultPages pages, int pageIndex, List<BsonDocument> page) {
            updatePagination();
            updateDocumentButtonsState();
        }

        @Override
        public void pageObjectUpdated(int index, BsonDocument oldValue, BsonDocument newValue) {
        }

    };

    /**
     * Creates new form QueryResultPanel
     */
    public CollectionResultPanel(Lookup lookup, final boolean readOnly) {
        this.lookup = lookup;
        this.readOnly = readOnly;
        initComponents();

        addButton.setVisible(readOnly == false);
        deleteButton.setVisible(readOnly == false);
        editButton.setVisible(readOnly == false);

        resultViewButtons.put(ResultView.FLAT_TABLE, flatTableViewButton);
        resultViewButtons.put(ResultView.TREE_TABLE, treeTableViewButton);
        resultViewButtons.put(ResultView.TEXT, textViewButton);

        int pageSize = 20; // TODO: store/load from pref
        currentResult = CollectionResult.EMPTY;
        treeTableModel = new DocumentsTreeTableModel(new CollectionResultPages(currentResult, pageSize, readOnly));
        flatTableModel = new DocumentsFlatTableModel(new CollectionResultPages(currentResult, pageSize, readOnly));
        textView = new ResultsTextView(new CollectionResultPages(currentResult, pageSize, readOnly));
        resultPanel.add(textView, "TEXT");
        resultViews.put(ResultView.TREE_TABLE, treeTableModel);
        resultViews.put(ResultView.FLAT_TABLE, flatTableModel);
        resultViews.put(ResultView.TEXT, textView);

        final ListSelectionListener tableSelectionListener = new ListSelectionListener() {

            @Override
            public void valueChanged(ListSelectionEvent evt) {
                if (!evt.getValueIsAdjusting()) {
                    updateDocumentButtonsState();
                }
            }
        };

        resultFlatTable.setModel(flatTableModel);
        resultFlatTable.setDefaultRenderer(BsonDocument.class, new BsonFlatTableCellRenderer());
        resultFlatTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultFlatTable.getSelectionModel().addListSelectionListener(tableSelectionListener);
        resultFlatTable.getColumnModel().addColumnModelListener(new TableColumnModelListener() {

            @Override
            public void columnAdded(TableColumnModelEvent e) {
                final TableColumnModel model = (TableColumnModel) e.getSource();
                final TableColumn column = model.getColumn(e.getToIndex());
                if ("_id".equals(column.getHeaderValue())) {
                    Font font = PrefsRenderingOptions.INSTANCE.documentRoot().getFont();
                    int preferredWidth = getFontMetrics(font)
                        .stringWidth("000000000000000000000000");
                    column.setPreferredWidth(preferredWidth);
                }
            }

            @Override
            public void columnRemoved(TableColumnModelEvent e) {
            }

            @Override
            public void columnMoved(TableColumnModelEvent e) {
            }

            @Override
            public void columnMarginChanged(ChangeEvent e) {
            }

            @Override
            public void columnSelectionChanged(ListSelectionEvent e) {
            }
        });

        resultTreeTable.setTreeTableModel(treeTableModel);
        resultTreeTable.setTreeCellRenderer(new BsonNodeRenderer());
        resultTreeTable.addHighlighter(new DocumentRootTreeTableHighlighter());
        resultTreeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTreeTable.getSelectionModel().addListSelectionListener(tableSelectionListener);

        final PlainDocument document = (PlainDocument) pageSizeField.getDocument();
        document.setDocumentFilter(new IntegerDocumentFilter());

        resultTreeTable.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    final TreePath path = resultTreeTable.getPathForLocation(e.getX(), e.getY());
                    final BsonValueNode node = (BsonValueNode) path.getLastPathComponent();

                    if ((e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) == MouseEvent.CTRL_DOWN_MASK) {
                        final BsonValueNode documentRootNode = (BsonValueNode) path.getPathComponent(1);
                        if (readOnly) {
                            BsonDocumentEditor.showReadOnly(
                                Bundle.displayDocumentTitle(),
                                documentRootNode.getValue().asDocument());
                        } else {
                            editDocumentAction.setDocument(documentRootNode.getValue().asDocument());
                            editDocumentAction.actionPerformed(null);
                        }
                    } else if (node.isLeaf()) {
                        if (readOnly == false) {
                            dislayDocumentEditionShortcutHintIfNecessary();
                            if (node instanceof BsonPropertyNode) {
                                BsonPropertyNode propertyNode = (BsonPropertyNode) node;
                                if (BsonPropertyEditor.isQuickEditableBsonValue(propertyNode.getValue())) {
                                    editBsonPropertyNodeAction.setPropertyNode(propertyNode);
                                    editBsonPropertyNodeAction.actionPerformed(null);
                                }
                            } else if (BsonPropertyEditor.isQuickEditableBsonValue(node.getValue())) {
                                editBsonValueNodeAction.setValueNode(node);
                                editBsonValueNodeAction.actionPerformed(null);
                            }
                        }
                    } else if (resultTreeTable.isCollapsed(path)) {
                        resultTreeTable.expandPath(path);
                    } else {
                        resultTreeTable.collapsePath(path);
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    final TreePath path = resultTreeTable.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        final int row = resultTreeTable.getRowForPath(path);
                        resultTreeTable.setRowSelectionInterval(row, row);
                    }
                    final JPopupMenu menu = createTreeTableContextMenu(path);
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }
            }

        });
        resultFlatTable.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && readOnly == false) {
                    editSelectedDocumentAction.actionPerformed(null);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    final int row = resultFlatTable.rowAtPoint(e.getPoint());
                    if (row > -1) {
                        final int column = resultFlatTable.columnAtPoint(e.getPoint());
                        resultFlatTable.setRowSelectionInterval(row, row);
                        final JPopupMenu menu = createFlatTableContextMenu(row, column);
                        menu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }

        });
        getResultPages().addListener(pagesListener);
    }

    public void setResult(CollectionResult result) {
        currentResult = result;
        Tasks.create(REQUEST_PROCESSOR, Bundle.TASK_refreshResults(), resultUpdate).execute();
    }

    public void refreshResults() {
        Tasks.create(REQUEST_PROCESSOR, Bundle.TASK_refreshResults(), resultRefresh).execute();
    }

    public void editDocument(BsonDocument document, BsonDocument modifiedDocument) {
        if (currentResult instanceof FindResult) {
            FindResult result = (FindResult) currentResult;
            BsonDocument projection = result.getFindCriteria().getProjection();
            if (projection.isEmpty() == false) {
                List<String> filtered = new ArrayList<>(modifiedDocument.size());
                for (String key : modifiedDocument.keySet()) {
                    if ("_id".equals(key) == false) {
                        BsonValue value = projection.get(key);
                        boolean filteredField = value == null
                                || (value.isBoolean() && value.asBoolean().getValue() == false)
                                || (value.isInt32() && value.asInt32().getValue() == 0);
                        if (filteredField) {
                            filtered.add(key);
                        }
                    }
                }
                for (String key : filtered) {
                    modifiedDocument.remove(key);
                }
            }
        }
        for (View view : resultViews.values()) {
            CollectionResultPages pages = view.getPages();
            if (pages.getQueryResult().equals(currentResult)) {
                pages.updateDocument(document, modifiedDocument);
            }
        }

    }

    private JTable getResultTable() {
        switch (resultView) {
            case FLAT_TABLE:
                return resultFlatTable;
            case TREE_TABLE:
                return resultTreeTable;
            default:
                return null;
        }
    }

    public CollectionResultPages getFlatTablePages() {
        return flatTableModel.getPages();
    }

    public CollectionResultPages getTreeTablePages() {
        return treeTableModel.getPages();
    }

    public CollectionResultPages getTextViewPages() {
        return textView.getPages();
    }

    public CollectionResultPages getResultPages() {
        return resultViews.get(resultView).getPages();
    }

    public BsonDocument getResultTableSelectedDocument() {
        final JTable table = getResultTable();
        if (table == null) { // TEXT view
            return null;
        }
        int row = table.getSelectedRow();
        if (row == -1) {
            return null;
        }
        switch (resultView) {
            case FLAT_TABLE:
                return ((DocumentsFlatTableModel) resultFlatTable.getModel()).getRowValue(row);
            case TREE_TABLE:
                final TreePath selectionPath = resultTreeTable.getPathForRow(row);
                final BsonValueNode documentRootNode = (BsonValueNode) selectionPath.getPathComponent(1);
                return documentRootNode.getValue().asDocument();
            default:
                throw new AssertionError();
        }
    }

    public void changeResultView(ResultView resultView) {
        getResultPages().removeListener(pagesListener);
        this.resultView = resultView;
        getResultPages().addListener(pagesListener);
        if (getResultPages().getQueryResult().equals(currentResult) == false) {
            setResult(currentResult);
        }
        updateResultPanel();
    }

    private void updateResultPanel() {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                final CardLayout layout = (CardLayout) resultPanel.getLayout();
                layout.show(resultPanel, resultView.name());
                final boolean treeViewDisplayed = resultView == ResultView.TREE_TABLE;
                collapseTreeAction.setEnabled(treeViewDisplayed);
                expandTreeAction.setEnabled(treeViewDisplayed);
            }
        });
    }

    public void updatePagination() {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                CollectionResultPages pages = getResultPages();
                long documentCount = pages.getTotalElementsCount();
                totalDocumentsLabel.setText(
                    Bundle.totalDocuments(documentCount));
                int page = documentCount == 0 ? 0 : pages.getPageIndex();
                int pageCount = pages.getPageCount();
                pageCountLabel.setText(Bundle.pageCountLabel(page, pageCount));
                navFirstAction.setEnabled(pages.canMoveBackward());
                navLeftAction.setEnabled(pages.canMoveBackward());
                navRightAction.setEnabled(pages.canMoveForward());
                navLastAction.setEnabled(pages.canMoveForward());
                setCursor(null);
            }
        });
    }

    private void updateDocumentButtonsState() {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                JTable table = getResultTable();
                boolean itemSelected = table != null && table.getSelectedRow() > -1;
                addButton.setEnabled(readOnly == false);
                deleteButton.setEnabled(itemSelected && readOnly == false);
                editButton.setEnabled(itemSelected && readOnly == false);
            }
        });
    }

    private void changePageSize(int pageSize) {
        getTreeTablePages().setPageSize(pageSize);
        getFlatTablePages().setPageSize(pageSize);
        getTextViewPages().setPageSize(pageSize);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        resultViewButtonGroup = new javax.swing.ButtonGroup();
        documentsToolBar = new javax.swing.JToolBar();
        treeTableViewButton = new javax.swing.JToggleButton();
        flatTableViewButton = new javax.swing.JToggleButton();
        textViewButton = new javax.swing.JToggleButton();
        jSeparator4 = new javax.swing.JToolBar.Separator();
        expandTreeButton = new javax.swing.JButton();
        collapseTreeButton = new javax.swing.JButton();
        jSeparator5 = new javax.swing.JToolBar.Separator();
        addButton = new javax.swing.JButton();
        deleteButton = new javax.swing.JButton();
        editButton = new javax.swing.JButton();
        exportButton = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JToolBar.Separator();
        refreshDocumentsButton = new javax.swing.JButton();
        navFirstButton = new javax.swing.JButton();
        navLeftButton = new javax.swing.JButton();
        navRightButton = new javax.swing.JButton();
        navLastButton = new javax.swing.JButton();
        sortFieldsButton = new javax.swing.JToggleButton();
        jSeparator2 = new javax.swing.JToolBar.Separator();
        pageSizeLabel = new javax.swing.JLabel();
        pageSizeField = new javax.swing.JTextField();
        jSeparator3 = new javax.swing.JToolBar.Separator();
        totalDocumentsLabel = new javax.swing.JLabel();
        pageCountLabel = new javax.swing.JLabel();
        resultPanel = new javax.swing.JPanel();
        treeTableScrollPane = new javax.swing.JScrollPane();
        resultTreeTable = new org.jdesktop.swingx.JXTreeTable();
        flatTableScrollPane = new javax.swing.JScrollPane();
        resultFlatTable = new javax.swing.JTable();

        setLayout(new java.awt.BorderLayout(0, 5));

        documentsToolBar.setFloatable(false);
        documentsToolBar.setRollover(true);

        treeTableViewButton.setAction(treeTableViewAction);
        resultViewButtonGroup.add(treeTableViewButton);
        treeTableViewButton.setFocusable(false);
        treeTableViewButton.setHideActionText(true);
        treeTableViewButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        treeTableViewButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        documentsToolBar.add(treeTableViewButton);

        flatTableViewButton.setAction(flatTableViewAction);
        resultViewButtonGroup.add(flatTableViewButton);
        flatTableViewButton.setFocusable(false);
        flatTableViewButton.setHideActionText(true);
        flatTableViewButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        flatTableViewButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        documentsToolBar.add(flatTableViewButton);

        textViewButton.setAction(treeViewAction);
        resultViewButtonGroup.add(textViewButton);
        textViewButton.setFocusable(false);
        textViewButton.setHideActionText(true);
        textViewButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        textViewButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        documentsToolBar.add(textViewButton);
        documentsToolBar.add(jSeparator4);

        expandTreeButton.setAction(expandTreeAction);
        expandTreeButton.setFocusable(false);
        expandTreeButton.setHideActionText(true);
        expandTreeButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        expandTreeButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        documentsToolBar.add(expandTreeButton);

        collapseTreeButton.setAction(collapseTreeAction);
        collapseTreeButton.setFocusable(false);
        collapseTreeButton.setHideActionText(true);
        collapseTreeButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        collapseTreeButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        documentsToolBar.add(collapseTreeButton);
        documentsToolBar.add(jSeparator5);

        addButton.setAction(addDocumentAction);
        addButton.setFocusable(false);
        addButton.setHideActionText(true);
        addButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        addButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        documentsToolBar.add(addButton);

        deleteButton.setAction(deleteSelectedDocumentAction);
        deleteButton.setEnabled(false);
        deleteButton.setFocusable(false);
        deleteButton.setHideActionText(true);
        deleteButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        deleteButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        documentsToolBar.add(deleteButton);

        editButton.setAction(editSelectedDocumentAction);
        editButton.setEnabled(false);
        editButton.setFocusable(false);
        editButton.setHideActionText(true);
        editButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        editButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        documentsToolBar.add(editButton);

        exportButton.setAction(exportQueryResultAction);
        exportButton.setFocusable(false);
        exportButton.setHideActionText(true);
        exportButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        exportButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        documentsToolBar.add(exportButton);
        documentsToolBar.add(jSeparator1);

        refreshDocumentsButton.setAction(refreshDocumentsAction);
        refreshDocumentsButton.setFocusable(false);
        refreshDocumentsButton.setHideActionText(true);
        refreshDocumentsButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        refreshDocumentsButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        documentsToolBar.add(refreshDocumentsButton);

        navFirstButton.setAction(navFirstAction);
        navFirstButton.setEnabled(false);
        navFirstButton.setHideActionText(true);
        documentsToolBar.add(navFirstButton);

        navLeftButton.setAction(navLeftAction);
        navLeftButton.setEnabled(false);
        navLeftButton.setHideActionText(true);
        documentsToolBar.add(navLeftButton);

        navRightButton.setAction(navRightAction);
        navRightButton.setEnabled(false);
        navRightButton.setFocusable(false);
        navRightButton.setHideActionText(true);
        navRightButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        navRightButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        documentsToolBar.add(navRightButton);

        navLastButton.setAction(navLastAction);
        navLastButton.setEnabled(false);
        navLastButton.setFocusable(false);
        navLastButton.setHideActionText(true);
        navLastButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        navLastButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        documentsToolBar.add(navLastButton);

        sortFieldsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/netbeans/modules/mongodb/images/sort-alph-asc.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(sortFieldsButton, org.openide.util.NbBundle.getMessage(CollectionResultPanel.class, "CollectionResultPanel.sortFieldsButton.text")); // NOI18N
        sortFieldsButton.setToolTipText(org.openide.util.NbBundle.getMessage(CollectionResultPanel.class, "CollectionResultPanel.sortFieldsButton.toolTipText")); // NOI18N
        sortFieldsButton.setFocusable(false);
        sortFieldsButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        sortFieldsButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        sortFieldsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sortFieldsButtonActionPerformed(evt);
            }
        });
        documentsToolBar.add(sortFieldsButton);
        documentsToolBar.add(jSeparator2);

        org.openide.awt.Mnemonics.setLocalizedText(pageSizeLabel, org.openide.util.NbBundle.getMessage(CollectionResultPanel.class, "CollectionResultPanel.pageSizeLabel.text")); // NOI18N
        documentsToolBar.add(pageSizeLabel);

        pageSizeField.setMaximumSize(new java.awt.Dimension(40, 2147483647));
        pageSizeField.setMinimumSize(new java.awt.Dimension(40, 20));
        pageSizeField.setPreferredSize(new java.awt.Dimension(40, 20));
        pageSizeField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pageSizeFieldActionPerformed(evt);
            }
        });
        documentsToolBar.add(pageSizeField);
        documentsToolBar.add(jSeparator3);
        documentsToolBar.add(totalDocumentsLabel);
        documentsToolBar.add(pageCountLabel);

        add(documentsToolBar, java.awt.BorderLayout.NORTH);

        resultPanel.setLayout(new java.awt.CardLayout());

        treeTableScrollPane.setViewportView(resultTreeTable);

        resultPanel.add(treeTableScrollPane, "TREE_TABLE");

        flatTableScrollPane.setViewportView(resultFlatTable);

        resultPanel.add(flatTableScrollPane, "FLAT_TABLE");

        add(resultPanel, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    private void pageSizeFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pageSizeFieldActionPerformed
        final int pageSize = Integer.parseInt(pageSizeField.getText());
        changePageSize(pageSize);
    }//GEN-LAST:event_pageSizeFieldActionPerformed

    private void sortFieldsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sortFieldsButtonActionPerformed
        treeTableModel.setSortDocumentsFields(sortFieldsButton.isSelected());
        textView.setSortDocumentsFields(sortFieldsButton.isSelected());
        flatTableModel.setSortDocumentsFields(sortFieldsButton.isSelected());
        refreshResults();
    }//GEN-LAST:event_sortFieldsButtonActionPerformed

    private final Action addDocumentAction = new AddDocumentAction(this);

    private final Action deleteSelectedDocumentAction = new DeleteSelectedDocumentAction(this);

    private final EditDocumentAction editDocumentAction = new EditDocumentAction(this);

    private final Action editSelectedDocumentAction = new EditSelectedDocumentAction(this);

    private final EditBsonPropertyNodeAction editBsonPropertyNodeAction = new EditBsonPropertyNodeAction(this, null);

    private final EditBsonValueNodeAction editBsonValueNodeAction = new EditBsonValueNodeAction(this, null);

    private final Action refreshDocumentsAction = new RefreshDocumentsAction(this);

    private final Action navFirstAction = new NavFirstAction(this);

    private final Action navLeftAction = new NavLeftAction(this);

    private final Action navRightAction = new NavRightAction(this);

    private final Action navLastAction = new NavLastAction(this);

    private final Action exportQueryResultAction = new ExportQueryResultAction(this);

    private final Action treeTableViewAction = ChangeResultViewAction.create(this, ResultView.TREE_TABLE);

    private final Action flatTableViewAction = ChangeResultViewAction.create(this, ResultView.FLAT_TABLE);

    private final Action treeViewAction = ChangeResultViewAction.create(this, ResultView.TEXT);

    private final Action collapseTreeAction = new CollapseAllDocumentsAction(this);

    private final Action expandTreeAction = new ExpandAllDocumentsAction(this);

    private final PropertyNodeAction usePropertySetFindFilterAction = new UsePropertySetFindFilterAction();

    private final PropertyNodeAction usePropertySetFindProjectionAction = new UsePropertySetFindProjectionAction();

    private final PropertyNodeAction usePropertySetFindSortAscAction = new UsePropertySetFindSortAction(SortOrder.ASCENDING);

    private final PropertyNodeAction usePropertySetFindSortDescAction = new UsePropertySetFindSortAction(SortOrder.DESCENDING);

    private final PropertyNodeAction usePropertyAddFindFilterAction = new UsePropertyAddFindFilterAction();

    private final PropertyNodeAction usePropertyAddFindProjectionAction = new UsePropertyAddFindProjectionAction();

    private final PropertyNodeAction usePropertyAddFindSortAscAction = new UsePropertyAddFindSortAction(SortOrder.ASCENDING);

    private final PropertyNodeAction usePropertyAddFindSortDescAction = new UsePropertyAddFindSortAction(SortOrder.DESCENDING);

    public enum ResultView {

        FLAT_TABLE, TREE_TABLE, TEXT

    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addButton;
    private javax.swing.JButton collapseTreeButton;
    private javax.swing.JButton deleteButton;
    private javax.swing.JToolBar documentsToolBar;
    private javax.swing.JButton editButton;
    private javax.swing.JButton expandTreeButton;
    private javax.swing.JButton exportButton;
    private javax.swing.JScrollPane flatTableScrollPane;
    private javax.swing.JToggleButton flatTableViewButton;
    private javax.swing.JToolBar.Separator jSeparator1;
    private javax.swing.JToolBar.Separator jSeparator2;
    private javax.swing.JToolBar.Separator jSeparator3;
    private javax.swing.JToolBar.Separator jSeparator4;
    private javax.swing.JToolBar.Separator jSeparator5;
    private javax.swing.JButton navFirstButton;
    private javax.swing.JButton navLastButton;
    private javax.swing.JButton navLeftButton;
    private javax.swing.JButton navRightButton;
    private javax.swing.JLabel pageCountLabel;
    private javax.swing.JTextField pageSizeField;
    private javax.swing.JLabel pageSizeLabel;
    private javax.swing.JButton refreshDocumentsButton;
    @Getter
    private javax.swing.JTable resultFlatTable;
    private javax.swing.JPanel resultPanel;
    @Getter
    private org.jdesktop.swingx.JXTreeTable resultTreeTable;
    private javax.swing.ButtonGroup resultViewButtonGroup;
    private javax.swing.JToggleButton sortFieldsButton;
    private javax.swing.JToggleButton textViewButton;
    private javax.swing.JLabel totalDocumentsLabel;
    private javax.swing.JScrollPane treeTableScrollPane;
    private javax.swing.JToggleButton treeTableViewButton;
    // End of variables declaration//GEN-END:variables

    private JPopupMenu createTreeTableContextMenu(TreePath treePath) {
        final JPopupMenu menu = new JPopupMenu();
        if (treePath != null) {
            BsonValueNode selectedNode = (BsonValueNode) treePath.getLastPathComponent();
            final BsonValueNode documentRootNode = (BsonValueNode) treePath.getPathComponent(1);
            Action copyFullDocumentAction = new CopyFullDocumentToClipboardAction(documentRootNode.getValue().asDocument());
            if (selectedNode != documentRootNode) {
                if (selectedNode instanceof BsonPropertyNode) {
                    BsonPropertyNode propertyNode = (BsonPropertyNode) selectedNode;
                    BsonProperty property = propertyNode.getBsonProperty();
                    menu.add(new JMenuItem(new CopyKeyValuePairToClipboardAction(property)));
                    menu.add(new JMenuItem(new CopyKeyToClipboardAction(property)));
                    menu.add(new JMenuItem(new CopyValueToClipboardAction(property.getValue())));
                    menu.add(new JMenuItem(copyFullDocumentAction));
                    menu.addSeparator();

                    if (lookup.lookup(CollectionInfo.class) != null) {
                        JMenu queryWithPropertyMenu = new JMenu(Bundle.MENU_queryWithProperty());
                        menu.add(queryWithPropertyMenu);
                        usePropertySetFindFilterAction.setPropertyNode(propertyNode);
                        usePropertySetFindProjectionAction.setPropertyNode(propertyNode);
                        usePropertySetFindSortAscAction.setPropertyNode(propertyNode);
                        usePropertySetFindSortDescAction.setPropertyNode(propertyNode);
                        queryWithPropertyMenu.add(new JMenuItem(usePropertySetFindFilterAction));
                        queryWithPropertyMenu.add(new JMenuItem(usePropertySetFindProjectionAction));
                        queryWithPropertyMenu.add(new JMenuItem(usePropertySetFindSortAscAction));
                        queryWithPropertyMenu.add(new JMenuItem(usePropertySetFindSortDescAction));
                        if (currentResult instanceof FindResult) {
                            usePropertyAddFindFilterAction.setPropertyNode(propertyNode);
                            usePropertyAddFindProjectionAction.setPropertyNode(propertyNode);
                            usePropertyAddFindSortAscAction.setPropertyNode(propertyNode);
                            usePropertyAddFindSortDescAction.setPropertyNode(propertyNode);
                            queryWithPropertyMenu.addSeparator();
                            queryWithPropertyMenu.add(new JMenuItem(usePropertyAddFindFilterAction));
                            queryWithPropertyMenu.add(new JMenuItem(usePropertyAddFindProjectionAction));
                            queryWithPropertyMenu.add(new JMenuItem(usePropertyAddFindSortAscAction));
                            queryWithPropertyMenu.add(new JMenuItem(usePropertyAddFindSortDescAction));
                        }
                    }
                    if (isQuickEditableBsonValue(property.getValue())) {
                        menu.addSeparator();
                        editBsonPropertyNodeAction.setPropertyNode(propertyNode);
                        menu.add(new JMenuItem(editBsonPropertyNodeAction));
                    }
                } else {
                    BsonValue value = selectedNode.getValue();
                    menu.add(new JMenuItem(new CopyValueToClipboardAction(value)));
                    menu.add(new JMenuItem(copyFullDocumentAction));
                    if (isQuickEditableBsonValue(value)) {
                        menu.addSeparator();
                        editBsonValueNodeAction.setValueNode((BsonValueNode) selectedNode);
                        menu.add(new JMenuItem(editBsonValueNodeAction));
                    }
                }
            } else {
                menu.add(new JMenuItem(copyFullDocumentAction));
            }
            if (readOnly == false) {
                menu.addSeparator();
                menu.add(new JMenuItem(new EditSelectedDocumentAction(this)));
                menu.add(new JMenuItem(new DeleteSelectedDocumentAction(this)));
            }
            menu.addSeparator();
            
            if(selectedNode.isLeaf() == false) {
                menu.add(new JMenuItem(new ExpandNodeWithChildrenAction(this, treePath)));
                menu.addSeparator();
            }
            
        }
        
        menu.add(new JMenuItem(collapseTreeAction));
        menu.add(new JMenuItem(expandTreeAction));
        return menu;
    }

    private JPopupMenu createFlatTableContextMenu(int row, int column) {
        final JPopupMenu menu = new JPopupMenu();
        final BsonDocument document = getFlatTablePages().getCurrentPageItems().get(row);
        menu.add(new JMenuItem(new CopyFullDocumentToClipboardAction(document)));
        final DocumentsFlatTableModel model = (DocumentsFlatTableModel) resultFlatTable.getModel();
        final BsonProperty property = new BsonProperty(
            model.getColumnName(column),
            model.getValueAt(row, column));
        menu.add(new JMenuItem(new CopyKeyValuePairToClipboardAction(property)));
        menu.add(new JMenuItem(new CopyKeyToClipboardAction(property)));
        menu.add(new JMenuItem(new CopyValueToClipboardAction(property.getValue())));
        if (readOnly == false) {
            menu.addSeparator();
            menu.add(new JMenuItem(new EditSelectedDocumentAction(this)));
            menu.add(new JMenuItem(new DeleteSelectedDocumentAction(this)));
        }
        return menu;
    }

    private void dislayDocumentEditionShortcutHintIfNecessary() {
        if (displayDocumentEditionShortcutHint && readOnly == false) {
            NotificationDisplayer.getDefault().notify(
                Bundle.documentEditionShortcutHintTitle(),
                new ImageIcon(Images.EDIT_DOCUMENT_ICON),
                Bundle.documentEditionShortcutHintDetails(),
                new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    displayDocumentEditionShortcutHint = false;
                }
            }
            );
        }
    }

    TopComponent getParentTopComponent() {
        Container parent = getParent();
        while (parent != null && (parent instanceof TopComponent) == false) {
            parent = parent.getParent();
        }
        return (TopComponent) parent;
    }
    
    public boolean isDocumentsFieldsSorted() {
        return sortFieldsButton.isSelected();
    }

    public void loadPreferences() {
        final Preferences prefs = Prefs.of(Prefs.COLLECTION_RESULTS_PANEL);
        displayDocumentEditionShortcutHint = prefs.getBoolean("display-document-edition-shortcut-hint", true);
        final String resultViewPref = prefs.get("result-view", ResultView.TREE_TABLE.name());
        final ResultView rView = ResultView.valueOf(resultViewPref);
        resultViewButtons.get(rView).setSelected(true);
        changeResultView(rView);
        final int pageSize = prefs.getInt("result-view-table-page-size", getTreeTablePages().getPageSize());
        getTreeTablePages().setPageSize(pageSize);
        getFlatTablePages().setPageSize(pageSize);
        getTextViewPages().setPageSize(pageSize);
        pageSizeField.setText(String.valueOf(pageSize));
        boolean sortDocumentsField = prefs.getBoolean("sort-documents-fields", false);
        sortFieldsButton.setSelected(sortDocumentsField);
        treeTableModel.setSortDocumentsFields(sortDocumentsField);
        textView.setSortDocumentsFields(sortDocumentsField);
        flatTableModel.setSortDocumentsFields(sortDocumentsField);
    }

    public void writePreferences() {
        final Preferences prefs = Prefs.of(Prefs.COLLECTION_RESULTS_PANEL);
        prefs.putInt("result-view-table-page-size", getTreeTablePages().getPageSize());
        prefs.put("result-view", resultView.name());
        prefs.putBoolean("display-document-edition-shortcut-hint", displayDocumentEditionShortcutHint);
        prefs.putBoolean("sort-documents-fields", sortFieldsButton.isSelected());
        try {
            prefs.flush();
        } catch (BackingStoreException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    public static interface View {

        CollectionResultPages getPages();
    }

    abstract class PropertyNodeAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        @Getter
        @Setter
        private BsonPropertyNode propertyNode;

        public PropertyNodeAction(String name) {
            super(name);
        }

        BsonProperty getBsonProperty() {
            return propertyNode.getBsonProperty();
        }
    }

    private static final BsonInt32 ASC = new BsonInt32(1);

    private static final BsonInt32 DESC = new BsonInt32(-1);

    abstract class SortPropertyNodeAction extends PropertyNodeAction {

        private static final long serialVersionUID = 1L;

        @Getter
        private final SortOrder sortOrder;

        public SortPropertyNodeAction(String name, SortOrder sortOrder) {
            super(name);
            this.sortOrder = sortOrder;
        }

        BsonValue getSortValue() {
            return sortOrder == SortOrder.ASCENDING ? ASC : DESC;
        }
    }

    class UsePropertySetFindFilterAction extends PropertyNodeAction {

        private static final long serialVersionUID = 1L;

        public UsePropertySetFindFilterAction() {
            super(Bundle.ACTION_usePropertySetFindFilter());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Lookup lookup = getLookup();
            CollectionInfo collection = lookup.lookup(CollectionInfo.class);
            if (collection != null) {
                FindCriteria findCriteria = FindCriteria.builder().filter(getBsonProperty().asDocument()).build();
                CollectionView view = new CollectionView(collection, lookup, findCriteria);
                view.open();
                view.requestActive();
            }

        }

    }

    class UsePropertySetFindProjectionAction extends PropertyNodeAction {

        private static final long serialVersionUID = 1L;

        public UsePropertySetFindProjectionAction() {
            super(Bundle.ACTION_usePropertySetFindProjection());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Lookup lookup = getLookup();
            CollectionInfo collection = lookup.lookup(CollectionInfo.class);
            if (collection != null) {
                BsonDocument projection = new BsonDocument(getBsonProperty().getName(), BsonBoolean.TRUE);
                FindCriteria findCriteria = FindCriteria.builder().projection(projection).build();
                CollectionView view = new CollectionView(collection, lookup, findCriteria);
                view.open();
                view.requestActive();
            }

        }

    }

    class UsePropertySetFindSortAction extends SortPropertyNodeAction {

        private static final long serialVersionUID = 1L;

        public UsePropertySetFindSortAction(SortOrder sortOrder) {
            super(Bundle.ACTION_usePropertySetFindSort(sortOrder.name().toLowerCase()), sortOrder);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Lookup lookup = getLookup();
            CollectionInfo collection = lookup.lookup(CollectionInfo.class);
            if (collection != null) {
                BsonDocument sort = new BsonDocument(getBsonProperty().getName(), getSortValue());
                FindCriteria findCriteria = FindCriteria.builder().sort(sort).build();
                CollectionView view = new CollectionView(collection, lookup, findCriteria);
                view.open();
                view.requestActive();
            }

        }

    }

    class UsePropertyAddFindFilterAction extends PropertyNodeAction {

        private static final long serialVersionUID = 1L;

        public UsePropertyAddFindFilterAction() {
            super(Bundle.ACTION_usePropertyAddFindFilter());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // cast, if not a find result, this action should not be enabled
            FindResult currentFindResult = (FindResult) currentResult;
            FindCriteria currentCriteria = currentFindResult.getFindCriteria();
            BsonDocument filter = currentCriteria.getFilter().clone();
            BsonProperty property = getBsonProperty();
            filter.put(property.getName(), property.getValue());

            // cast, if not a find query view, this action should not be enabled
            CollectionView parent = (CollectionView) getParentTopComponent();
            parent.setFindCriteria(currentCriteria.copy().filter(filter).build());
        }

    }

    class UsePropertyAddFindProjectionAction extends PropertyNodeAction {

        private static final long serialVersionUID = 1L;

        public UsePropertyAddFindProjectionAction() {
            super(Bundle.ACTION_usePropertyAddFindProjection());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // cast, if not a find result, this action should not be enabled
            FindResult currentFindResult = (FindResult) currentResult;
            FindCriteria currentCriteria = currentFindResult.getFindCriteria();
            BsonDocument projection = currentCriteria.getProjection().clone();
            BsonProperty property = getBsonProperty();
            projection.put(property.getName(), BsonBoolean.TRUE);

            // cast, if not a find query view, this action should not be enabled
            CollectionView parent = (CollectionView) getParentTopComponent();
            parent.setFindCriteria(currentCriteria.copy().projection(projection).build());
        }

    }

    class UsePropertyAddFindSortAction extends SortPropertyNodeAction {

        private static final long serialVersionUID = 1L;

        public UsePropertyAddFindSortAction(SortOrder sortOrder) {
            super(Bundle.ACTION_usePropertyAddFindSort(sortOrder.name().toLowerCase()), sortOrder);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // cast, if not a find result, this action should not be enabled
            FindResult currentFindResult = (FindResult) currentResult;
            FindCriteria currentCriteria = currentFindResult.getFindCriteria();
            BsonDocument sort = currentCriteria.getSort().clone();
            sort.put(getBsonProperty().getName(), getSortValue());

            // cast, if not a find query view, this action should not be enabled
            CollectionView parent = (CollectionView) getParentTopComponent();
            parent.setFindCriteria(currentCriteria.copy().sort(sort).build());
        }

    }

    public static final Comparator<Map.Entry<String, BsonValue>> DOCUMENT_FIELD_ENTRY_KEY_COMPARATOR = new Comparator<Map.Entry<String, BsonValue>>() {
        
        @Override
        public int compare(Map.Entry<String, BsonValue> entry1, Map.Entry<String, BsonValue> entry2) {
            return entry1.getKey().compareTo(entry2.getKey());
        }
    };

}
