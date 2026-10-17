// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.actors.toolbox;

import io.vlingo.xoom.actors.Actor;
import io.vlingo.xoom.actors.ActorInstantiator;
import io.vlingo.xoom.actors.World;

/**
 * The three calls that start, use and stop an actor system. Nothing here hides anything: every
 * method is a one-line delegation to the xoom-actors API it stands for.
 */
public final class Actors {

  private Actors() {
  }

  /**
   * Starts a {@code World} named {@code name}, configured with the sensible defaults of
   * xoom-actors. No {@code xoom-actors.properties} file, and no plugin configuration, is needed.
   *
   * @param name the {@code String} name of the {@code World}
   * @return World
   */
  public static World world(final String name) {
    return World.startWithDefaults(name);
  }

  /**
   * Starts the {@code actor} that implements the {@code protocol} and answers the {@code protocol} of it.
   *
   * @param world the {@code World} in which the actor starts
   * @param protocol the {@code Class<P>} protocol the actor implements
   * @param actor the {@code Class<A>} of the actor to start
   * @param instantiator the {@code ActorInstantiator<A>} that creates the actor, for example {@code TickerActor::new}
   * @param <P> the protocol type
   * @param <A> the actor type
   * @return P
   */
  public static <P, A extends Actor> P actor(
          final World world,
          final Class<P> protocol,
          final Class<A> actor,
          final ActorInstantiator<A> instantiator) {
    return world.actorFor(protocol, actor, instantiator);
  }

  /**
   * Stops the {@code world} and all of its actors.
   *
   * @param world the {@code World} to stop
   */
  public static void shutdown(final World world) {
    world.terminate();
  }
}
