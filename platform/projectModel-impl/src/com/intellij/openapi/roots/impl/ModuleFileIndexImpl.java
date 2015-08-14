/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ModuleFileIndexImpl extends FileIndexBase implements ModuleFileIndex {
  private final Module myModule;
  private final ContentFilter myContentFilter;

  public ModuleFileIndexImpl(Module module, DirectoryIndex directoryIndex) {
    super(directoryIndex, FileTypeRegistry.getInstance(), module.getProject());
    myModule = module;
    myContentFilter = new ContentFilter();
  }

  @Override
  public boolean iterateContent(@NotNull ContentIterator iterator) {
    final List<VirtualFile> contentRoots = ApplicationManager.getApplication().runReadAction(new Computable<List<VirtualFile>>() {
      @Override
      public List<VirtualFile> compute() {
        if (myModule.isDisposed()) return Collections.emptyList();
        
        List<VirtualFile> result = ContainerUtil.newArrayList();
        for (VirtualFile contentRoot : ModuleRootManager.getInstance(myModule).getContentRoots()) {
          VirtualFile parent = contentRoot.getParent();
          if (parent != null) {
            DirectoryInfo parentInfo = myDirectoryIndex.getInfoForFile(parent);
            if (parentInfo.isInProject() && myModule.equals(parentInfo.getModule())) continue; // inner content - skip it
          }
          result.add(contentRoot);
        }

        return result;
      }
    });
    for (VirtualFile contentRoot : contentRoots) {
      boolean finished = VfsUtilCore.iterateChildrenRecursively(contentRoot, myContentFilter, iterator);
      if (!finished) return false;
    }

    return true;
  }

  @Override
  public boolean iterateContentUnderDirectory(@NotNull VirtualFile dir, @NotNull ContentIterator iterator) {
    return VfsUtilCore.iterateChildrenRecursively(dir, myContentFilter, iterator);
  }

  @Override
  public boolean isInContent(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInProject() && myModule.equals(info.getModule());
  }

  @Override
  public boolean isInSourceContent(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInModuleSource() && myModule.equals(info.getModule());
  }

  @Override
  @NotNull
  public Iterable<OrderEntry> getOrderEntriesForFile(@NotNull VirtualFile fileOrDir) {
    return myDirectoryIndex.getOrderEntries(getInfoForFileOrDirectory(fileOrDir)).findAllOrderEntriesWithOwnerModule(myModule);
  }

  @Override
  public OrderEntry getOrderEntryForFile(@NotNull VirtualFile fileOrDir) {
    return myDirectoryIndex.getOrderEntries(getInfoForFileOrDirectory(fileOrDir)).findOrderEntryWithOwnerModule(myModule);
  }

  @Override
  public boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInModuleSource() && myModule.equals(info.getModule())
           && JavaModuleSourceRootTypes.isTestSourceOrResource(myDirectoryIndex.getSourceRootType(info));
  }

  @Override
  public boolean isUnderSourceRootOfType(@NotNull VirtualFile fileOrDir, @NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    DirectoryInfo info = getInfoForFileOrDirectory(fileOrDir);
    return info.isInModuleSource() && myModule.equals(info.getModule()) && rootTypes.contains(myDirectoryIndex.getSourceRootType(info));
  }

  private class ContentFilter implements VirtualFileFilter {
    @Override
    public boolean accept(@NotNull final VirtualFile file) {
      return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          if (myModule.isDisposed()) return false;
          if (file.isDirectory()) {
            DirectoryInfo info = myDirectoryIndex.getInfoForFile(file);
            return info.isInProject() && myModule.equals(info.getModule());
          }
          else {
            return !myFileTypeRegistry.isFileIgnored(file);
          }
        }
      });
    }
  }
}
