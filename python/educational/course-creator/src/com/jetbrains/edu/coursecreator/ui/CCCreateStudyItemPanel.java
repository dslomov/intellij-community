package com.jetbrains.edu.coursecreator.ui;

import javax.swing.*;
import java.awt.*;

public class CCCreateStudyItemPanel extends JPanel {
  private final String myItemName;
  private JPanel myPanel;
  private JTextField myNameField;
  private CCItemPositionPanel myPositionalPanel;
  private String myThresholdName;
  private int myThresholdIndex;

  public CCCreateStudyItemPanel(String itemName, String thresholdName, int thresholdIndex) {
    myThresholdName = thresholdName;
    myThresholdIndex = thresholdIndex;
    myItemName = itemName;
    myNameField.setText(itemName + thresholdIndex);
    add(myPanel, BorderLayout.CENTER);
  }

  private void createUIComponents() {
    myPositionalPanel = new CCItemPositionPanel(myItemName, myThresholdName, myThresholdIndex);
  }

  public String getItemName() {
    return myNameField.getText();
  }

  public int getIndexDelta() {
    return myPositionalPanel.getIndexDelta();
  }
}
