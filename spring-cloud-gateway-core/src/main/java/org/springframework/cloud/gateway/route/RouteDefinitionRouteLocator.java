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

package org.springframework.cloud.gateway.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.event.FilterArgsEvent;
import org.springframework.cloud.gateway.event.PredicateArgsEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.support.ConfigurationUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.validation.Validator;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;

/**
 * 这里才是定义外部Route
 *
 * {@link RouteLocator} that loads routes from a {@link RouteDefinitionLocator}
 * @author Spencer Gibb
 *
 * RouteDefinitionRouteLocator 从 RouteDefinitionLocator 获取 RouteDefinition ，转换成 Route
 *
 */
public class RouteDefinitionRouteLocator implements RouteLocator, BeanFactoryAware, ApplicationEventPublisherAware {
	protected final Log logger = LogFactory.getLog(getClass());

	private final RouteDefinitionLocator routeDefinitionLocator;    //RouteDefinitionLocator 对象

	private final Map<String, RoutePredicateFactory> predicates = new LinkedHashMap<>();  //根据key找到对应的工厂

	private final Map<String, GatewayFilterFactory> gatewayFilterFactories = new HashMap<>(); //同样是根据key找到对应的Filter的工厂

	/**
	 * GatewayProperties 已经提供了RouteDefinitionLocator 为什么还要依赖RouteDefinitionLocator ？
	 *
	 * result：这里并不会直接使用GatewayProperties的RouteDefinitionLocator
	 * 仅仅是用到他提供的default filters
	 * 最终传入的 RouteDefinitionLocator 实现上是 CompositeRouteDefinitionLocator 的实例
	 * 它组合了 GatewayProperties 中所定义的 routes。
	 */
	private final GatewayProperties gatewayProperties;  //配置属性


	private final SpelExpressionParser parser = new SpelExpressionParser();

	private BeanFactory beanFactory;

	private ApplicationEventPublisher publisher;

	public RouteDefinitionRouteLocator(RouteDefinitionLocator routeDefinitionLocator,
									   List<RoutePredicateFactory> predicates,
									   List<GatewayFilterFactory> gatewayFilterFactories,
									   GatewayProperties gatewayProperties) {

		/**
		 * 设置 RouteDefinitionLocator
		 *
		 * 提供 RouteDefinition 的 RouteDefinitionLocator
		 */
		this.routeDefinitionLocator = routeDefinitionLocator;


		/**
		 * 初始化 RoutePredicateFactory
		 *
		 * RoutePredicateFactory Bean 对象的映射
		 *
		 * key 为 {@link RoutePredicateFactory#name()} 。
		 *
		 * RouteDefinition.predicates 转换成 Route.predicates 。
		 */
		initFactories(predicates);

		/**
		 *  初始化 FilterFactories
		 *
		 *  RoutePredicateFactory Bean 对象映射
		 *
		 *  key 为 {@link GatewayFilterFactory#name()}
		 *
		 *  将 RouteDefinition.filters 转换成 Route.filters
		 */
		gatewayFilterFactories.forEach(factory -> this.gatewayFilterFactories.put(factory.name(), factory));

		/**
		 *  设置 GatewayProperties
		 *
		 *  gatewayProperties 属性，使用 GatewayProperties.defaultFilters 默认过滤器定义数组，
		 *
		 *  添加到每个 Route 。下文会看到相关代码的实现。
		 */
		this.gatewayProperties = gatewayProperties;
	}

	@Autowired
	private Validator validator;

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	private void initFactories(List<RoutePredicateFactory> predicates) {
		predicates.forEach(factory -> {
			String key = factory.name();
			if (this.predicates.containsKey(key)) {
				this.logger.warn("A RoutePredicateFactory named "+ key
						+ " already exists, class: " + this.predicates.get(key)
						+ ". It will be overwritten.");
			}
			this.predicates.put(key, factory);
			if (logger.isInfoEnabled()) {
				logger.info("Loaded RoutePredicateFactory [" + key + "]");
			}
		});
	}

	/**
	 *  todo 重点核心方法
	 *
	 *  获取route数组
	 *
	 *  @return
	 */
	@Override
	public Flux<Route> getRoutes() {
		return this.routeDefinitionLocator.getRouteDefinitions()
				.map(this::convertToRoute) //调用convertToRoute方法将RouteDefinition转化为Route
				//TODO: error handling
				//打印每个数组
				.map(route -> {
					if (logger.isDebugEnabled()) {
						logger.debug("RouteDefinition matched: " + route.getId());
					}
					return route;
				});


		/* TODO: trace logging
			if (logger.isTraceEnabled()) {
				logger.trace("RouteDefinition did not match: " + routeDefinition.getId());
			}*/
	}

	/**
	 *  将routeDefinition 转换成Route
	 *
	 *  @param routeDefinition
	 *
	 *  @return
	 */
	private Route convertToRoute(RouteDefinition routeDefinition) {

		/**
		 *  将 PredicateDefinition 转换成 AsyncPredicate。
		 *
		 *  将routeDefinition.predicates 数组合并成一个AsyncPredicate
		 *
		 *  这样 RoutePredicateHandlerMapping 为请求匹配 Route ，只要调用一次 Predicate#test(ServerWebExchange) 方法即可。
		 */
		AsyncPredicate<ServerWebExchange> predicate = combinePredicates(routeDefinition);

		/**
		 *   从routeDefinition 获取到Filters
		 *
		 */
		List<GatewayFilter> gatewayFilters = getFilters(routeDefinition);

		return Route.async(routeDefinition)
				.asyncPredicate(predicate)
				.replaceFilters(gatewayFilters)
				.build();  //根据前方的谓语和filter 产生Route对象
	}

	@SuppressWarnings("unchecked")
	private List<GatewayFilter> loadGatewayFilters(String id, List<FilterDefinition> filterDefinitions) {
		List<GatewayFilter> filters = filterDefinitions.stream()
				.map(definition -> {   //将filterDefinitions 转为 gatewayFilter
					GatewayFilterFactory factory = this.gatewayFilterFactories.get(definition.getName());   //根据名称获取对应的GatewayFilterFactory
					if (factory == null) {
                        throw new IllegalArgumentException("Unable to find GatewayFilterFactory with name " + definition.getName());
					}
					//获取相关参数  //key-value 形式的filter参数
					Map<String, String> args = definition.getArgs();
					if (logger.isDebugEnabled()) {
						logger.debug("RouteDefinition " + id + " applying filter " + args + " to " + definition.getName());
					}

					//把filter 参数解析为 Map形式
                    Map<String, Object> properties = factory.shortcutType().normalize(args, factory, this.parser, this.beanFactory);

                    Object configuration = factory.newConfig();

                    ConfigurationUtils.bind(configuration, properties,
                            factory.shortcutFieldPrefix(), definition.getName(), validator);

					/**
					 * 生成对应的GatewayFilter
					 */
					GatewayFilter gatewayFilter = factory.apply(configuration);

					//又发布了什么锤子事件？？ 待研究
                    if (this.publisher != null) {
                        this.publisher.publishEvent(new FilterArgsEvent(this, id, properties));
                    }
                    return gatewayFilter;
				})
				.collect(Collectors.toList());

		ArrayList<GatewayFilter> ordered = new ArrayList<>(filters.size());
		for (int i = 0; i < filters.size(); i++) {
			GatewayFilter gatewayFilter = filters.get(i);
			if (gatewayFilter instanceof Ordered) {
				ordered.add(gatewayFilter);
			}
			else {
				ordered.add(new OrderedGatewayFilter(gatewayFilter, i + 1));
			}
		}

		return ordered;
	}

	/**
	 * 将routeDefinition 转化为对应的Filter
	 * @param routeDefinition
	 * @return
	 */

	private List<GatewayFilter> getFilters(RouteDefinition routeDefinition) {
		List<GatewayFilter> filters = new ArrayList<>();

		//TODO: support option to apply defaults after route specific filters?
		/**
		 *  添加默认的管理器
		 */
		if (!this.gatewayProperties.getDefaultFilters().isEmpty()) {
			filters.addAll(loadGatewayFilters("defaultFilters",
					this.gatewayProperties.getDefaultFilters()));
		}
		/**
		 * 添加自己配置的过滤器
		 */
		if (!routeDefinition.getFilters().isEmpty()) {
			/**
			 * loadGatewayFilters() 解析routeDefinition中的Filters
			 */
			filters.addAll(loadGatewayFilters(routeDefinition.getId(), routeDefinition.getFilters()));
		}
		/**
		 * 对Filter进行排序
		 *
		 */
		AnnotationAwareOrderComparator.sort(filters);
		return filters;
	}

	private AsyncPredicate<ServerWebExchange> combinePredicates(RouteDefinition routeDefinition) {

		/**
		 *  获取routeDefinition.predicates 的数组
		 */
		List<PredicateDefinition> predicates = routeDefinition.getPredicates();

		/**
		 *  调用lookup方法将predicates列表中的第一个转换为AsyncPredicate
		 *
		 *  为什么分为2部分?(可能第一步想先生成一个AsyncPredicate,其余的拼接上.对响应式编程了解的不够深入)
		 *
		 *  第一部分找到 java.util.function.Predicate,第二部分通过 Predicate#and(Predicate) 方法不断拼接。
		 */
		AsyncPredicate<ServerWebExchange> predicate = lookup(routeDefinition, predicates.get(0));

		//循环抵调用将列表中每一个 PredicateDefinition拼接到predicate中
		for (PredicateDefinition andPredicate : predicates.subList(1, predicates.size())) {
			AsyncPredicate<ServerWebExchange> found = lookup(routeDefinition, andPredicate);

			predicate = predicate.and(found);  //and方法将所有的predicate 组合成一个predicate.
		}

		return predicate;
	}
	/**
	 * spring:
      	 cloud:
	       gateway:
	           routes:
	            - id: after_route
	              uri: http://example.org
	              predicates:
	            - After=2017-01-20T17:42:47.789-07:00[America/Denver] # ①
	 */

	/**
	 * 具体的转换逻辑
	 * @param route
	 * @param predicate
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private AsyncPredicate<ServerWebExchange> lookup(RouteDefinition route, PredicateDefinition predicate) {

        //从predicates map中获取到对应名称的RoutePredicateFactory
  		RoutePredicateFactory<Object> factory = this.predicates.get(predicate.getName());
		if (factory == null) {
            throw new IllegalArgumentException("Unable to find RoutePredicateFactory with name " + predicate.getName());
		}

		// 获取 PredicateDefinition 中的 Map 类型参数，key 是固定字符串_genkey_ + 数字拼接而成。
		Map<String, String> args = predicate.getArgs();
		if (logger.isDebugEnabled()) {
			logger.debug("RouteDefinition " + route.getId() + " applying "
					+ args + " to " + predicate.getName());
		}
		/**
		 * 对参数进行转换
		 * key为 config 类（工厂类中通过范型指定）的属性名称
		 */
        Map<String, Object> properties = factory.shortcutType().normalize(args, factory, this.parser, this.beanFactory);

		/**
		 * 创建一个config对象
		 */
		Object config = factory.newConfig();

		//将参数绑定到config上
        ConfigurationUtils.bind(config, properties,
                factory.shortcutFieldPrefix(), predicate.getName(), validator);

		/**
		 * 这里发布了个什么事件？？？？
		 */
		if (this.publisher != null) {
            this.publisher.publishEvent(new PredicateArgsEvent(this, route.getId(), properties));
        }
        //将 cofing 作参数代入，调用 factory 的 applyAsync 方法创建 AsyncPredicate 对象。
        return factory.applyAsync(config);
	}
}
