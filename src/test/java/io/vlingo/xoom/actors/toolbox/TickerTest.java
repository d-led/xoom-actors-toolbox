// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.actors.toolbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.net.URLClassLoader;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vlingo.xoom.actors.ActorProxyBase;
import io.vlingo.xoom.actors.Proxy;
import io.vlingo.xoom.actors.World;
import io.vlingo.xoom.common.Completes;

/**
 * Proves that the proxy the xoom-actors runtime uses is the one generated at build time, and that
 * messages reach the actor through it.
 */
public class TickerTest {

  private static final String GeneratedProxy = "io.vlingo.xoom.actors.toolbox.Ticker__Proxy";

  private World world;
  private Reported<Integer> selfTicks;
  private Ticker ticker;

  @Before
  public void setUp() {
    world = Actors.world("toolbox-tests");
    selfTicks = Reported.once();
    ticker = Actors.actor(world, Ticker.class, TickerActor.class, () -> new TickerActor(selfTicks));
  }

  @After
  public void tearDown() {
    Actors.shutdown(world);
  }

  @Test
  public void theRuntimeUsesTheBuildTimeGeneratedProxy() throws Exception {
    final Class<?> generatedProxy = Class.forName(GeneratedProxy);

    assertSame("the proxy of the ticker is the generated proxy", generatedProxy, ticker.getClass());
    assertFalse("no proxy was generated and compiled at runtime",
        ticker.getClass().getClassLoader() instanceof URLClassLoader);
  }

  @Test
  public void theProxyCanStandInForTheActor() {
    assertTrue("the proxy carries protocol, definition and address of the actor so that it can be shared",
        ticker instanceof ActorProxyBase);
    assertNotNull("the proxy answers the address of the actor", Proxy.from(ticker).address());
  }

  @Test
  public void voidMessagesReachTheActorInOrder() {
    ticker.tick();
    ticker.tick();
    ticker.tick();

    assertEquals("three ticks reached the actor", 3, integerFrom(ticker.ticks()));
  }

  @Test(timeout = 5000)
  public void anActorSendsToItselfThroughItsGeneratedProxy() {
    ticker.tickTwice();

    assertEquals("the ticker ticked itself twice", 2, selfTicks.value().intValue());
  }

  @Test
  public void genericMessagesReachTheActor() {
    ticker.tickTagged("hello");
    ticker.tickTagged(Integer.valueOf(42));

    assertEquals("both tags reached the actor in order", List.of("hello", 42), listFrom(ticker.tags()));
  }

  private int integerFrom(final Completes<Integer> completes) {
    final Reported<Integer> reported = Reported.once();
    completes.andFinallyConsume(reported::report);
    return reported.value();
  }

  private List<Object> listFrom(final Completes<List<Object>> completes) {
    final Reported<List<Object>> reported = Reported.once();
    completes.andFinallyConsume(reported::report);
    return reported.value();
  }
}
