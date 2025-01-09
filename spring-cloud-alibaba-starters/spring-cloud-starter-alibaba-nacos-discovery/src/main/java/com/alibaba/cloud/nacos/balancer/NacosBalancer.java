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

package com.alibaba.cloud.nacos.balancer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.cloud.nacos.NacosServiceInstance;
import com.alibaba.cloud.nacos.loadbalancer.NacosLoadBalancer;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.core.Balancer;

import org.springframework.cloud.client.ServiceInstance;

/**
 * Nacos客户端均衡器
 *
 * @author itmuch.com XuDaojie
 * @since 2021.1
 */
public class NacosBalancer extends Balancer {

	private static final String IPV4_REGEX = "((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})(.((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})){3}";

	private static final String IPV6_KEY = "IPv6";

	/**
	 * 基于权重选择一个实例，其中仅会在权重>=0的实例中选择
	 *
	 * @param instances Instance List
	 * @return the chosen instance
	 */
	public static Instance getHostByRandomWeight2(List<Instance> instances) {
		return getHostByRandomWeight(instances);
	}

	/**
	 * Spring Cloud LoadBalancer Choose instance by weight.
	 *
	 * @param serviceInstances Instance List
	 * @return the chosen instance
	 */
	public static ServiceInstance getHostByRandomWeight3(List<ServiceInstance> serviceInstances) {
		Map<Instance, ServiceInstance> instanceMap = new HashMap<>();
		List<Instance> nacosInstance = serviceInstances.stream().map(serviceInstance -> {
			/**
			 * {@link ServiceInstance}中的实例权重是保存在metadata中的，key为nacos.weight
			 */
			Map<String, String> metadata = serviceInstance.getMetadata();

			// see
			// com.alibaba.cloud.nacos.discovery.NacosServiceDiscovery.hostToServiceInstance()
			Instance instance = new Instance();
			// instance.ip -> serviceInstance.host
			instance.setIp(serviceInstance.getHost());
			// instance.port -> serviceInstance.port
			instance.setPort(serviceInstance.getPort());
			// instance.weight -> metadata(nacos.weight)
			instance.setWeight(Double.parseDouble(metadata.get("nacos.weight")));
			// instance.healthy -> metadata(nacos.healthy)
			instance.setHealthy(Boolean.parseBoolean(metadata.get("nacos.healthy")));
			instanceMap.put(instance, serviceInstance);
			return instance;
		}).collect(Collectors.toList());

		/**
		 * 基于权重选择一个实例，其中仅会在权重>=0的实例中随机选择
		 */
		Instance instance = getHostByRandomWeight2(nacosInstance);
		/**
		 * 获取对应的NacosServiceInstance，因为这是处于Spring Cloud场景下，所有的实例交互都是通过{@link ServiceInstance}
		 */
		NacosServiceInstance nacosServiceInstance = (NacosServiceInstance) instanceMap.get(instance);
		// When local support IPv6 address stack, referred to use IPv6 address.
		if (StringUtils.isNotEmpty(NacosLoadBalancer.ipv6)) {
			convertIPv4ToIPv6(nacosServiceInstance);
		}
		return nacosServiceInstance;
	}

	/**
	 * There is two type Ip,using IPv6 should use IPv6 in metadata to replace IPv4 in IP
	 * field.
	 */
	private static void convertIPv4ToIPv6(NacosServiceInstance instance) {
		if (Pattern.matches(IPV4_REGEX, instance.getHost())) {
			String ip = instance.getMetadata().get(IPV6_KEY);
			if (StringUtils.isNotEmpty(ip)) {
				instance.setHost(ip);
			}
		}
	}

}
