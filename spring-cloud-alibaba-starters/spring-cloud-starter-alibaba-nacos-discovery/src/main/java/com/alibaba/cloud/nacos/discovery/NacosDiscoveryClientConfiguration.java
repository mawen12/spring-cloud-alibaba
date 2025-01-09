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

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.CommonsClientAutoConfiguration;
import org.springframework.cloud.client.ConditionalOnBlockingDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Nacos服务发现的配置，该配置的开启条件如下：
 * <ul>
 *     <li>PROPERTIES(spring.cloud.discovery.enabled)=true</li>
 *     <li>PROPERTIES(spring.cloud.discovery.blocking.enabled)=true</li>
 *     <li>PROPERTIES(spring.cloud.nacos.discovery.enabled)=true</li>
 * </ul>
 * <p>
 * 触发顺序为：{@link NacosDiscoveryAutoConfiguration} -> {@code This} -> {@link SimpleDiscoveryClientAutoConfiguration} {@link CommonsClientAutoConfiguration}
 *
 * @author xiaojing
 * @author echooymxq
 * @author ruansheng
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnBlockingDiscoveryEnabled
@ConditionalOnNacosDiscoveryEnabled
@AutoConfigureBefore({ SimpleDiscoveryClientAutoConfiguration.class, CommonsClientAutoConfiguration.class })
@AutoConfigureAfter(NacosDiscoveryAutoConfiguration.class)
public class NacosDiscoveryClientConfiguration {

	/**
	 * 注册服务发现类，该类依赖{@link NacosServiceDiscovery}，因此该配置类的注册需要在{@link NacosDiscoveryAutoConfiguration}之后
	 *
	 * @param nacosServiceDiscovery
	 * @return
	 */
	@Bean
	public DiscoveryClient nacosDiscoveryClient(
			NacosServiceDiscovery nacosServiceDiscovery) {
		return new NacosDiscoveryClient(nacosServiceDiscovery);
	}

	/**
	 * 注册Nacos观察器，该观察其监听当前实例发生变化的事件，并更新{@link NacosDiscoveryProperties#metadata}
	 * 该类依赖{@link NacosServiceManager}和{@link NacosDiscoveryProperties}，
	 * 因此该配置类的注册需要在{@link NacosDiscoveryAutoConfiguration}之后
	 *
	 * NacosWatch is no longer enabled by default .
	 * see https://github.com/alibaba/spring-cloud-alibaba/issues/2868
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(value = "spring.cloud.nacos.discovery.watch.enabled", matchIfMissing = false)
	public NacosWatch nacosWatch(NacosServiceManager nacosServiceManager,
			NacosDiscoveryProperties nacosDiscoveryProperties) {
		return new NacosWatch(nacosServiceManager, nacosDiscoveryProperties);
	}

}
