// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.actors.toolbox;

import java.util.concurrent.atomic.AtomicReference;

import io.vlingo.xoom.actors.testkit.AccessSafely;

/**
 * Reports a value from an actor to a test, and waits for it the way the xoom-actors testkit does:
 * {@code AccessSafely} blocks the reading side until the expected write happened.
 *
 * @param <T> the type of the reported value
 */
final class Reported<T> {

  private final AccessSafely access;
  private final AtomicReference<T> value = new AtomicReference<>();

  private Reported() {
    access = AccessSafely
        .afterCompleting(1)
        .writingWith("value", value::set)
        .readingWith("value", value::get);
  }

  static <T> Reported<T> once() {
    return new Reported<>();
  }

  void report(final T reportedValue) {
    access.writeUsing("value", reportedValue);
  }

  T value() {
    return access.readFrom("value");
  }
}
