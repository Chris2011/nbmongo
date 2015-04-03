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
package org.netbeans.modules.mongodb.ui.windows.queryresultpanel.actions;

import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.mongodb.util.JSON;
import java.awt.event.ActionEvent;
import javax.swing.ImageIcon;
import lombok.Getter;
import lombok.Setter;
import org.netbeans.modules.mongodb.resources.Images;
import org.netbeans.modules.mongodb.ui.util.JsonEditor;
import org.netbeans.modules.mongodb.ui.windows.QueryResultPanel;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Yann D'Isanto
 */
@Messages({
    "editDocumentTitle=Edit document",
    "ACTION_editDocument=Edit document",
    "ACTION_editDocument_tooltip=Edit Document"
})
public class EditDocumentAction extends QueryResultPanelAction {

    private static final long serialVersionUID = 1L;

    @Getter
    @Setter
    private DBObject document;

    public EditDocumentAction(QueryResultPanel resultPanel) {
        super(resultPanel,
            Bundle.ACTION_editDocument(),
            new ImageIcon(Images.EDIT_DOCUMENT_ICON),
            Bundle.ACTION_editDocument_tooltip());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (document == null) {
            return;
        }
        final DBObject modifiedDocument = JsonEditor.show(
            Bundle.editDocumentTitle(),
            JSON.serialize(document));
        if (modifiedDocument != null) {
            try {
                final DBCollection dbCollection = getResultPanel().getLookup().lookup(DBCollection.class);
                dbCollection.save(modifiedDocument);
                getResultPanel().getResultCache().editObject(document, modifiedDocument);
            } catch (MongoException ex) {
                DialogDisplayer.getDefault().notify(
                    new NotifyDescriptor.Message(ex.getLocalizedMessage(), NotifyDescriptor.ERROR_MESSAGE));
            }
        }
    }
}
