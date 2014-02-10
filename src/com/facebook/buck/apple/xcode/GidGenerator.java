/*
 * Copyright 2013-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.apple.xcode;

import com.google.common.collect.Sets;

import java.util.Random;
import java.util.Set;

/**
 * Generator for Global ID (GID) which are present on every xcode project object.
 *
 * The GID is a 96 bit identifier that's unique on a per-project basis.
 */
public class GidGenerator {
  private final Random random;
  private final Set<String> generatedIds;

  public GidGenerator(long seed) {
    random = new Random(seed);
    generatedIds = Sets.newHashSet();
  }

  /**
   * Generate a stable GID based on the class name and hash of some object info.
   *
   * GIDs generated this way will be in the form of
   *  {@code <class-name-hash-32> <obj-hash-32> <counter-32>}
   */
  public String generateStableGid(String pbxClassName, int hash) {
    int counter = 0;
    String gid;
    do {
      gid = String.format("%08X%08X%08X", pbxClassName.hashCode(), hash, counter++);
    } while (generatedIds.contains(gid));
    generatedIds.add(gid);
    return gid;
  }

  public String genGid() {
    String gid;

    do {
      StringBuilder builder = new StringBuilder();

      for (int i = 0; i < 3; i++) {
        builder.append(String.format("%08X", random.nextLong() & 0xFFFFFFFFL));
      }

      gid = builder.toString();
    } while (generatedIds.contains(gid));

    generatedIds.add(gid);
    return gid;
  }
}
