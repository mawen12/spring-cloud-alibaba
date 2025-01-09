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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.cloud.nacos.discovery.reactive.NacosReactiveDiscoveryClient;

import org.springframework.cloud.client.ServiceInstance;

/**
 * 服务缓存
 * <p>
 * 缓存Nacos服务器上特定分组的服务名以及对应服务的健康状态的实例列表
 * <p>
 * 该缓存是基于简单的数据结构，因为本身数据量不大的原因，所以使用了内置的数据结构，而非正式的缓存工具。
 * 该缓存是用于提升查询速度，但是内部的数据并非实时的。
 * 缓存数据的刷新主要依赖于{@link NacosDiscoveryClient#getServices()}以及{@link NacosDiscoveryClient#getInstances(String)}的调用，
 * 基于异步的{@link NacosReactiveDiscoveryClient#getServices()}以及{@link NacosReactiveDiscoveryClient#getInstances(String)}的调用。
 *
 * @author freeman
 * @since 2021.0.1.0
 */
public final class ServiceCache {

	private ServiceCache() {
	}

	/**
	 * Nacos服务器上特定于{@link com.alibaba.cloud.nacos.NacosDiscoveryProperties#group}的所有服务名称
	 */
	private static List<String> services = Collections.emptyList();

	/**
	 * Map<特定分组的服务名称, 归属服务下指定分组且健康状态的实例>
	 * Nacos服务器上特定于{@link com.alibaba.cloud.nacos.NacosDiscoveryProperties#group}的所有服务和健康实例信息的映射
	 */
	private static Map<String, List<ServiceInstance>> instancesMap = new ConcurrentHashMap<>();

	/**
	 * 将特定分组的服务id和健康实例列表写入本地缓存
	 * Set instances for specific service.
	 * @param serviceId service id
	 * @param instances service instances
	 */
	public static void setInstances(String serviceId, List<ServiceInstance> instances) {
		instancesMap.put(serviceId, Collections.unmodifiableList(instances));
	}

	/**
	 * Get instances for specific service.
	 * @param serviceId service id
	 * @return service instances
	 */
	public static List<ServiceInstance> getInstances(String serviceId) {
		return Optional.ofNullable(instancesMap.get(serviceId))
				.orElse(Collections.emptyList());
	}

	/**
	 * Set all services.
	 * @param serviceIds all services
	 * @deprecated since 2021.0.1.1, use {@link #setServiceIds(List)} instead.
	 */
	@Deprecated
	public static void set(List<String> serviceIds) {
		services = Collections.unmodifiableList(serviceIds);
	}

	/**
	 * Set all services.
	 * @param serviceIds all services
	 * @since 2021.0.1.1
	 */
	public static void setServiceIds(List<String> serviceIds) {
		services = Collections.unmodifiableList(serviceIds);
	}

	/**
	 * Get all services.
	 * @return all services
	 * @deprecated since 2021.0.1.1, use {@link #getServiceIds()} instead.
	 */
	@Deprecated
	public static List<String> get() {
		return services;
	}

	/**
	 * Get all services.
	 * @return all services
	 * @since 2021.0.1.1
	 */
	public static List<String> getServiceIds() {
		return services;
	}

}
