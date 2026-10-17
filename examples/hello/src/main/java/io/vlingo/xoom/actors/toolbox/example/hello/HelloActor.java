// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.actors.toolbox.example.hello;

import io.vlingo.xoom.actors.Actor;
import io.vlingo.xoom.common.Completes;

/**
 * How the actor greets. This is all an actor needs: the behaviour, and nothing of the plumbing.
 */
public class HelloActor extends Actor implements Hello {

  @Override
  public Completes<String> hello(final String name) {
    return completes().with("Hello, " + name + "!");
  }
}
