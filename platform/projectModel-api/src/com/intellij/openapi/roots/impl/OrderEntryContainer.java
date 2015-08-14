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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by dslomov on 8/14/15.
 */
public class OrderEntryContainer implements Iterable<OrderEntry> {
  public static final OrderEntryContainer EMPTY = new OrderEntryContainer(OrderEntry.EMPTY_ARRAY);
  public static final Comparator<OrderEntry> BY_OWNER_MODULE = new Comparator<OrderEntry>() {
    @Override
    public int compare(OrderEntry o1, OrderEntry o2) {
      String name1 = o1.getOwnerModule().getName();
      String name2 = o2.getOwnerModule().getName();
      return name1.compareTo(name2);
    }
  };
  private final OrderEntry[] myOrderEntries;

  public OrderEntryContainer(OrderEntry[] orderEntries) {
    myOrderEntries = orderEntries;
  }

  @Nullable
  OrderEntry findOrderEntryWithOwnerModule(@NotNull Module ownerModule) {
    if (myOrderEntries.length < 10) {
      for (OrderEntry entry : myOrderEntries) {
        if (entry.getOwnerModule() == ownerModule) return entry;
      }
      return null;
    }
    int index = Arrays.binarySearch(myOrderEntries, new FakeOrderEntry(ownerModule), BY_OWNER_MODULE);
    return index < 0 ? null : myOrderEntries[index];
  }

  @NotNull
  Iterable<OrderEntry> findAllOrderEntriesWithOwnerModule(@NotNull Module ownerModule) {
    if (isEmpty()) return Collections.emptyList();

    if (myOrderEntries.length == 1) {
      OrderEntry entry = myOrderEntries[0];
      return entry.getOwnerModule() == ownerModule ? Arrays.asList(myOrderEntries) : Collections.<OrderEntry>emptyList();
    }
    int index = Arrays.binarySearch(myOrderEntries, new FakeOrderEntry(ownerModule), BY_OWNER_MODULE);
    if (index < 0) {
      return Collections.emptyList();
    }
    int firstIndex = index;
    while (firstIndex - 1 >= 0 && myOrderEntries[firstIndex - 1].getOwnerModule() == ownerModule) {
      firstIndex--;
    }
    int lastIndex = index + 1;
    while (lastIndex < myOrderEntries.length && myOrderEntries[lastIndex].getOwnerModule() == ownerModule) {
      lastIndex++;
    }

    OrderEntry[] subArray = new OrderEntry[lastIndex - firstIndex];
    System.arraycopy(myOrderEntries, firstIndex, subArray, 0, lastIndex - firstIndex);

    return Arrays.asList(subArray);
  }

  void assertConsistency() {
    for (int i = 1; i < myOrderEntries.length; i++) {
      assert BY_OWNER_MODULE.compare(myOrderEntries[i - 1], myOrderEntries[i]) <= 0;
    }
  }

  @NotNull
  @Override
  public Iterator<OrderEntry> iterator() {
    return Arrays.asList(myOrderEntries).iterator();
  }

  public boolean isEmpty() {
    return myOrderEntries.length == 0;
  }

  @Override
  public String toString() {
    return Arrays.toString(myOrderEntries);
  }

  private static class FakeOrderEntry implements OrderEntry {
    private final Module myOwnerModule;

    public FakeOrderEntry(Module ownerModule) {
      myOwnerModule = ownerModule;
    }

    @NotNull
    @Override
    public VirtualFile[] getFiles(OrderRootType type) {
      throw new IncorrectOperationException();
    }

    @NotNull
    @Override
    public String[] getUrls(OrderRootType rootType) {
      throw new IncorrectOperationException();
    }

    @NotNull
    @Override
    public String getPresentableName() {
      throw new IncorrectOperationException();
    }

    @Override
    public boolean isValid() {
      throw new IncorrectOperationException();
    }

    @NotNull
    @Override
    public Module getOwnerModule() {
      return myOwnerModule;
    }

    @Override
    public <R> R accept(RootPolicy<R> policy, @Nullable R initialValue) {
      throw new IncorrectOperationException();
    }

    @Override
    public int compareTo(@NotNull OrderEntry o) {
      throw new IncorrectOperationException();
    }

    @Override
    public boolean isSynthetic() {
      throw new IncorrectOperationException();
    }
  }
}
