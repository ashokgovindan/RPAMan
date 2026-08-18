package rpa.rpaman;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Renders tree rows as icon + optional checkbox + label, with a rounded
 * selection pill instead of the default rectangular highlight.
 */
public class CheckBoxTreeCellRenderer extends JPanel implements TreeCellRenderer {

    private final JCheckBox checkBox = new JCheckBox();
    private final JLabel label = new JLabel();

    private final Icon rootIcon = AppIcons.folder(16, "App.accent");
    private final Icon projectIcon = AppIcons.layers(16, "App.accent");
    private final Icon stepIcon = AppIcons.file(15, "App.subtleForeground");
    private final Icon stepDoneIcon = AppIcons.check(15, "App.accent");
    private final Icon changeRequestIcon = AppIcons.inbox(15, "App.accent");
    private final Icon deploymentIcon = AppIcons.upload(15, "App.accent");
    private final Icon serviceAccountIcon = AppIcons.key(15, "App.accent");

    private boolean selected;

    public CheckBoxTreeCellRenderer() {
        super(new BorderLayout(2, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(1, 4, 1, 8));

        checkBox.setOpaque(false);
        checkBox.setBorder(new EmptyBorder(0, 0, 0, 4));
        checkBox.setFocusable(false);

        label.setOpaque(false);
        label.setIconTextGap(7);

        add(checkBox, BorderLayout.WEST);
        add(label, BorderLayout.CENTER);
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                  boolean isSelected, boolean expanded,
                                                  boolean leaf, int row, boolean hasFocus) {

        this.selected = isSelected;

        Object userObject = (value instanceof DefaultMutableTreeNode)
                ? ((DefaultMutableTreeNode) value).getUserObject()
                : value;

        if (userObject instanceof CheckableItem) {
            CheckableItem item = (CheckableItem) userObject;
            checkBox.setVisible(true);
            checkBox.setSelected(item.isSelected());
            label.setText(item.getText());
            label.setIcon(item.isSelected() ? stepDoneIcon : stepIcon);
            label.setFont(baseFont(tree).deriveFont(Font.PLAIN));
            label.setForeground(item.isSelected()
                    ? ThemeManager.accent()
                    : ThemeManager.color("Tree.foreground", Color.DARK_GRAY));
        } else if (userObject instanceof SectionItem) {
            // Delivery sections: no checkbox, but visually distinct from steps
            SectionItem section = (SectionItem) userObject;
            checkBox.setVisible(false);
            label.setText(section.getText());
            label.setIcon(iconFor(section.getKind()));
            label.setFont(baseFont(tree).deriveFont(Font.PLAIN));
            label.setForeground(ThemeManager.color("Tree.foreground", Color.DARK_GRAY));
        } else {
            checkBox.setVisible(false);
            label.setText(String.valueOf(userObject));
            boolean isRoot = (value instanceof DefaultMutableTreeNode)
                    && ((DefaultMutableTreeNode) value).isRoot();
            label.setIcon(isRoot ? rootIcon : projectIcon);
            label.setFont(baseFont(tree).deriveFont(Font.BOLD));
            label.setForeground(ThemeManager.color("Tree.foreground", Color.DARK_GRAY));
        }

        setEnabled(tree.isEnabled());
        return this;
    }

    private Icon iconFor(SectionItem.Kind kind) {
        switch (kind) {
            case CHANGE_REQUESTS:
                return changeRequestIcon;
            case SERVICE_ACCOUNTS:
                return serviceAccountIcon;
            case DEPLOYMENTS:
            default:
                return deploymentIcon;
        }
    }

    private Font baseFont(JTree tree) {
        Font font = tree.getFont();
        return font != null ? font : UIManager.getFont("Tree.font");
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (selected) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(ThemeManager.color("Tree.selectionBackground",
                        ThemeManager.blend(ThemeManager.accent(), ThemeManager.card(), 0.18f)));
                g.fill(new RoundRectangle2D.Double(0, 1, getWidth() - 1.0, getHeight() - 2.0, 9, 9));
            } finally {
                g.dispose();
            }
        }
        super.paintComponent(graphics);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = Math.max(size.height, 24);
        return size;
    }
}
