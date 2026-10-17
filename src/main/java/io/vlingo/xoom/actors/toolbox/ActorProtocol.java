// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.actors.toolbox;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tags an interface as the protocol of an {@code Actor}. The toolbox generates the
 * {@code <Protocol>__Proxy} class at build time, so the xoom-actors runtime never has to
 * generate and compile one while your application is starting up.
 *
 * <p>Annotated interfaces must follow the protocol rules of xoom-actors: methods are either
 * {@code void}, {@code Completes<T>}, {@code Future<T>} or {@code CompletableFuture<T>}, or the
 * protocol (or one of its super interfaces) is annotated with
 * {@link io.vlingo.xoom.actors.SafeProxyGenerable}.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ActorProtocol {
}
