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

package org.springframework.cloud.gateway.discovery;

import java.net.URI;
import java.util.Map;
import java.util.function.Predicate;

import reactor.core.publisher.Flux;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.style.ToStringCreator;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.StringUtils;

/**
 * TODO: change to RouteLocator? use java dsl
 * 注册中心
 * @author Spencer Gibb
 */

/**
 * 通过调用 org.springframework.cloud.client.discovery.DiscoveryClient
 * 获取注册在注册中心的服务列表，生成对应的 RouteDefinition 数组。
 */
public class DiscoveryClientRouteDefinitionLocator implements RouteDefinitionLocator {

	//注册发现客户端,用于像注册中心发起请求
	private final DiscoveryClient discoveryClient;

	//加载配置文件属性
	private final DiscoveryLocatorProperties properties;

	//属性，路由配置编号前缀，以 DiscoveryClient 类名 + _
	private final String routeIdPrefix;

	//解析el表达式
	private final SimpleEvaluationContext evalCtxt;

	public DiscoveryClientRouteDefinitionLocator(DiscoveryClient discoveryClient, DiscoveryLocatorProperties properties) {
		this.discoveryClient = discoveryClient;
		this.properties = properties;
		if (StringUtils.hasText(properties.getRouteIdPrefix())) {
			this.routeIdPrefix = properties.getRouteIdPrefix();
		} else {
			this.routeIdPrefix = this.discoveryClient.getClass().getSimpleName() + "_";
		}
		evalCtxt = SimpleEvaluationContext
				.forReadOnlyDataBinding()
				.withInstanceMethods()
				.build();
	}

	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {

		SpelExpressionParser parser = new SpelExpressionParser();
		Expression includeExpr = parser.parseExpression(properties.getIncludeExpression());
		Expression urlExpr = parser.parseExpression(properties.getUrlExpression());

		Predicate<ServiceInstance> includePredicate;
		if (properties.getIncludeExpression() == null || "true".equalsIgnoreCase(properties.getIncludeExpression())) {
			includePredicate = instance -> true;
		} else {
			includePredicate = instance -> {
				Boolean include = includeExpr.getValue(evalCtxt, instance, Boolean.class);
				if (include == null) {
					return false;
				}
				return include;
			};
		}

		return Flux.fromIterable(discoveryClient.getServices())  //调用 discoveryClient 获取注册在注册中心的服务列表。
				.map(discoveryClient::getInstances)
  				.filter(instances -> !instances.isEmpty())    //过滤出服务部位null
				.map(instances -> instances.get(0))           //多实例的话去第一个
				.filter(includePredicate)                     //根据谓语匹配
				.map(instance -> {                    //遍历服务列表，生成对应的 RouteDefinition 数组。
					String serviceId = instance.getServiceId();

                    RouteDefinition routeDefinition = new RouteDefinition();

                    // 设置 ID
                    routeDefinition.setId(this.routeIdPrefix + serviceId);

                    //设置uri
					/**
					 * 格式为 lb://${serviceId} 。
					 * 在 LoadBalancerClientFilter 会根据 lb:// 前缀过滤处理，负载均衡，选择最终调用的服务地址
					 */
					String uri = urlExpr.getValue(evalCtxt, instance, String.class);
					routeDefinition.setUri(URI.create(uri));


					final ServiceInstance instanceForEval = new DelegatingServiceInstance(instance, properties);

					/**
					 * 添加path 匹配断言
					 *
					 * 例如服务的 serviceId = spring.application.name = juejin-sample ，
					 * 通过网关 http://${gateway}/${serviceId}/some_api 访问服务 http://some_api 。
					 */
					for (PredicateDefinition original : this.properties.getPredicates()) {
						PredicateDefinition predicate = new PredicateDefinition();
						predicate.setName(original.getName());
						for (Map.Entry<String, String> entry : original.getArgs().entrySet()) {
							String value = getValueFromExpr(evalCtxt, parser, instanceForEval, entry);
							predicate.addArg(entry.getKey(), value);
						}
						routeDefinition.getPredicates().add(predicate);
					}

					/**
					 * 添加path 重写过滤器
					 * 使用 RewritePathGatewayFilterFactory 创建重写网关过滤器，
					 * 用于移除请求路径里的 /${serviceId} 。如果不移除，最终请求不到服务
					 */
                    for (FilterDefinition original : this.properties.getFilters()) {
                    	FilterDefinition filter = new FilterDefinition();
                    	filter.setName(original.getName());
						for (Map.Entry<String, String> entry : original.getArgs().entrySet()) {
							String value = getValueFromExpr(evalCtxt, parser, instanceForEval, entry);
							filter.addArg(entry.getKey(), value);
						}
						routeDefinition.getFilters().add(filter);
					}

                    return routeDefinition;
				});
	}

	String getValueFromExpr(SimpleEvaluationContext evalCtxt, SpelExpressionParser parser, ServiceInstance instance, Map.Entry<String, String> entry) {
		Expression valueExpr = parser.parseExpression(entry.getValue());
		return valueExpr.getValue(evalCtxt, instance, String.class);
	}

	private static class DelegatingServiceInstance implements ServiceInstance {

		final ServiceInstance delegate;
		private final DiscoveryLocatorProperties properties;

		private DelegatingServiceInstance(ServiceInstance delegate, DiscoveryLocatorProperties properties) {
			this.delegate = delegate;
			this.properties = properties;
		}

		@Override
		public String getServiceId() {
			if (properties.isLowerCaseServiceId()) {
				return delegate.getServiceId().toLowerCase();
			}
			return delegate.getServiceId();
		}

		@Override
		public String getHost() {
			return delegate.getHost();
		}

		@Override
		public int getPort() {
			return delegate.getPort();
		}

		@Override
		public boolean isSecure() {
			return delegate.isSecure();
		}

		@Override
		public URI getUri() {
			return delegate.getUri();
		}

		@Override
		public Map<String, String> getMetadata() {
			return delegate.getMetadata();
		}

		@Override
		public String getScheme() {
			return delegate.getScheme();
		}

		@Override
		public String toString() {
			return new ToStringCreator(this)
					.append("delegate", delegate)
					.append("properties", properties)
					.toString();
		}
	}
}
