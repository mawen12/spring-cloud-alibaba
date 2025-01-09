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
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

/**
 * 基于Nacos的服务发现，这是基于Spring Cloud Common的{@link DiscoveryClient}的特定实现
 *
 * @author xiaojing
 * @author renhaojun
 * @author echooymxq
 * @author freeman
 */
public class NacosDiscoveryClient implements DiscoveryClient {

	private static final Logger log = LoggerFactory.getLogger(NacosDiscoveryClient.class);

	/**
	 * Nacos Discovery Client Description.
	 */
	public static final String DESCRIPTION = "Spring Cloud Nacos Discovery Client";

	/**
	 * Nacos服务发现，提供获取服务及实例的功能
	 */
	private NacosServiceDiscovery serviceDiscovery;

	/**
	 * 是否开启故障容错，默认为false，故障容错主要用于
	 */
	@Value("${spring.cloud.nacos.discovery.failure-tolerance-enabled:false}")
	private boolean failureToleranceEnabled;

	public NacosDiscoveryClient(NacosServiceDiscovery nacosServiceDiscovery) {
		this.serviceDiscovery = nacosServiceDiscovery;
	}

	/**
	 * 返回该服务发现的描述
	 *
	 * @return
	 */
	@Override
	public String description() {
		return DESCRIPTION;
	}

	/**
	 * 返回指定服务id，特定分组，且健康的实例列表
	 *
	 * @param serviceId
	 * @return
	 */
	@Override
	public List<ServiceInstance> getInstances(String serviceId) {
		try {
			return Optional.of(serviceDiscovery.getInstances(serviceId))
					.map(instances -> {
						/**
						 * 将服务id和对应实例写入缓存
						 */
						ServiceCache.setInstances(serviceId, instances);
						return instances;
					}).get();
		}
		catch (Exception e) {
			/**
			 * 当开启故障容错时，获取异常出错时，从缓存中获取实例列表；否则抛出异常
			 * 需要注意，如果第一次调用，会直接返回空
			 */
			if (failureToleranceEnabled) {
				return ServiceCache.getInstances(serviceId);
			}
			throw new RuntimeException("Can not get hosts from nacos server. serviceId: " + serviceId, e);
		}
	}

	/**
	 * 返回特定分组下所有的服务名称，发生异常时，不会抛出异常
	 *
	 * @return
	 */
	@Override
	public List<String> getServices() {
		try {
			return Optional.of(serviceDiscovery.getServices()).map(services -> {
				/**
				 * 将服务名称列表写入缓存
				 */
				ServiceCache.setServiceIds(services);
				return services;
			}).get();
		}
		catch (Exception e) {
			log.error("get service name from nacos server failed.", e);
			/**
			 * 当开启故障容错时，获取异常出错时，从缓存中获取服务名称列表，否则返回空集合
			 */
			return failureToleranceEnabled ? ServiceCache.getServiceIds() : Collections.emptyList();
		}
	}

}
