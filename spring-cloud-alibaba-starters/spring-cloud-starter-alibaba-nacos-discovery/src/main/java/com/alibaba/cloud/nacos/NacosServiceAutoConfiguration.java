/*
 * Copyright 2013-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.nacos;

import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Nacos服务自动配置，该配置的启动条件有两个：
 * <ul>
 *     <li>PROPERTIES(spring.cloud.discovery.enabled)=true</li>
 *     <li>PROPERTIES(spring.cloud.nacos.discovery.enabled)=true</li>
 * </ul>
 * <p>
 * 该类负责注册服务相关的类，此处仅注册{@link NacosServiceManager}。
 *
 * @author yuhuangbin
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnNacosDiscoveryEnabled
public class NacosServiceAutoConfiguration {

	/**
	 * 将Nacos服务管理器注册为Bean，该类提供了服务注册、服务订阅、服务维护等功能。
	 * {@link NacosServiceManager}仅由本类进行注册，因此无法额外添加{@link org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean}
	 *
	 * @return
	 */
	@Bean
	public NacosServiceManager nacosServiceManager() {
		return new NacosServiceManager();
	}

}
