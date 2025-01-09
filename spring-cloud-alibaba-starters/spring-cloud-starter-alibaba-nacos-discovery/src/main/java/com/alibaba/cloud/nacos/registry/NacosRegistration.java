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

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.BeanUtils;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.ManagementServerPortUtils;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * 基于Nacos的注册实例，这是基于Spring Cloud Common的{@link Registration}的特定实现
 * 其作为Spring Cloud场景下的实例，底层为{@link NacosDiscoveryProperties}
 *
 * @author xiaojing
 * @author changjin wei(魏昌进)
 */
public class NacosRegistration implements Registration {

	/**
	 * The metadata key of management port.
	 */
	public static final String MANAGEMENT_PORT = "management.port";

	/**
	 * The metadata key of management context-path.
	 */
	public static final String MANAGEMENT_CONTEXT_PATH = "management.context-path";

	/**
	 * The metadata key of management address.
	 */
	public static final String MANAGEMENT_ADDRESS = "management.address";

	/**
	 * The metadata key of management endpoints web base path.
	 */
	public static final String MANAGEMENT_ENDPOINT_BASE_PATH = "management.endpoints.web.base-path";

	/**
	 * 用户自定义的Nacos注册自定义
	 */
	private List<NacosRegistrationCustomizer> registrationCustomizers;

	/**
	 * 使用该属性的service, group, clusterName, ip, port, metadata等信息构造要注册的服务，
	 * 并且需要对metadata进行扩展
	 */
	private NacosDiscoveryProperties nacosDiscoveryProperties;

	/**
	 * Spring的应用上下文，用于通过{@link Environment}获取特定属性值
	 */
	private ApplicationContext context;

	public NacosRegistration(List<NacosRegistrationCustomizer> registrationCustomizers,
			NacosDiscoveryProperties nacosDiscoveryProperties,
			ApplicationContext context) {
		this.registrationCustomizers = registrationCustomizers;
		this.nacosDiscoveryProperties = nacosDiscoveryProperties;
		this.context = context;
	}

	/**
	 * 对注册服务的元数据进行扩展，主要扩展以下内容：
	 * <ul>
	 *     <li>management.endpoints.web.base-path</li>
	 *     <li>management.server.port</li>
	 *     <li>management.server.servlet.context-path</li>
	 *     <li>management.server.address</li>
	 *     <li>spring.cloud.nacos.discovery.heartBeatInterval</li>
	 *     <li>spring.cloud.nacos.discovery.heartBeatTimeout</li>
	 *     <li>spring.cloud.nacos.discovery.ipDeleteTimeout</li>
	 * </ul>
	 */
	@PostConstruct
	public void init() {
		/**
		 * 获取要注册实例的元信息
		 */
		Map<String, String> metadata = nacosDiscoveryProperties.getMetadata();
		/**
		 * 从Spring上下文中获取特定属性的值
		 */
		Environment env = context.getEnvironment();

		/**
		 * 从 PROPERTIES(management.endpoints.web.base-path) 解析管理端口路径，
		 * 如果有值，则写入元数据
		 */
		String endpointBasePath = env.getProperty(MANAGEMENT_ENDPOINT_BASE_PATH);
		if (StringUtils.hasLength(endpointBasePath)) {
			metadata.put(MANAGEMENT_ENDPOINT_BASE_PATH, endpointBasePath);
		}

		/**
		 * 从 PROPERTIES(management.server.port) 解析管理端口，仅在开启actuator时才解析
		 */
		Integer managementPort = ManagementServerPortUtils.getPort(context);
		if (null != managementPort) {
			/**
			 * 写入元信息
			 */
			metadata.put(MANAGEMENT_PORT, managementPort.toString());
			/**
			 * 从 PROPERTIES(management.server.servlet.context-path) 解析上下文路径
			 */
			String contextPath = env.getProperty("management.server.servlet.context-path");
			/**
			 * 从 PROPERTIES(management.server.address) 解析地址
			 */
			String address = env.getProperty("management.server.address");
			if (StringUtils.hasLength(contextPath)) {
				metadata.put(MANAGEMENT_CONTEXT_PATH, contextPath);
			}
			if (StringUtils.hasLength(address)) {
				metadata.put(MANAGEMENT_ADDRESS, address);
			}
		}

		/**
		 * 从 PROPERTIES(spring.cloud.nacos.discovery.heartBeatInterval) 解析心跳间隔
		 * 如果有值，则写入元信息
		 */
		if (null != nacosDiscoveryProperties.getHeartBeatInterval()) {
			metadata.put(PreservedMetadataKeys.HEART_BEAT_INTERVAL, nacosDiscoveryProperties.getHeartBeatInterval().toString());
		}
		/**
		 * 从 PROPERTIES(spring.cloud.nacos.discovery.heartBeatTimeout) 解析心跳超时
		 * 如果有值，则写入元信息
		 */
		if (null != nacosDiscoveryProperties.getHeartBeatTimeout()) {
			metadata.put(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, nacosDiscoveryProperties.getHeartBeatTimeout().toString());
		}
		/**
		 * 从 PROPERTIES(spring.cloud.nacos.discovery.ipDeleteTimeout) 解析ip删除超时
		 */
		if (null != nacosDiscoveryProperties.getIpDeleteTimeout()) {
			metadata.put(PreservedMetadataKeys.IP_DELETE_TIMEOUT, nacosDiscoveryProperties.getIpDeleteTimeout().toString());
		}
		/**
		 * 执行自定义的注册器
		 */
		customize(registrationCustomizers);
	}

	protected void customize(List<NacosRegistrationCustomizer> registrationCustomizers) {
		if (registrationCustomizers != null) {
			for (NacosRegistrationCustomizer customizer : registrationCustomizers) {
				customizer.customize(this);
			}
		}
	}

	@Override
	public String getServiceId() {
		return nacosDiscoveryProperties.getService();
	}

	@Override
	public String getHost() {
		return nacosDiscoveryProperties.getIp();
	}

	@Override
	public int getPort() {
		return nacosDiscoveryProperties.getPort();
	}

	public void setPort(int port) {
		this.nacosDiscoveryProperties.setPort(port);
	}

	@Override
	public boolean isSecure() {
		return nacosDiscoveryProperties.isSecure();
	}

	@Override
	public URI getUri() {
		return DefaultServiceInstance.getUri(this);
	}

	@Override
	public Map<String, String> getMetadata() {
		return nacosDiscoveryProperties.getMetadata();
	}

	public boolean isRegisterEnabled() {
		return nacosDiscoveryProperties.isRegisterEnabled();
	}

	public String getCluster() {
		return nacosDiscoveryProperties.getClusterName();
	}

	public float getRegisterWeight() {
		return nacosDiscoveryProperties.getWeight();
	}

	public NacosDiscoveryProperties getNacosDiscoveryProperties() {
		return nacosDiscoveryProperties;
	}

	@Override
	public String toString() {
		NacosDiscoveryProperties safeProp = new NacosDiscoveryProperties();
		BeanUtils.copyProperties(safeProp, nacosDiscoveryProperties);
		/**
		 * 对用户名和密码进行加密
		 */
		safeProp.setUsername("******");
		safeProp.setPassword("******");
		return "NacosRegistration{" + "nacosDiscoveryProperties=" + safeProp + '}';
	}

}
