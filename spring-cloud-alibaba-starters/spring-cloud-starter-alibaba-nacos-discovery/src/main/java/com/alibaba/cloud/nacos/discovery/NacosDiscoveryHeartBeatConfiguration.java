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

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ConditionalOnBlockingDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Nacos服务发现心跳配置类，该配置开启的条件如下：
 * <ul>
 *     <li>PROPERTIES(spring.cloud.discovery.enabled)=true</li>
 *     <li>PROPERTIES(spring.could.discovery.blocking.enable)=true</li>
 *     <li>PROPERTIES(spring.could.nacos.discovery.enable)=true</li>
 * </ul>
 * <p>
 * 触发顺序为：{@link NacosDiscoveryAutoConfiguration} -> This
 * <p>
 * 其内部还存在较为复杂的条件判断，总的来说，除了激活Nacos服务发现外，还需要激活任意的心跳实现，用于监听{@link org.springframework.cloud.client.discovery.event.HeartbeatEvent}
 *
 * @author xiaojing
 * @author echooymxq
 * @author ruansheng
 * @author zhangbin
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnBlockingDiscoveryEnabled
@ConditionalOnNacosDiscoveryEnabled
@AutoConfigureAfter(value = NacosDiscoveryAutoConfiguration.class, name = "de.codecentric.boot.admin.server.cloud.config.AdminServerDiscoveryAutoConfiguration")
public class NacosDiscoveryHeartBeatConfiguration {

	/**
	 * 注册Nacos服务发现心跳发布器，默认每隔30s发布{@link org.springframework.cloud.client.discovery.event.HeartbeatEvent},
	 * 仅当满足{@link NacosDiscoveryHeartBeatCondition}任意条件时触发，
	 * 该类依赖{@link NacosDiscoveryProperties}，因此该配置类的注册需要在{@link NacosDiscoveryAutoConfiguration}之后
	 *
	 * TODO by mawen commit format
	 * see https://github.com/alibaba/spring-cloud-alibaba/issues/2868
	 * see https://github.com/alibaba/spring-cloud-alibaba/issues/3258
	 */
	@Bean
	@ConditionalOnMissingBean
	@Conditional(NacosDiscoveryHeartBeatCondition.class)
	public NacosDiscoveryHeartBeatPublisher nacosDiscoveryHeartBeatPublisher(NacosDiscoveryProperties nacosDiscoveryProperties) {
		return new NacosDiscoveryHeartBeatPublisher(nacosDiscoveryProperties);
	}

	/**
	 * 继承{@link AnyNestedCondition}，代表任意条件满足时，便会触发，即它们的条件满足一个即可
	 */
	private static class NacosDiscoveryHeartBeatCondition extends AnyNestedCondition {

		NacosDiscoveryHeartBeatCondition()  {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		/**
		 * Spring Cloud Gateway 心跳，仅当PROPERTIES(spring.cloud.discovery.locator.enabled)=true触发
		 */
		@ConditionalOnProperty(value = "spring.cloud.gateway.discovery.locator.enabled", matchIfMissing = false)
		static class GatewayLocatorHeartBeatEnabled { }

		/**
		 * Spring Boot Admin 心跳，仅当BEAN(de.codecentric.boot.admin.server.cloud.discovery.InstanceDiscoveryListener)存在时触发
		 */
		@ConditionalOnBean(type = "de.codecentric.boot.admin.server.cloud.discovery.InstanceDiscoveryListener")
		static class SpringBootAdminHeartBeatEnabled { }

		/**
		 * Nacos 心跳，仅当PROPERTIES(spring.cloud.nacos.discovery.heart-beat.enabled)=true触发
		 */
		@ConditionalOnProperty(value = "spring.cloud.nacos.discovery.heart-beat.enabled", matchIfMissing = false)
		static class NacosDiscoveryHeartBeatEnabled { }
	}

}
