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
package org.netbeans.modules.mongodb.ui.components.result_panel.actions;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import javax.swing.ImageIcon;
import org.netbeans.modules.mongodb.resources.Images;
import org.netbeans.modules.mongodb.ui.components.CollectionResultPanel;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Yann D'Isanto
 */
@Messages({
    "ACTION_navLast=Last Page",
    "ACTION_navLast_tooltip=Last Page"
})
public final class NavLastAction extends QueryResultPanelAction {

    private static final long serialVersionUID = 1L;

    public NavLastAction(CollectionResultPanel resultPanel) {
        super(resultPanel,
            Bundle.ACTION_navLast(),
            new ImageIcon(Images.NAV_LAST_ICON),
            Bundle.ACTION_navLast_tooltip());
        enabled = false;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        REQUEST_PROCESSOR.post(new Runnable() {

            @Override
            public void run() {
                getResultPanel().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                getResultPanel().getResultPages().moveLast();
                getResultPanel().updatePagination();
            }
        });
    }
}
