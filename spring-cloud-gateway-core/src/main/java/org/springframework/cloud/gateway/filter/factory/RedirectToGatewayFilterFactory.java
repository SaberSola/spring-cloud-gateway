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

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.parse;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 *
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *       # =====================================
 *       - id: prefixpath_route
 *         uri: http://example.org
 *         filters:
 *         - RedirectTo=302, http://www.iocoder.cn
 * @author Spencer Gibb
 */
public class RedirectToGatewayFilterFactory extends AbstractGatewayFilterFactory<RedirectToGatewayFilterFactory.Config> {

	public static final String STATUS_KEY = "status";    //注意status 必须是3XX  重定向状态
	public static final String URL_KEY = "url";

	public RedirectToGatewayFilterFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(STATUS_KEY, URL_KEY);
	}

	@Override
	public GatewayFilter apply(Config config) {
		return apply(config.status, config.url);
	}

	public GatewayFilter apply(String statusString, String urlString) {
		final HttpStatus httpStatus = parse(statusString);
		Assert.isTrue(httpStatus.is3xxRedirection(), "status must be a 3xx code, but was " + statusString);
		final URI url = URI.create(urlString);     //解析配置的uriString
		return apply(httpStatus, url);
	}

	public GatewayFilter apply(HttpStatus httpStatus, URI uri) {

		return (exchange, chain) ->
				chain.filter(exchange).then(Mono.defer(() -> {     //defer 是一种冷的发布 只有当订阅时候才发生一次http请求  then(Mono) 返回另一个Mono
					if (!exchange.getResponse().isCommitted()) {   //判断响应的提交状态 要是没提交 设置响应头
						setResponseStatus(exchange, httpStatus);

						final ServerHttpResponse response = exchange.getResponse();
						response.getHeaders().set(HttpHeaders.LOCATION, uri.toString());
						return response.setComplete();             // 返回Mono 包含了  结果
					}
					return Mono.empty();     //创建一个空结果 表示响应已经体检
				}));
	}

	public static class Config {
		String status;
		String url;

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}
	}

}
