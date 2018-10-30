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

package org.springframework.cloud.gateway.handler;

import java.util.function.Function;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.handler.AbstractHandlerMapping;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_HANDLER_MAPPER_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 *
 *   路由匹配
 *
 *   一个请求是怎么被Spring-Cloud_Gateway 处理的?
 *
 *   1: gateWayClients  ---> DispatcherHandler ---匹配HandlerMapping--->HandlerMapping ----匹配Route----> WebHandler ---FilterChain-->Service
 *
 *   2: org.springframework.web.reactive.DispatcherHandler 接收到请求，匹配 HandlerMapping ，此处会匹配到 RoutePredicateHandlerMapping
 *
 *   3: org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping ：接收到请求，匹配 Route 。
 *
 *   4: org.springframework.cloud.gateway.handler.FilteringWebHandler ：获得 Route 的 GatewayFilter 数组，创建 GatewayFilterChain 处理请求。
 *
 *
 *   @author Spencer Gibb
 */
public class RoutePredicateHandlerMapping extends AbstractHandlerMapping {

	private final FilteringWebHandler webHandler;
	private final RouteLocator routeLocator;
	private final Integer managmentPort;

	public RoutePredicateHandlerMapping(FilteringWebHandler webHandler, RouteLocator routeLocator, GlobalCorsProperties globalCorsProperties, Environment environment) {
		this.webHandler = webHandler;
		this.routeLocator = routeLocator;

		if (environment.containsProperty("management.server.port")) {
			managmentPort = new Integer(environment.getProperty("management.server.port"));
		} else {
			managmentPort = null;
		}
		/**
		 *  为什么设置setOrder
		 *
		 *  Spring Cloud Gateway 的 GatewayWebfluxEndpoint 提供 HTTP API ，不需要经过网关，
		 *
		 *  它通过 RequestMappingHandlerMapping 进行请求匹配处理。RequestMappingHandlerMapping 的 order = 0 ，
		 *
		 *  需要排在 RoutePredicateHandlerMapping 前面。所有，RoutePredicateHandlerMapping 设置 order = 1 。
		 *
		 */
		setOrder(1);
		setCorsConfigurations(globalCorsProperties.getCorsConfigurations());
	}

	/**
	 *
	 *  匹配 Route ，并返回处理 Route 的 FilteringWebHandler
	 *
	 *  @param exchange
	 *
	 *  @return
	 */
	@Override
	protected Mono<?> getHandlerInternal(ServerWebExchange exchange) {
		// don't handle requests on the management port if set
		if (managmentPort != null && exchange.getRequest().getURI().getPort() == managmentPort.intValue()) {
			return Mono.empty();
		}
		exchange.getAttributes().put(GATEWAY_HANDLER_MAPPER_ATTR, getSimpleName());   //设置 GATEWAY_HANDLER_MAPPER_ATTR 为 RoutePredicateHandlerMapping 。

		/**
		 *  匹配 Route 。
		 */
		return lookupRoute(exchange)
				// .log("route-predicate-handler-mapping", Level.FINER) //name this
				.flatMap((Function<Route, Mono<?>>) r -> {                                        //返回 Route 的处理器 FilteringWebHandler 。
					exchange.getAttributes().remove(GATEWAY_PREDICATE_ROUTE_ATTR);                // 移除 GATEWAY_PREDICATE_ROUTE_ATTR ？？？？不理解
					if (logger.isDebugEnabled()) {
						logger.debug("Mapping [" + getExchangeDesc(exchange) + "] to " + r);
					}
                    //r 设置GATEWAY_ROUTE_ATTR route
					exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, r);
					return Mono.just(webHandler);             //返回webHandler
				}).switchIfEmpty(Mono.empty().then(Mono.fromRunnable(() -> {   //这里代表匹配不了Route
					exchange.getAttributes().remove(GATEWAY_PREDICATE_ROUTE_ATTR);
					if (logger.isTraceEnabled()) {
						logger.trace("No RouteDefinition found for [" + getExchangeDesc(exchange) + "]");
					}
				})));
	}

	@Override
	protected CorsConfiguration getCorsConfiguration(Object handler, ServerWebExchange exchange) {
		// TODO: support cors configuration via properties on a route see gh-229
		// see RequestMappingHandlerMapping.initCorsConfiguration()
		// also see https://github.com/spring-projects/spring-framework/blob/master/spring-web/src/test/java/org/springframework/web/cors/reactive/CorsWebFilterTests.java	        
	    
		return super.getCorsConfiguration(handler, exchange);
	}

	//TODO: get desc from factory?
	private String getExchangeDesc(ServerWebExchange exchange) {
		StringBuilder out = new StringBuilder();
		out.append("Exchange: ");
		out.append(exchange.getRequest().getMethod());
		out.append(" ");
		out.append(exchange.getRequest().getURI());
		return out.toString();
	}


	/**
	 * 顺序匹配Route
	 * @param exchange
	 * @return
	 */
	protected Mono<Route> lookupRoute(ServerWebExchange exchange) {
		return this.routeLocator
				.getRoutes()                           //获取全部的Routes
				//individually filter routes so that filterWhen error delaying is not a problem
				.concatMap(route -> Mono                       //和FlatMap类似
						.just(route)
						.filterWhen(r -> {
							// add the current route we are testing
							exchange.getAttributes().put(GATEWAY_PREDICATE_ROUTE_ATTR, r.getId());            //异步的进行判断
							return r.getPredicate().apply(exchange);
						})
						//instead of immediately stopping main flux due to error, log and swallow it
						.doOnError(e -> logger.error("Error applying predicate for route: "+route.getId(), e))
						.onErrorResume(e -> Mono.empty())
				)
				// .defaultIfEmpty() put a static Route not found
				// or .switchIfEmpty()
				// .switchIfEmpty(Mono.<Route>empty().log("noroute"))
				.next()
				//TODO: error handling
				.map(route -> {
					if (logger.isDebugEnabled()) {
						logger.debug("Route matched: " + route.getId());
					}
					validateRoute(route, exchange);    //这是个空方法 检验Route的有效性
					return route;
				});

		/* TODO: trace logging
			if (logger.isTraceEnabled()) {
				logger.trace("RouteDefinition did not match: " + routeDefinition.getId());
			}*/
	}

	/**
	 * Validate the given handler against the current request.
	 * <p>The default implementation is empty. Can be overridden in subclasses,
	 * for example to enforce specific preconditions expressed in URL mappings.
	 * @param route the Route object to validate
	 * @param exchange current exchange
	 * @throws Exception if validation failed
	 */
	@SuppressWarnings("UnusedParameters")
	protected void validateRoute(Route route, ServerWebExchange exchange) {
	}

	protected String getSimpleName() {
		return "RoutePredicateHandlerMapping";
	}
}
