/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

package com.techeazyhq.cm.audkit;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

/**
 * The actor of the request being handled, carried in Reactor's context (ADR-040): a web filter puts it there from the
 * caller's token, so application code records actions without passing the actor through every command. Outside a
 * request (a scheduled job, an event consumer) there is none, and the actor is {@link Actor#SYSTEM}.
 */
public final class Actors {

    static final Object KEY = Actor.class;

    private Actors() {
    }

    /** The actor of the current request, or {@link Actor#SYSTEM}. */
    public static Mono<Actor> current() {
        return Mono.deferContextual(ctx -> Mono.just(of(ctx)));
    }

    public static Actor of(ContextView ctx) {
        return ctx.<Actor>getOrEmpty(KEY).orElse(Actor.SYSTEM);
    }

    /** For {@code contextWrite}: everything downstream acts as {@code actor}. */
    public static Context with(Context ctx, Actor actor) {
        return ctx.put(KEY, actor);
    }
}
