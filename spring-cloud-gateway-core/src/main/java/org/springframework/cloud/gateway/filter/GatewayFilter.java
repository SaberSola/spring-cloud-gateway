package org.springframework.cloud.gateway.filter;

/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.springframework.cloud.gateway.support.ShortcutConfigurable;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Contract for interception-style, chained processing of Web requests that may
 * be used to implement cross-cutting, application-agnostic requirements such
 * as security, timeouts, and others. Specific to a Gateway
 *
 * Copied from WebFilter
 *
 *  网关过滤器接口
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public interface GatewayFilter extends ShortcutConfigurable {

	String NAME_KEY = "name";
	String VALUE_KEY = "value";

	/**
	 * Process the Web request and (optionally) delegate to the next
	 * {@code WebFilter} through the given {@link GatewayFilterChain}.
	 * @param exchange the current server exchange
	 * @param chain provides a way to delegate to the next filter
	 * @return {@code Mono<Void>} to indicate when request processing is complete
	 */
	/**
	 * Filter 最终是通过 filter chain 来形成链式调用的，
	 * 每个 filter 处理完 pre filter 逻辑后委派给 filter chain，filter chain
	 * 再委派给下一下 filter
	 */
	Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain);

}

