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

package com.alibaba.cloud.nacos.registry;

import java.util.List;

import com.alibaba.cloud.nacos.ConditionalOnNacosDiscoveryEnabled;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationAutoConfiguration;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationConfiguration;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Nacos服务注册自动配置，该配置开启的条件为：
 * <ul>
 *     <li>PROPERTIES(spring.cloud.nacos.discovery.enabled)=true</li>
 *     <li>PROPERTIES(spring.cloud.service-registry.auto-registration.enabled)=true</li>
 * </ul>
 * <p>
 * 触发顺序为：{@link AutoServiceRegistrationConfiguration}, {@link AutoServiceRegistrationAutoConfiguration}, {@link NacosDiscoveryAutoConfiguration} -> This
 *
 * @author xiaojing
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties
@ConditionalOnNacosDiscoveryEnabled
@ConditionalOnProperty(value = "spring.cloud.service-registry.auto-registration.enabled", matchIfMissing = true)
@AutoConfigureAfter({ AutoServiceRegistrationConfiguration.class,
		AutoServiceRegistrationAutoConfiguration.class,
		NacosDiscoveryAutoConfiguration.class })
public class NacosServiceRegistryAutoConfiguration {

	/**
	 * 注册Nacos服务注册器，负责实例注册，其依赖{@link NacosServiceManager}和{@link NacosDiscoveryProperties}。
	 * 因此该配置类需要在{@link NacosDiscoveryAutoConfiguration}之后注册
	 *
	 * @param nacosServiceManager
	 * @param nacosDiscoveryProperties
	 * @return
	 */
	@Bean
	public NacosServiceRegistry nacosServiceRegistry(
			/**
			 * 负责执行实际的实例注册
			 */
			NacosServiceManager nacosServiceManager,
			/**
			 * 负责提供实例的相关信息
			 */
			NacosDiscoveryProperties nacosDiscoveryProperties) {
		return new NacosServiceRegistry(nacosServiceManager, nacosDiscoveryProperties);
	}

	/**
	 * 注册Nacos实例类，该实例被用于Spring Cloud场景的被注册的实例，
	 * 其仅在存在Bean(AutoServiceRegistrationProperties)时触发
	 *
	 * @param registrationCustomizers
	 * @param nacosDiscoveryProperties
	 * @param context
	 * @return
	 */
	@Bean
	@ConditionalOnBean(AutoServiceRegistrationProperties.class)
	public NacosRegistration nacosRegistration(
			ObjectProvider<List<NacosRegistrationCustomizer>> registrationCustomizers,
			NacosDiscoveryProperties nacosDiscoveryProperties,
			ApplicationContext context) {
		return new NacosRegistration(registrationCustomizers.getIfAvailable(), nacosDiscoveryProperties, context);
	}

	/**
	 * 注册Nacos自动服务注册类，仅用于Spring Cloud的自动服务注册场景
	 * 其仅在存在Bean(AutoServiceRegistrationProperties)时触发
	 *
	 * @param registry
	 * @param autoServiceRegistrationProperties
	 * @param registration
	 * @return
	 */
	@Bean
	@ConditionalOnBean(AutoServiceRegistrationProperties.class)
	public NacosAutoServiceRegistration nacosAutoServiceRegistration(
			NacosServiceRegistry registry,
			/**
			 * 自动服务注册的属性，用于将定义自动服务注册的行为
			 */
			AutoServiceRegistrationProperties autoServiceRegistrationProperties,
			NacosRegistration registration) {
		return new NacosAutoServiceRegistration(registry, autoServiceRegistrationProperties, registration);
	}

}
