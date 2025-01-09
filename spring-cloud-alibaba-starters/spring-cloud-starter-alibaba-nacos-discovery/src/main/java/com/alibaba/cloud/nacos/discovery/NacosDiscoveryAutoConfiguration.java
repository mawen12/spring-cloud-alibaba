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

package com.alibaba.cloud.nacos.discovery;

import com.alibaba.cloud.nacos.ConditionalOnNacosDiscoveryEnabled;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceManager;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Nacos服务发现的自动配置，该配置开启的条件有两个：
 * <ul>
 *     <li>PROPERTIES(spring.cloud.discovery.enabled)=true</li>
 *     <li>PROPERTIES(spring.cloud.nacos.discovery.enabled)=true</li>
 * </ul>
 * <p>
 * 该类负责注册服务发现相关的类，此处注册两个：
 * <ul>
 *     <li>{@link NacosDiscoveryProperties}</li>
 *     <li>{@link NacosDiscoveryClient}</li>
 * </ul>
 * <p>
 * 严格来说，只需要注册{@link NacosDiscoveryClient}，但其依赖{@link NacosDiscoveryProperties}和{@link NacosServiceManager}。
 * 其中{@link NacosServiceManager}已由{@link com.alibaba.cloud.nacos.NacosServiceAutoConfiguration}进行了注册，所以此处仅需注册{@link NacosDiscoveryProperties}。
 *
 * @author <a href="mailto:echooy.mxq@gmail.com">echooymxq</a>
 **/
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnNacosDiscoveryEnabled
public class NacosDiscoveryAutoConfiguration {

	/**
	 * 注册{@link NacosDiscoveryClient}所需的依赖，其负责提供启动的属性。
	 * 该类可能存在被其他地方注册的情况，因此加上了{@link ConditionalOnMissingBean}避免二次注册
	 *
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean
	public NacosDiscoveryProperties nacosProperties() {
		return new NacosDiscoveryProperties();
	}

	/**
	 * 注册Nacos服务发现类，该类的依赖之一{@link NacosServiceManager}由{@link com.alibaba.cloud.nacos.NacosServiceAutoConfiguration}负责注册。
	 * 该类可能存在被其他地方注册的情况，因此加上了{@link ConditionalOnMissingBean}避免二次注册
	 *
	 * @param discoveryProperties
	 * @param nacosServiceManager
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean
	public NacosServiceDiscovery nacosServiceDiscovery(
			NacosDiscoveryProperties discoveryProperties,
			NacosServiceManager nacosServiceManager) {
		return new NacosServiceDiscovery(discoveryProperties, nacosServiceManager);
	}

}
