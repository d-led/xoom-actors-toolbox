// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.actors.toolbox;

import java.util.ArrayList;
import java.util.List;

import io.vlingo.xoom.actors.Actor;
import io.vlingo.xoom.common.Completes;

/**
 * The ticker implementation. This is all an actor needs: the behaviour, and — only because it
 * sends messages to itself — its own reference to itself.
 */
public class TickerActor extends Actor implements Ticker {

  private final Reported<Integer> selfTicks;
  private final Ticker self = selfAs(Ticker.class);
  private final List<Object> tags = new ArrayList<>();
  private int count;

  public TickerActor(final Reported<Integer> selfTicks) {
    this.selfTicks = selfTicks;
  }

  @Override
  public void tick() {
    count++;
  }

  @Override
  public void tickTwice() {
    self.tick();
    self.tick();
    // Self-sent messages are delivered after the messages already queued, so this third message
    // observes the two ticks above.
    self.ticks().andFinallyConsume(selfTicks::report);
  }

  @Override
  public <T> void tickTagged(final T tag) {
    tags.add(tag);
  }

  @Override
  public Completes<Integer> ticks() {
    return completes().with(count);
  }

  @Override
  public Completes<List<Object>> tags() {
    return completes().with(tags);
  }
}
