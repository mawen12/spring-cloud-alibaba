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

package com.alibaba.cloud.nacos.loadbalancer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.util.InetIPv6Utils;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;

/**
 * 基于Nacos的负载均衡器，这是基于Spring Cloud Common的{@link ReactorServiceInstanceLoadBalancer}的特定实现
 *
 * see original.
 * {@link org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer}
 *
 * @author XuDaojie
 * @since 2021.1
 */
public class NacosLoadBalancer implements ReactorServiceInstanceLoadBalancer {

	private static final Logger log = LoggerFactory.getLogger(NacosLoadBalancer.class);

	/**
	 * 服务名称，不包含分组
	 */
	private final String serviceId;

	private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

	/**
	 * Nacos注册中心属性
	 */
	private final NacosDiscoveryProperties nacosDiscoveryProperties;

	private static final String IPV4_REGEX = "((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})(.((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})){3}";

	private static final String IPV6_KEY = "IPv6";
	/**
	 * Storage local valid IPv6 address, it's a flag whether local machine support IPv6 address stack.
	 */
	public static String ipv6;

	/**
	 * 检测ipv6相关的工具类
	 */
	private final InetIPv6Utils inetIPv6Utils;

	/**
	 * List<服务实例过滤器>
	 */
	private final List<ServiceInstanceFilter> serviceInstanceFilters;

	/**
	 * Map<服务id, 负载均衡算法>
	 */
	private final Map<String, LoadBalancerAlgorithm> loadBalancerAlgorithmMap;

	@PostConstruct
	public void init() {
		/**
		 * 获取实例ip
		 */
		String ip = nacosDiscoveryProperties.getIp();
		if (StringUtils.isNotEmpty(ip)) {
			/**
			 * 如果有ip，且该ip是ipv4，那么便从metadata(IPV6)获取，否则标识将该ip作为ipv6
			 */
			ipv6 = Pattern.matches(IPV4_REGEX, ip) ? nacosDiscoveryProperties.getMetadata().get(IPV6_KEY) : ip;
		}
		else {
			/**
			 * 自行解析ipv6地址
			 */
			ipv6 = inetIPv6Utils.findIPv6Address();
		}
	}

	private List<ServiceInstance> filterInstanceByIpType(List<ServiceInstance> instances) {
		/**
		 * 优先解析ipv6
		 */
		if (StringUtils.isNotEmpty(ipv6)) {
			/**
			 * TODO by mawen 要么指定容量，要么使用LinkedList
			 */
			List<ServiceInstance> ipv6InstanceList = new ArrayList<>();
			for (ServiceInstance instance : instances) {
				/**
				 * 匹配ipv4格式，且设置了metadata(IPV6)元信息，则加入到集合中；或者匹配ipv6，直接加入集合中
				 */
				if (Pattern.matches(IPV4_REGEX, instance.getHost())) {
					if (StringUtils.isNotEmpty(instance.getMetadata().get(IPV6_KEY))) {
						ipv6InstanceList.add(instance);
					}
				}
				else {
					ipv6InstanceList.add(instance);
				}
			}

			/**
			 * 实例没有提供ipv6，则直接使用ipv4
			 *
			 * TODO by mawen simplify by only check !ipv6InstanceList.isEmpty()
			 */
			if (ipv6InstanceList.isEmpty()) {
				return instances.stream()
						.filter(instance -> Pattern.matches(IPV4_REGEX, instance.getHost()))
						.collect(Collectors.toList());
			}
			else {
				return ipv6InstanceList;
			}
		}
		return instances.stream()
				.filter(instance -> Pattern.matches(IPV4_REGEX, instance.getHost()))
				.collect(Collectors.toList());
	}

	public NacosLoadBalancer(
			ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider,
			String serviceId, NacosDiscoveryProperties nacosDiscoveryProperties, InetIPv6Utils inetIPv6Utils,
			List<ServiceInstanceFilter> serviceInstanceFilters,
			Map<String, LoadBalancerAlgorithm> loadBalancerAlgorithmMap) {
		this.serviceId = serviceId;
		this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
		this.nacosDiscoveryProperties = nacosDiscoveryProperties;
		this.inetIPv6Utils = inetIPv6Utils;
		this.serviceInstanceFilters = serviceInstanceFilters;
		this.loadBalancerAlgorithmMap = loadBalancerAlgorithmMap;
	}

	@Override
	public Mono<Response<ServiceInstance>> choose(Request request) {
		ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider.getIfAvailable(NoopServiceInstanceListSupplier::new);
		return supplier.get(request).next().map(serviceInstances -> getInstanceResponse(request, serviceInstances));
	}

	private Response<ServiceInstance> getInstanceResponse(Request<?> request, List<ServiceInstance> serviceInstances) {
		/**
		 * 如果没有任何实例，便直接返回空响应，因为请求无法到达目的地
		 */
		if (serviceInstances.isEmpty()) {
			log.warn("No servers available for service: {}", this.serviceId);
			return new EmptyResponse();
		}

		try {
			/**
			 * 获取实例的集群
			 */
			String clusterName = this.nacosDiscoveryProperties.getClusterName();

			List<ServiceInstance> instancesToChoose = serviceInstances;

			if (StringUtils.isNotBlank(clusterName)) {
				List<ServiceInstance> sameClusterInstances = serviceInstances.stream()
						.filter(serviceInstance -> {
							/**
							 * 从 metadata(nacos.cluster) 获取集群名称
							 */
							String cluster = serviceInstance.getMetadata().get("nacos.cluster");
							/**
							 * 过滤出相同集群的实例
							 */
							return StringUtils.equals(cluster, clusterName);
						}).collect(Collectors.toList());
				if (!CollectionUtils.isEmpty(sameClusterInstances)) {
					instancesToChoose = sameClusterInstances;
				}
			}
			else {
				/**
				 * 未指定集群的话，但是其他服务指定了集群，就有可能发生跨集群调用
				 */
				log.warn("A cross-cluster call occurs，name = {}, clusterName = {}, instance = {}", serviceId, clusterName, serviceInstances);
			}
			/**
			 * 获取特定ip类型的实例，优先ipv6，其次ipv4
			 */
			instancesToChoose = this.filterInstanceByIpType(instancesToChoose);

			// Filter the service list sequentially based on the order number
			/**
			 * 使用实例过滤器过滤
			 */
			for (ServiceInstanceFilter filter : serviceInstanceFilters) {
				instancesToChoose = filter.filterInstance(request, instancesToChoose);
			}

			ServiceInstance instance;
			// Find the corresponding load balancing algorithm through the service ID and select the final service instance
			/**
			 * 是否有对应服务名称的负载均衡算法，如果没有的话，则使用默认的负载均衡算法
			 */
			if (loadBalancerAlgorithmMap.containsKey(serviceId)) {
				instance = loadBalancerAlgorithmMap.get(serviceId).getInstance(request, instancesToChoose);
			}
			else {
				instance = loadBalancerAlgorithmMap.get(LoadBalancerAlgorithm.DEFAULT_SERVICE_ID).getInstance(request, instancesToChoose);
			}

			/**
			 * 将选择的实例返回
			 */
			return new DefaultResponse(instance);
		}
		catch (Exception e) {
			log.warn("NacosLoadBalancer error", e);
			return null;
		}
	}

}
