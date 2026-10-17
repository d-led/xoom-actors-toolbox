// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.actors.toolbox.example.hello;

import io.vlingo.xoom.actors.World;
import io.vlingo.xoom.actors.toolbox.Actors;

/**
 * Hello, World!: start a world, start the actor in it, greet, stop.
 */
public final class Main {

  public static void main(final String[] args) {
    final World world = Actors.world("hello");
    final Hello hello = Actors.actor(world, Hello.class, HelloActor.class, HelloActor::new);

    final String greeting = hello.hello("World").await();
    System.out.println(greeting);

    Actors.shutdown(world);
    // xoom-actors keeps the thread of its plugin scanner running after World.terminate(), so a
    // console application ends explicitly. A server application simply keeps running.
    System.exit(0);
  }

  private Main() {
  }
}
