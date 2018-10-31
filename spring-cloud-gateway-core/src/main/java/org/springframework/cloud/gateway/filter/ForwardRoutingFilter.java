package org.springframework.cloud.gateway.filter;

import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

/**
 * spring:
 *   application:
 *       name: juejin-gateway
 *   cloud:
 *     gateway:
 *       routes:
 *       # =====================================
 *       - id: forward_sample
 *         uri: forward:///globalfilters
 *         order: 10000
 *         predicates:
 *         - Path=/globalfilters
 *         filters:
 *         - PrefixPath=/application/gateway
 *
 */
public class ForwardRoutingFilter implements GlobalFilter, Ordered {


	/**
	 *   我们假定网关端口为 8080 。
	 *
	 *   当请求 http://127.0.0.1:8080/globalfilters
	 *
	 *   匹配到路由 Route (id = forward_sample) 。
	 *
	 *   配置的 PrefixPathGatewayFilterFactory 将请求改写成 http://127.0.0.1:8080/application/gateway/globalfilters
	 *
	 *   ForwardRoutingFilter 判断有 forward:// 前缀( Scheme )，过滤处理，将请求转发给 DispatcherHandler 。
	 *
	 *   DispatcherHandler 匹配并转发到当前网关实例本地接口 application/gateway/globalfilters
	 *
	 *   为什么需要配置 PrefixPathGatewayFilterFactory ？需要通过 PrefixPathGatewayFilterFactory 将请求重写路径，
	 *   以匹配本地 API ，否则 DispatcherHandler 转发会失败。
	 *
	 */


	private static final Log log = LogFactory.getLog(ForwardRoutingFilter.class);

	/**
	 * 类似于 @Autowired
	 * 注解的另外一种形式
	 */
	private final ObjectProvider<DispatcherHandler> dispatcherHandler;    //ObjectProvider 是一个Object的工厂  DispatcherHandler 可以直接从里边get


	public ForwardRoutingFilter(ObjectProvider<DispatcherHandler> dispatcherHandler) {
		this.dispatcherHandler = dispatcherHandler;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;  // 返回Integer.MAXVALUE
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

		//获取requestUrl
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		//获取有没有前缀 没有的话直接过滤到下一个
		String scheme = requestUrl.getScheme();
		if (isAlreadyRouted(exchange) || !"forward".equals(scheme)) {
			return chain.filter(exchange);
		}
		//设置已经路由
		setAlreadyRouted(exchange);

		//TODO: translate url?

		if (log.isTraceEnabled()) {
			log.trace("Forwarding to URI: "+requestUrl);
		}

		// DispatcherHandler 匹配并转发到当前网关实例本地接口
		return this.dispatcherHandler.getIfAvailable().handle(exchange);
	}
}
