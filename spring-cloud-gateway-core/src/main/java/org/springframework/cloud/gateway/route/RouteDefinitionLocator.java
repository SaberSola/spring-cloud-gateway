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

import reactor.core.publisher.Flux;

/**
 * @author Spencer Gibb
 * 外部化配置定义 Route 使用的是 RouteDefinition 组件。
 * 同样的也有配套的 RouteDefinitionLocator 组件。
 * //路由定位器接口
 * FLux 响应式编程
 */
public interface RouteDefinitionLocator {

	Flux<RouteDefinition> getRouteDefinitions();
}
