/*
 * Copyright 2023-2024 the original author or authors.
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

import java.util.List;

import com.alibaba.cloud.nacos.balancer.NacosBalancer;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.core.Ordered;

/**
 * 默认的负载均衡算法实现，底层基于{@link NacosBalancer}的基于权重的随机选择算法。
 * 该算法仅会从权重>=0且健康状态的实例中随机选择
 *
 * @author <a href="mailto:zhangbin1010@qq.com">zhangbinhub</a>
 */
public class DefaultLoadBalancerAlgorithm implements LoadBalancerAlgorithm {
	/**
	 * 返回默认值，即DEFAULT(defaultServiceId)
	 * @return
	 */
	@Override
	public String getServiceId() {
		return LoadBalancerAlgorithm.DEFAULT_SERVICE_ID;
	}

	@Override
	public ServiceInstance getInstance(Request<?> request, List<ServiceInstance> serviceInstances) {
		/**
		 * 使用基于权重的随机选择算法，其中仅会选择权重>0，并且健康状态的实例中随机选择
		 */
		return NacosBalancer.getHostByRandomWeight3(serviceInstances);
	}

	/**
	 * 返回最低优先级
	 *
	 * @return
	 */
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}
}
