/*
 * Copyright 2013-2017 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * @author Spencer Gibb
 *
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *       # =====================================
 *       - id: add_request_header_route
 *         uri: http://example.org
 *         filters:
 *         - AddRequestHeader=X-Request-Foo, Bar
 *
 */
public class AddRequestHeaderGatewayFilterFactory extends AbstractNameValueGatewayFilterFactory {

	@Override
	public GatewayFilter apply(NameValueConfig config) {
		return (exchange, chain) -> {

			//创建新的ServerHttpRequest
			ServerHttpRequest request = exchange.getRequest().mutate()
					.header(config.getName(), config.getValue())
					.build();

			//过滤器链接续过滤
			return chain.filter(exchange.mutate().request(request).build());
		};
    }

}
