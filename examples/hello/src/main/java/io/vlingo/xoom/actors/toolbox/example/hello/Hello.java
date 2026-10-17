// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.actors.toolbox.example.hello;

import io.vlingo.xoom.actors.toolbox.ActorProtocol;
import io.vlingo.xoom.common.Completes;

/**
 * What the actor does, as seen from the outside. The toolbox writes {@code Hello__Proxy} from this
 * interface while the project is compiled.
 */
@ActorProtocol
public interface Hello {

  /** Answers a greeting for {@code name}. */
  Completes<String> hello(String name);
}
