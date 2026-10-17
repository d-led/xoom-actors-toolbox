// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.actors.toolbox;

import java.util.List;

import io.vlingo.xoom.common.Completes;

/**
 * The protocol of the ticker: what the ticker does, as seen from the outside. The toolbox
 * generates {@code Ticker__Proxy} from this interface at build time.
 */
@ActorProtocol
public interface Ticker {

  /** Ticks once. */
  void tick();

  /** Ticks twice, by sending itself two {@code tick()} messages. */
  void tickTwice();

  /** Ticks with a tag of any type. */
  <T> void tickTagged(T tag);

  /** Answers how many times the ticker ticked. */
  Completes<Integer> ticks();

  /** Answers the tags the ticker was ticked with. */
  Completes<List<Object>> tags();
}
