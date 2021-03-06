/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.netbeans.modules.mongodb.ui.explorer;

import org.netbeans.modules.mongodb.properties.LocalizedProperties;
import org.netbeans.modules.mongodb.resources.Images;
import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import org.netbeans.modules.mongodb.ui.windows.MapReduceTopComponent;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;
import org.netbeans.modules.mongodb.DbInfo;
import org.netbeans.modules.mongodb.MongoConnection;
import org.netbeans.modules.mongodb.api.MongoErrorCode;
import org.netbeans.modules.mongodb.native_tools.MongoNativeToolsAction;
import org.netbeans.modules.mongodb.ui.util.CollectionNameValidator;
import org.netbeans.modules.mongodb.ui.util.DialogNotification;
import org.netbeans.modules.mongodb.ui.util.TopComponentUtils;
import org.netbeans.modules.mongodb.ui.windows.CollectionView;
import org.netbeans.modules.mongodb.ui.wizards.ExportWizardAction;
import org.netbeans.modules.mongodb.ui.wizards.ImportWizardAction;
import org.netbeans.modules.mongodb.util.Tasks;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Task;
import org.openide.util.TaskListener;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.openide.windows.TopComponent;

/**
 *
 * @author Tim Boudreau
 * @author Yann D'Isanto
 */
@Messages({
    "ACTION_AddCollection=Add Collection",
    "ACTION_DropDatabase=Drop Database",
    "ACTION_Export=Export",
    "ACTION_Import=Import",
    "addCollectionText=Collection name:",
    "# {0} - collection name",
    "collectionAlreadyExists=Collection ''{0}'' already exists",
    "# {0} - database name",
    "dropDatabaseConfirmText=Permanently drop ''{0}'' database?",
    "# {0} - collection name",
    "TASK_addCollection=creating '{0}' collection",
    "# {0} - database name",
    "TASK_dropDatabase=dropping '{0}' database"
})
final class DBNode extends AbstractNode {

    private final CollectionNodesFactory childFactory;

    DBNode(DbInfo info) {
        this(info, new InstanceContent());
    }

    DBNode(DbInfo info, InstanceContent content) {
        this(info, content, new AbstractLookup(content));
    }

    DBNode(DbInfo info, InstanceContent content, AbstractLookup lkp) {
        this(info, content, new ProxyLookup(info.getLookup(), lkp, Lookups.fixed(info)));
    }

    DBNode(DbInfo info, InstanceContent content, ProxyLookup lkp) {
        this(info, content, lkp, new CollectionNodesFactory(lkp));
    }

    DBNode(DbInfo info, InstanceContent content, ProxyLookup lookup, CollectionNodesFactory childFactory) {
        super(Children.create(childFactory, true), lookup);
        this.childFactory = childFactory;
        content.add(info, new MongoDatabaseConverter());
        setName(info.getDbName());
        setDisplayName(info.getDbName());
        setIconBaseWithExtension(Images.DB_ICON_PATH);
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = Sheet.createDefault();
        Sheet.Set set = Sheet.createPropertiesSet();
        MongoDatabase db = getLookup().lookup(MongoDatabase.class);
        BsonDocument commandDocument = new BsonDocument("dbStats", new BsonInt32(1)).append("scale", new BsonInt32(1));
        try {
            Document result = db.runCommand(commandDocument);
            set.put(new LocalizedProperties(DBNode.class).fromDocument(result).toArray());
            sheet.put(set);
        } catch (MongoException ex) {
            if (MongoErrorCode.of(ex) != MongoErrorCode.Unauthorized) {
                DialogNotification.error(ex);
            }
        }
        return sheet;
    }

    @Override
    public Action[] getActions(boolean ignored) {
        final List<Action> actions = new LinkedList<>();
        actions.add(new AddCollectionAction());
        actions.add(new RefreshChildrenAction(childFactory));
        actions.add(new DropDatabaseAction());
        actions.add(null);
        actions.add(new MongoNativeToolsAction(getLookup()));
        actions.add(null);
        actions.add(new ExportWizardAction(getLookup()));
        actions.add(new ImportWizardAction(getLookup(), new Runnable() {

            @Override
            public void run() {
                refreshChildren();
            }
        }));
        final Action[] orig = super.getActions(ignored);
        if (orig.length > 0) {
            actions.add(null);
        }
        actions.addAll(Arrays.asList(orig));
        return actions.toArray(new Action[actions.size()]);
    }

    public void refreshChildren() {
        childFactory.refresh();
    }

    private class MongoDatabaseConverter implements InstanceContent.Convertor<DbInfo, MongoDatabase> {

        @Override
        public MongoDatabase convert(DbInfo t) {
            DbInfo info = getLookup().lookup(DbInfo.class);
            MongoConnection connection = getLookup().lookup(MongoConnection.class);
            return connection.getClient().getDatabase(info.getDbName());
        }

        @Override
        public Class<? extends MongoDatabase> type(DbInfo t) {
            return MongoDatabase.class;
        }

        @Override
        public String id(DbInfo t) {
            return t.getDbName();
        }

        @Override
        public String displayName(DbInfo t) {
            return id(t);
        }
    }

    public final class AddCollectionAction extends AbstractAction {

        public AddCollectionAction() {
            super(Bundle.ACTION_AddCollection());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String input = DialogNotification.validatingInput(
                    Bundle.addCollectionText(),
                    Bundle.ACTION_AddCollection(),
                    new CollectionNameValidator(getLookup()));
            if (input != null) {
                final String collectionName = input.trim();
                Tasks.create(Bundle.TASK_addCollection(collectionName), new Runnable() {

                    @Override
                    public void run() {
                        try {
                            MongoDatabase db = getLookup().lookup(MongoDatabase.class);
                            db.createCollection(collectionName, new CreateCollectionOptions().capped(false));
                        } catch (MongoException ex) {
                            DialogNotification.error(ex);
                        }
                    }
                }).execute().addTaskListener(new TaskListener() {

                    @Override
                    public void taskFinished(Task task) {
                        childFactory.refresh();
                    }
                });

            }
        }
    }

    public final class DropDatabaseAction extends AbstractAction {

        public DropDatabaseAction() {
            super(Bundle.ACTION_DropDatabase());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            final MongoDatabase db = getLookup().lookup(MongoDatabase.class);
            if (DialogNotification.confirm(Bundle.dropDatabaseConfirmText(db.getName()))) {
                Tasks.create(Bundle.TASK_dropDatabase(db.getName()), new Runnable() {

                    @Override
                    public void run() {
                        try {
                            db.drop();
                        } catch (MongoException ex) {
                            DialogNotification.error(ex);
                        }
                    }
                }).execute().addTaskListener(new TaskListener() {

                    @Override
                    public void taskFinished(Task task) {
                        ((ConnectionNode) getParentNode()).refreshChildren();
                        DbInfo dbInfo = getLookup().lookup(DbInfo.class);
                        for (TopComponent topComponent : TopComponentUtils.findAll(dbInfo, CollectionView.class, MapReduceTopComponent.class)) {
                            topComponent.close();
                        }
                    }
                });
            }
        }
    }
}
