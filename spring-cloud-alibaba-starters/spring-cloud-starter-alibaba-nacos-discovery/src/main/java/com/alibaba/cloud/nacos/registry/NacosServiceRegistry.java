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
import java.util.Properties;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

import static org.springframework.util.ReflectionUtils.rethrowRuntimeException;

/**
 * 基于Nacos的服务注册注销，以及状态获取与更新{@link Instance#enabled}，这是基于Spring Cloud Common的{@link ServiceRegistry}的特定实现
 *
 * @author xiaojing
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @author <a href="mailto:78552423@qq.com">eshun</a>
 * @author JAY
 */
public class NacosServiceRegistry implements ServiceRegistry<Registration> {

	private static final String STATUS_UP = "UP";

	private static final String STATUS_DOWN = "DOWN";

	private static final Logger log = LoggerFactory.getLogger(NacosServiceRegistry.class);

	/**
	 * Nacos注册中心属性，包含了注册服务的所有信息
	 */
	private final NacosDiscoveryProperties nacosDiscoveryProperties;

	/**
	 * Nacos服务管理器，提供实例注册、服务订阅、服务维护等功能
	 */
	private final NacosServiceManager nacosServiceManager;

	public NacosServiceRegistry(NacosServiceManager nacosServiceManager,
			NacosDiscoveryProperties nacosDiscoveryProperties) {
		this.nacosDiscoveryProperties = nacosDiscoveryProperties;
		this.nacosServiceManager = nacosServiceManager;
	}

	/**
	 * 将带有实例信息的{@link Registration}的实例注册到Nacos上
	 *
	 * @param registration registration meta data
	 */
	@Override
	public void register(Registration registration) {
		/**
		 * 目标服务不能为空，因为实例是属于服务下的
		 */
		if (StringUtils.isEmpty(registration.getServiceId())) {
			log.warn("No service to register for nacos client...");
			return;
		}

		/**
		 * 获取负责实例注册的注册中心类
		 */
		NamingService namingService = namingService();
		/**
		 * 获取服务名称
		 */
		String serviceId = registration.getServiceId();
		/**
		 * 获取分组名称
		 */
		String group = nacosDiscoveryProperties.getGroup();

		/**
		 * 从 Registration -> Instance，将其转换为{@link NamingService}支持的实例对象
		 */
		Instance instance = getNacosInstanceFromRegistration(registration);

		try {
			/**
			 * 执行实例注册，注册到特定服务、特定分组、特定集群
			 */
			namingService.registerInstance(serviceId, group, instance);

			log.info("nacos registry, {} {} {}:{} register finished", group, serviceId, instance.getIp(), instance.getPort());
		}
		catch (Exception e) {
			/**
			 * 如果 PROPERTIES(spring.cloud.nacos.discovery.failFast)=true，不仅打印日志，还抛出运行时异常；反之仅打印日志，不中断当前线程
			 */
			if (nacosDiscoveryProperties.isFailFast()) {
				// TODO by mawen remove toString()
				log.error("nacos registry, {} register failed...{},", serviceId, registration.toString(), e);
				rethrowRuntimeException(e);
			}
			else {
				// TODO by mawen remove toString()
				log.warn("Failfast is false. {} register failed...{},", serviceId, registration.toString(), e);
			}
		}
	}

	/**
	 * 将Nacos上指定实例注销
	 *
	 * @param registration registration meta data
	 */
	@Override
	public void deregister(Registration registration) {

		log.info("De-registering from Nacos Server now...");
		/**
		 * 目标服务不能为空，因为实例是属于服务下的
		 */
		if (StringUtils.isEmpty(registration.getServiceId())) {
			log.warn("No dom to de-register for nacos client...");
			return;
		}
		/**
		 * 获取负责实例注销的注册中心类
		 */
		NamingService namingService = namingService();
		/**
		 * 获取服务名称
		 */
		String serviceId = registration.getServiceId();
		/**
		 * 获取分组名称
		 */
		String group = nacosDiscoveryProperties.getGroup();

		try {
			/**
			 * 执行服务注销
			 */
			namingService.deregisterInstance(serviceId, group, registration.getHost(), registration.getPort(), nacosDiscoveryProperties.getClusterName());
		}
		catch (Exception e) {
			log.error("ERR_NACOS_DEREGISTER, de-register failed...{},", registration.toString(), e);
		}

		log.info("De-registration finished.");
	}

	/**
	 * 生命周期函数，停止
	 */
	@Override
	public void close() {
		try {
			nacosServiceManager.nacosServiceShutDown();
		}
		catch (NacosException e) {
			log.error("Nacos namingService shutDown failed", e);
		}
	}

	/**
	 * 将实例状态更新到Nacos Server上
	 *
	 * @param registration The registration to update.
	 * @param status The status to set.
	 */
	@Override
	public void setStatus(Registration registration, String status) {
		/**
		 * 更新实例状态，仅接受UP或DOWN
		 */
		if (!STATUS_UP.equalsIgnoreCase(status) && !STATUS_DOWN.equalsIgnoreCase(status)) {
			log.warn("can't support status {},please choose UP or DOWN", status);
			return;
		}

		/**
		 * 获取服务名称
		 */
		String serviceId = registration.getServiceId();
		/**
		 * 从 Registration -> Instance
		 */
		Instance instance = getNacosInstanceFromRegistration(registration);

		/**
		 * 更新实例状态
		 */
		if (STATUS_DOWN.equalsIgnoreCase(status)) {
			instance.setEnabled(false);
		}
		else {
			instance.setEnabled(true);
		}

		try {
			Properties nacosProperties = nacosDiscoveryProperties.getNacosProperties();
			/**
			 * 使用维护服务将实例状态通知到Nacos Server
			 */
			nacosServiceManager.getNamingMaintainService(nacosProperties).updateInstance(serviceId, nacosDiscoveryProperties.getGroup(), instance);
		}
		catch (Exception e) {
			throw new RuntimeException("update nacos instance status fail", e);
		}

	}

	/**
	 * 从Nacos Server获取当前实例的状态
	 *
	 * @param registration The registration to query.
	 * @return
	 */
	@Override
	public Object getStatus(Registration registration) {
        /**
         * 获取服务名称
         */
		String serviceName = registration.getServiceId();
        /**
         * 获取分组名称
         */
		String group = nacosDiscoveryProperties.getGroup();
		try {
            /**
             * 获取特定服务、特定分组下可订阅的所有服务
             */
			List<Instance> instances = namingService().getAllInstances(serviceName,group);
			for (Instance instance : instances) {
				/**
				 * 过滤当前实例
				 */
				if (instance.getIp().equalsIgnoreCase(nacosDiscoveryProperties.getIp()) && instance.getPort() == nacosDiscoveryProperties.getPort()) {
					/**
					 * 根据实例的状态来确定UP还是DOWN
					 */
					return instance.isEnabled() ? STATUS_UP : STATUS_DOWN;
				}
			}
		}
		catch (Exception e) {
			log.error("get all instance of {} error,", serviceName, e);
		}
		return null;
	}

	private Instance getNacosInstanceFromRegistration(Registration registration) {
		/**
		 * 构造instance
 		 */
		Instance instance = new Instance();
		/**
		 * instance.ip -> PROPERTIES(spring.cloud.nacos.discovery.ip)
		 */
		instance.setIp(registration.getHost());
		/**
		 * instance.port -> PROPERTIES(spring.cloud.nacos.discovery.port)
		 */
		instance.setPort(registration.getPort());
		/**
		 * instance.weight -> PROPERTIES(spring.cloud.nacos.discovery.weight)
		 */
		instance.setWeight(nacosDiscoveryProperties.getWeight());
		/**
		 * instance.ip -> PROPERTIES(spring.cloud.nacos.discovery.clusterName)
		 */
		instance.setClusterName(nacosDiscoveryProperties.getClusterName());
		/**
		 * instance.ip -> PROPERTIES(spring.cloud.nacos.discovery.instanceEnabled)
		 */
		instance.setEnabled(nacosDiscoveryProperties.isInstanceEnabled());
		/**
		 * instance.ip -> PROPERTIES(spring.cloud.nacos.discovery.metadata)
		 */
		instance.setMetadata(registration.getMetadata());
		/**
		 * instance.ip -> PROPERTIES(spring.cloud.nacos.discovery.ephemeral)
		 */
		instance.setEphemeral(nacosDiscoveryProperties.isEphemeral());
		return instance;
	}

	private NamingService namingService() {
		return nacosServiceManager.getNamingService();
	}

}
