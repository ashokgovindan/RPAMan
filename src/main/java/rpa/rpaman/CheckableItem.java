package rpa.rpaman;

public class CheckableItem {
    private String text;
    private boolean isSelected;

    public CheckableItem(String text) {
        this.text = text;
        this.isSelected = false;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }

    @Override
    public String toString() { return text; }
}