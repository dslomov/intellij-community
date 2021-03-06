package com.jetbrains.edu.coursecreator.ui;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;

import javax.swing.*;
import java.awt.*;

public class CCItemPositionPanel extends JPanel {
  private JPanel myPanel;
  private JBRadioButton myBeforeButton;
  private JBRadioButton myAfterButton;
  private JBLabel mySpecifyPositionLabel;

  public CCItemPositionPanel(String itemName, String thresholdName, int thresholdNum) {
    this.add(myPanel, BorderLayout.CENTER);
    mySpecifyPositionLabel.setText("Specify " + itemName + " position:");
    String postfix = itemName + " " + thresholdNum + " '" + thresholdName + "'";
    ButtonGroup group = new ButtonGroup();
    group.add(myBeforeButton);
    group.add(myAfterButton);
    myBeforeButton.setText("before " + postfix);
    myAfterButton.setText("after " + postfix);
    myBeforeButton.setSelected(true);
  }

  public int getIndexDelta() {
    return myBeforeButton.isSelected() ? 0 : 1;
  }
}
