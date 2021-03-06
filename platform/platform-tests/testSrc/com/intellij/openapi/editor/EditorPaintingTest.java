/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.MockFontLayoutService;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.ui.Graphics2DDelegate;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

@TestDataPath("$CONTENT_ROOT/testData/editor/painting")
public class EditorPaintingTest extends AbstractEditorTest {
  private static final boolean GENERATE_MISSING_TEST_DATA = false;

  public void testWholeLineHighlighterAtDocumentEnd() throws Exception {
    initText("foo");
    myEditor.getMarkupModel().addRangeHighlighter(0, 3, HighlighterLayer.WARNING,
                                                  new TextAttributes(null, Color.red, null, null, Font.PLAIN),
                                                  HighlighterTargetArea.LINES_IN_RANGE);
    checkResult();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    FontLayoutService.setInstance(new MockFontLayoutService(BitmapFont.CHAR_WIDTH, BitmapFont.CHAR_HEIGHT, BitmapFont.CHAR_DESCENT));
    Registry.get("editor.new.rendering").setValue(true);
  }

  @Override
  protected void tearDown() throws Exception {
    Registry.get("editor.new.rendering").setValue(false);
    FontLayoutService.setInstance(null);
    super.tearDown();
  }

  private void checkResult() throws IOException {
    checkResult(getTestName(true) + ".png");
  }

  private void checkResult(@TestDataFile String expectedResultFileName) throws IOException {
    myEditor.putUserData(EditorImpl.DO_DOCUMENT_UPDATE_TEST, Boolean.TRUE);
    myEditor.getSettings().setAdditionalLinesCount(0);
    myEditor.getSettings().setAdditionalColumnsCount(1);
    JComponent editorComponent = myEditor.getContentComponent();
    Dimension size = editorComponent.getPreferredSize();
    editorComponent.setSize(size);
    //noinspection UndesirableClassUsage
    BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
    BitmapFont bitmapFont = BitmapFont.loadFromFile(getFontFile());
    MyGraphics graphics = new MyGraphics(image.createGraphics(), bitmapFont);
    try {
      editorComponent.paint(graphics);
    }
    finally {
      graphics.dispose();
    }

    File fileWithExpectedResult = getTestDataFile(expectedResultFileName);
    if (fileWithExpectedResult.exists()) {
      BufferedImage expectedResult = ImageIO.read(fileWithExpectedResult);
      if (expectedResult.getWidth() != image.getWidth()) {
        fail("Unexpected image width", fileWithExpectedResult, image);
      }
      if (expectedResult.getHeight() != image.getHeight()) {
        fail("Unexpected image height", fileWithExpectedResult, image);
      }
      for (int i = 0; i < expectedResult.getWidth(); i++) {
        for (int j = 0; j < expectedResult.getHeight(); j++) {
          if (expectedResult.getRGB(i, j) != image.getRGB(i, j)) {
            fail("Unexpected image contents", fileWithExpectedResult, image);
          }
        }
      }
    }
    else {
      if (GENERATE_MISSING_TEST_DATA) {
        ImageIO.write(image, "png", fileWithExpectedResult);
      }
      else {
        fail("Test data is missing", fileWithExpectedResult, image);
      }
    }
  }

  private void fail(String message, File expectedResultsFile, BufferedImage actualImage) throws IOException {
    File savedImage = FileUtil.createTempFile(getName(), ".png", false);
    addTmpFileToKeep(savedImage);
    ImageIO.write(actualImage, "png", savedImage);
    throw new FileComparisonFailure(message, expectedResultsFile.getAbsolutePath(), savedImage.getAbsolutePath(),
                                    expectedResultsFile.getAbsolutePath(), savedImage.getAbsolutePath());
  }

  private static File getFontFile() {
    return getTestDataFile("_font.png");
  }

  private static File getTestDataFile(String fileName) {
    return new File(PathManagerEx.findFileUnderCommunityHome("platform/platform-tests/testData/editor/painting"), fileName);
  }

  // renders font characters to be used for text painting in tests (to make font rendering platform-independent)
  public static void main(String[] args) throws Exception {
    Font font = Font.createFont(Font.TRUETYPE_FONT, EditorPaintingTest.class.getResourceAsStream("/fonts/Inconsolata.ttf"));
    BitmapFont bitmapFont = BitmapFont.createFromFont(font);
    bitmapFont.saveToFile(getFontFile());
  }

  public static class MyGraphics extends Graphics2DDelegate {
    private final BitmapFont myBitmapFont;

    public MyGraphics(Graphics2D g2d, BitmapFont bitmapFont) {
      super(g2d);
      myBitmapFont = bitmapFont;
    }

    @Override
    public void addRenderingHints(Map hints) {
    }

    @Override
    public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
    }

    @Override
    public void setRenderingHints(Map hints) {
    }

    @NotNull
    @Override
    public Graphics create() {
      return new MyGraphics((Graphics2D)myDelegate.create(), myBitmapFont);
    }

    @Override
    public void drawGlyphVector(GlyphVector g, float x, float y) {
      for (int i = 0; i < g.getNumGlyphs(); i++) {
        drawChar((char)g.getGlyphCode(i),(int)x, (int)y);
        x += BitmapFont.CHAR_WIDTH;
      }
    }

    @Override
    public void drawChars(char[] data, int offset, int length, int x, int y) {
      for (int i = offset; i < offset + length; i++) {
        drawChar(data[i], x, y);
        x += BitmapFont.CHAR_WIDTH;
      }
    }

    private void drawChar(char c, int x, int y) {
      myBitmapFont.draw(myDelegate, c, x, y);
    }
  }

  // font which, once created, should be rendered identically on all platforms
  private static class BitmapFont {
    private static final float FONT_SIZE = 12;
    private static final int CHAR_WIDTH = 10;
    private static final int CHAR_HEIGHT = 12;
    private static final int CHAR_DESCENT = 2;
    private static final int NUM_CHARACTERS = 128;

    private final BufferedImage myImage;

    private BitmapFont(BufferedImage image) {
      myImage = image;
    }

    public static BitmapFont createFromFont(Font font) {
      font = font.deriveFont(FONT_SIZE);
      int imageWidth = CHAR_WIDTH * NUM_CHARACTERS;
      //noinspection UndesirableClassUsage
      BufferedImage image = new BufferedImage(imageWidth, CHAR_HEIGHT, BufferedImage.TYPE_BYTE_BINARY);
      Graphics2D g = image.createGraphics();
      g.setColor(Color.white);
      g.fillRect(0, 0, imageWidth, CHAR_HEIGHT);
      g.setColor(Color.black);
      g.setFont(font);
      char[] c = new char[1];
      for (c[0] = 0; c[0] < NUM_CHARACTERS; c[0]++) {
        if (font.canDisplay(c[0])) {
          int x = c[0] * CHAR_WIDTH;
          g.setClip(x, 0, CHAR_WIDTH, CHAR_HEIGHT);
          g.drawChars(c, 0, 1, x, CHAR_HEIGHT - CHAR_DESCENT);
        }
      }
      g.dispose();
      return new BitmapFont(image);
    }

    public static BitmapFont loadFromFile(File file) throws IOException {
      BufferedImage image = ImageIO.read(file);
      return new BitmapFont(image);
    }

    public void saveToFile(File file) throws IOException {
      ImageIO.write(myImage, "png", file);
    }

    public void draw(Graphics2D g, char c, int x, int y) {
      if (c >= NUM_CHARACTERS) return;
      for (int i = 0; i < CHAR_HEIGHT; i++) {
        for (int j = 0; j < CHAR_WIDTH; j++) {
          if (myImage.getRGB(j + c * CHAR_WIDTH, i) == 0xFF000000) {
            g.fillRect(x + j, y + i - CHAR_HEIGHT + CHAR_DESCENT, 1, 1);
          }
        }
      }
    }
  }
}
