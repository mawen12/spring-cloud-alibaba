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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceInstance;
import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;

import org.springframework.cloud.client.ServiceInstance;

/**
 * Nacos服务发现，提供以下功能：
 * <ul>
 *    <li>从Nacos Server获取特定分组的所有服务</li>
 *    <li>从Nacos Server获取指定服务、特定分组的所有健康实例</li>
 * </ul>
 * 需要注意的是，返回的是{@link NacosServiceInstance}，而非原始的{@link Instance}
 *
 * @author <a href="mailto:echooy.mxq@gmail.com">echooymxq</a>
 * @author changjin wei(魏昌进)
 **/
public class NacosServiceDiscovery {

	/**
	 * Nacos注册中心属性，提供启动配置的属性
	 */
	private NacosDiscoveryProperties discoveryProperties;

	/**
	 * Nacos服务管理器，提供实例注册、服务订阅、服务维护功能
	 */
	private NacosServiceManager nacosServiceManager;

	public NacosServiceDiscovery(NacosDiscoveryProperties discoveryProperties, NacosServiceManager nacosServiceManager) {
		this.discoveryProperties = discoveryProperties;
		this.nacosServiceManager = nacosServiceManager;
	}

	/**
	 * 返回给定服务id、特定分组下所有健康的实例，该实例对象为{@link NacosServiceInstance}，这是SpringCloud中定义的扩展
	 *
	 * @param serviceId id of service
	 * @return list of instances
	 * @throws NacosException nacosException
	 */
	public List<ServiceInstance> getInstances(String serviceId) throws NacosException {
		/**
		 * 获取实例所属的分组
		 */
		String group = discoveryProperties.getGroup();
		/**
		 * 获取Nacos上指定服务id，分组名称，并且是健康状态的所有实例
		 */
		List<Instance> instances = namingService().selectInstances(serviceId, group, true);
		/**
		 * 从 List<Instance> -> List<NacosServiceInstance>
		 */
		return hostToServiceInstanceList(instances, serviceId);
	}

	/**
	 * 返回特定分组下的所有服务名称
	 *
	 * @return list of service names
	 * @throws NacosException nacosException
	 */
	public List<String> getServices() throws NacosException {
		/**
		 * 获取启动时配置的分组
		 */
		String group = discoveryProperties.getGroup();
		/**
		 * 从Nacos服务器上读取特定分组下的所有实例名称
		 */
		ListView<String> services = namingService().getServicesOfServer(1, Integer.MAX_VALUE, group);
		return services.getData();
	}

	public static List<ServiceInstance> hostToServiceInstanceList(List<Instance> instances, String serviceId) {
		List<ServiceInstance> result = new ArrayList<>(instances.size());
		for (Instance instance : instances) {
			/**
			 * 从 Instance -> NacosServiceInstance
			 */
			ServiceInstance serviceInstance = hostToServiceInstance(instance, serviceId);
			if (serviceInstance != null) {
				/**
				 * 仅处理已启动并且是健康状态的实例
				 */
				result.add(serviceInstance);
			}
		}
		return result;
	}

	public static ServiceInstance hostToServiceInstance(Instance instance, String serviceId) {
		/**
		 * 仅处理已启动且为健康状态的实例
		 */
		if (instance == null || !instance.isEnabled() || !instance.isHealthy()) {
			return null;
		}

		NacosServiceInstance nacosServiceInstance = new NacosServiceInstance();
		// NacosServiceInstance.host -> Instance.ip
		nacosServiceInstance.setHost(instance.getIp());
		// NacosServiceInstance.port -> Instance.port
		nacosServiceInstance.setPort(instance.getPort());
		// NacosServiceInstance.serviceId -> serviceId
		nacosServiceInstance.setServiceId(serviceId);
		// NacosServiceInstance.instanceId -> Instance.instanceId
		nacosServiceInstance.setInstanceId(instance.getInstanceId());

		/**
		 * 固定元信息
		 */
		Map<String, String> metadata = new HashMap<>();
		/**
		 * 实例id，默认，在自定义实例元信息时需要排除
		 */
		metadata.put("nacos.instanceId", instance.getInstanceId());
		/**
		 * 实例权重，默认，在自定义实例元信息时需要排除
		 */
		metadata.put("nacos.weight", instance.getWeight() + "");
		/**
		 * 实例健康，因为方法入口过滤了不健康的实例，此处均是健康状态的实例
		 * 默认，在自定义实例元信息时需要排除
		 */
		metadata.put("nacos.healthy", instance.isHealthy() + "");
		/**
		 * TODO by mawen 移除“”，因为集群名称是字符串类型
		 * 实例所在的集群名称，默认，在自定义实例元信息时需要排除
		 */
		metadata.put("nacos.cluster", instance.getClusterName() + "");
		/**
		 * 将原有实例的元信息合并
		 */
		if (instance.getMetadata() != null) {
			metadata.putAll(instance.getMetadata());
		}
		/**
		 * 实例是否是临时的，默认，在自定义实例元信息时需要排除
		 */
		metadata.put("nacos.ephemeral", String.valueOf(instance.isEphemeral()));
		/**
		 * 回写元信息
		 */
		nacosServiceInstance.setMetadata(metadata);

		/**
		 * 读取实例元信息secure，并解析到{@link NacosServiceInstance#secure}
		 */
		if (metadata.containsKey("secure")) {
			boolean secure = Boolean.parseBoolean(metadata.get("secure"));
			nacosServiceInstance.setSecure(secure);
		}
		return nacosServiceInstance;
	}

	private NamingService namingService() {
		return nacosServiceManager.getNamingService();
	}

}
