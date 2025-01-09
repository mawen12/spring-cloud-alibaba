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

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.core.Ordered;

/**
 * 负载均衡算法接口
 *
 * @author <a href="mailto:zhangbin1010@qq.com">zhangbinhub</a>
 */
public interface LoadBalancerAlgorithm extends Ordered {
	/**
	 * default service id.
	 */
	String DEFAULT_SERVICE_ID = "defaultServiceId";

	/**
	 * 返回服务名称
	 *
	 * @return
	 */
	String getServiceId();

	/**
	 * 根据请求和实例信息集合，通过负载负载均衡算法，返回一个实例
	 *
	 * @param request
	 * @param serviceInstances
	 * @return
	 */
	ServiceInstance getInstance(Request<?> request, List<ServiceInstance> serviceInstances);
}
