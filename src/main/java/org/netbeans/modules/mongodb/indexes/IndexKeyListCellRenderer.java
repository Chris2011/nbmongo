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
package org.netbeans.modules.mongodb.indexes;

import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JList;
import javax.swing.JTextPane;
import javax.swing.ListCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import org.netbeans.modules.mongodb.indexes.Index.Type;
import org.netbeans.modules.mongodb.resources.Images;
import org.openide.util.Exceptions;

/**
 *
 * @author Yann D'Isanto
 */
public class IndexKeyListCellRenderer extends JTextPane implements ListCellRenderer<Index.Key> {
    
    private static final long serialVersionUID = 1L;

    private final Style fieldsStyle;

    private final Map<Type, Style> sortStyles;

    public IndexKeyListCellRenderer() {
        StyledDocument document = getStyledDocument();
        Style def = StyleContext.getDefaultStyleContext().
            getStyle(StyleContext.DEFAULT_STYLE);
        fieldsStyle = document.addStyle("fields", def);
        StyleConstants.setAlignment(fieldsStyle, StyleConstants.ALIGN_CENTER);

        Style ascIcon = document.addStyle("sortAsc", def);
        StyleConstants.setAlignment(ascIcon, StyleConstants.ALIGN_CENTER);
        StyleConstants.setIcon(ascIcon, new ImageIcon(Images.SORT_ASC_ICON, Type.ASCENDING.toString()));

        Style descIcon = document.addStyle("sortDesc", def);
        StyleConstants.setAlignment(descIcon, StyleConstants.ALIGN_CENTER);
        StyleConstants.setIcon(descIcon, new ImageIcon(Images.SORT_DESC_ICON, Type.DESCENDING.toString()));
        
        Style geo2dIcon = document.addStyle("geo2d", def);
        StyleConstants.setAlignment(geo2dIcon, StyleConstants.ALIGN_CENTER);
        StyleConstants.setIcon(geo2dIcon, new ImageIcon(Images.MAP_ICON, Type.GEOSPATIAL_2D.toString()));
        
        Style geo2dSphereIcon = document.addStyle("geo2dSphere", def);
        StyleConstants.setAlignment(geo2dSphereIcon, StyleConstants.ALIGN_CENTER);
        StyleConstants.setIcon(geo2dSphereIcon, new ImageIcon(Images.WORLD_ICON, Type.GEOSPATIAL_2DSPHERE.toString()));
        
        Style geoHaystackIcon = document.addStyle("geoHaystack", def);
        StyleConstants.setAlignment(geoHaystackIcon, StyleConstants.ALIGN_CENTER);
        StyleConstants.setIcon(geoHaystackIcon, new ImageIcon(Images.MAP_MAGNIFY_ICON, Type.GEOSPATIAL_HAYSTACK.toString()));
        
        Style hashedIcon = document.addStyle("hashed", def);
        StyleConstants.setAlignment(hashedIcon, StyleConstants.ALIGN_CENTER);
        StyleConstants.setIcon(hashedIcon, new ImageIcon(Images.SHADING_ICON, Type.HASHED.toString()));
        
        Style textIcon = document.addStyle("text", def);
        StyleConstants.setAlignment(textIcon, StyleConstants.ALIGN_CENTER);
        StyleConstants.setIcon(textIcon, new ImageIcon(Images.TEXT_ALIGN_JUSTIFY_ICON, Type.TEXT.toString()));
        
        sortStyles = new HashMap<>(7);
        sortStyles.put(Type.ASCENDING, ascIcon);
        sortStyles.put(Type.DESCENDING, descIcon);
        sortStyles.put(Type.GEOSPATIAL_2D, geo2dIcon);
        sortStyles.put(Type.GEOSPATIAL_2DSPHERE, geo2dSphereIcon);
        sortStyles.put(Type.GEOSPATIAL_HAYSTACK, geoHaystackIcon);
        sortStyles.put(Type.HASHED, hashedIcon);
        sortStyles.put(Type.TEXT, textIcon);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Index.Key> list, Index.Key key, int index, boolean isSelected, boolean cellHasFocus) {
        if (isSelected) {
            setForeground(list.getSelectionForeground());
            setBackground(list.getSelectionBackground());
        } else {
            setForeground(list.getForeground());
            setBackground(list.getBackground());
        }
        setText("");
        if (key == null) {
            return this;
        }
        StyledDocument document = getStyledDocument();
        try {
            document.insertString(document.getLength(), key.getField(), fieldsStyle);
            document.insertString(document.getLength(), " ", fieldsStyle);
            document.insertString(document.getLength(), " ", sortStyles.get(key.getType()));
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
        return this;
    }
}
