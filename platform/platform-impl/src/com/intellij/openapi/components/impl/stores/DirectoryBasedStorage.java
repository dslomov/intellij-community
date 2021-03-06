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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.StateSplitter;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.store.ReadOnlyModificationException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import com.intellij.util.PairConsumer;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.SmartHashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;

public class DirectoryBasedStorage extends StateStorageBase<DirectoryStorageData> {
  private final File myDir;
  private volatile VirtualFile myVirtualFile;
  @SuppressWarnings("deprecation")
  private final StateSplitter mySplitter;

  private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;

  public DirectoryBasedStorage(@Nullable TrackingPathMacroSubstitutor pathMacroSubstitutor, @NotNull File dir, @SuppressWarnings("deprecation") @NotNull StateSplitter splitter) {
    myPathMacroSubstitutor = pathMacroSubstitutor;
    myDir = dir;
    mySplitter = splitter;
  }

  public void setVirtualDir(@Nullable VirtualFile dir) {
    myVirtualFile = dir;
  }

  @Override
  public void analyzeExternalChangesAndUpdateIfNeed(@NotNull Set<String> componentNames) {
    // todo reload only changed file, compute diff
    DirectoryStorageData oldData = storageDataRef.get();
    DirectoryStorageData newData = loadData();
    storageDataRef.set(newData);
    if (oldData == null) {
      componentNames.addAll(newData.getComponentNames());
    }
    else {
      componentNames.addAll(oldData.getComponentNames());
      componentNames.addAll(newData.getComponentNames());
    }
  }

  @Nullable
  @Override
  protected Element getStateAndArchive(@NotNull DirectoryStorageData storageData, Object component, @NotNull String componentName) {
    return storageData.getCompositeStateAndArchive(componentName, mySplitter);
  }

  @NotNull
  @Override
  protected DirectoryStorageData loadData() {
    DirectoryStorageData storageData = new DirectoryStorageData();
    storageData.loadFrom(getVirtualFile(), myPathMacroSubstitutor);
    return storageData;
  }

  @Nullable
  private VirtualFile getVirtualFile() {
    VirtualFile virtualFile = myVirtualFile;
    if (virtualFile == null) {
      myVirtualFile = virtualFile = LocalFileSystem.getInstance().findFileByIoFile(myDir);
    }
    return virtualFile;
  }

  @Override
  @Nullable
  public ExternalizationSession startExternalization() {
    return checkIsSavingDisabled() ? null : new MySaveSession(this, getStorageData());
  }

  private static class MySaveSession extends SaveSessionBase {
    private final DirectoryBasedStorage storage;
    private final DirectoryStorageData originalStorageData;
    private DirectoryStorageData copiedStorageData;

    private final Set<String> dirtyFileNames = new SmartHashSet<String>();
    private final Set<String> removedFileNames = new SmartHashSet<String>();

    private MySaveSession(@NotNull DirectoryBasedStorage storage, @NotNull DirectoryStorageData storageData) {
      this.storage = storage;
      originalStorageData = storageData;
    }

    @Override
    protected void setSerializedState(@NotNull Object component, @NotNull String componentName, @Nullable Element element) {
      removedFileNames.addAll(originalStorageData.getFileNames(componentName));
      if (JDOMUtil.isEmpty(element)) {
        doSetState(componentName, null, null);
      }
      else {
        for (Pair<Element, String> pair : storage.mySplitter.splitState(element)) {
          removedFileNames.remove(pair.second);
          doSetState(componentName, pair.second, pair.first);
        }

        if (!removedFileNames.isEmpty()) {
          for (String fileName : removedFileNames) {
            doSetState(componentName, fileName, null);
          }
        }
      }
    }

    private void doSetState(@NotNull String componentName, @Nullable String fileName, @Nullable Element subState) {
      if (copiedStorageData == null) {
        copiedStorageData = DirectoryStorageData.setStateAndCloneIfNeed(componentName, fileName, subState, originalStorageData);
        if (copiedStorageData != null && fileName != null) {
          dirtyFileNames.add(fileName);
        }
      }
      else if (copiedStorageData.setState(componentName, fileName, subState) != null && fileName != null) {
        dirtyFileNames.add(fileName);
      }
    }

    @Override
    @Nullable
    public SaveSession createSaveSession() {
      return storage.checkIsSavingDisabled() || copiedStorageData == null ? null : this;
    }

    @Override
    public void save() throws IOException {
      VirtualFile dir = storage.getVirtualFile();
      if (copiedStorageData.isEmpty()) {
        if (dir != null && dir.exists()) {
          StorageUtil.deleteFile(this, dir);
        }
        storage.storageDataRef.set(copiedStorageData);
        return;
      }

      if (dir == null || !dir.isValid()) {
        dir = StorageUtil.createDir(storage.myDir, this);
      }

      if (!dirtyFileNames.isEmpty()) {
        saveStates(dir);
      }
      if (dir.exists() && !removedFileNames.isEmpty()) {
        deleteFiles(dir);
      }

      storage.myVirtualFile = dir;
      storage.storageDataRef.set(copiedStorageData);
    }

    private void saveStates(@NotNull final VirtualFile dir) {
      final Element storeElement = new Element(StateMap.COMPONENT);

      for (final String componentName : copiedStorageData.getComponentNames()) {
        copiedStorageData.processComponent(componentName, new PairConsumer<String, Object>() {
          @Override
          public void consume(String fileName, Object state) {
            if (!dirtyFileNames.contains(fileName)) {
              return;
            }

            Element element = null;
            try {
              element = copiedStorageData.stateToElement(fileName, state);
              if (storage.myPathMacroSubstitutor != null) {
                storage.myPathMacroSubstitutor.collapsePaths(element);
              }

              storeElement.setAttribute(StateMap.NAME, componentName);
              storeElement.addContent(element);

              VirtualFile file = StorageUtil.getFile(fileName, dir, MySaveSession.this);
              // we don't write xml prolog due to historical reasons (and should not in any case)
              StorageUtil.writeFile(null, MySaveSession.this, file, storeElement,
                                    LineSeparator.fromString(file.exists() ? StorageUtil.loadFile(file).second : SystemProperties.getLineSeparator()), false);
            }
            catch (IOException e) {
              LOG.error(e);
            }
            finally {
              if (element != null) {
                element.detach();
              }
            }
          }
        });
      }
    }

    private void deleteFiles(@NotNull VirtualFile dir) throws IOException {
      AccessToken token = WriteAction.start();
      try {
        for (VirtualFile file : dir.getChildren()) {
          if (removedFileNames.contains(file.getName())) {
            try {
              file.delete(this);
            }
            catch (FileNotFoundException e) {
              throw new ReadOnlyModificationException(file, e, null);
            }
          }
        }
      }
      finally {
        token.finish();
      }
    }
  }
}
