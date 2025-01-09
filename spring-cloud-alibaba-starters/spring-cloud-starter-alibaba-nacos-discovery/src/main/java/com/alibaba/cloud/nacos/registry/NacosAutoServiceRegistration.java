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

import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.cloud.nacos.event.NacosDiscoveryInfoChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.client.serviceregistry.AbstractAutoServiceRegistration;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.util.Assert;

/**
 * Nacos自动服务注册，基于Spring Cloud Common{@link AbstractAutoServiceRegistration}的特定实现。
 * 仅在PROPERTIES(spring.cloud.nacos.discovery.registerEnabled)=true时，才会注册
 *
 * @author xiaojing
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 */
public class NacosAutoServiceRegistration
		extends AbstractAutoServiceRegistration<Registration> {

	private static final Logger log = LoggerFactory.getLogger(NacosAutoServiceRegistration.class);

	/**
	 * 保存待注册实例的信息
	 */
	private NacosRegistration registration;

	public NacosAutoServiceRegistration(ServiceRegistry<Registration> serviceRegistry,
			AutoServiceRegistrationProperties autoServiceRegistrationProperties,
			NacosRegistration registration) {
		super(serviceRegistry, autoServiceRegistrationProperties);
		this.registration = registration;
	}

	@Deprecated
	public void setPort(int port) {
		getPort().set(port);
	}

	@Override
	protected NacosRegistration getRegistration() {
		/**
		 * 如果当前待注册的实例未指定端口，且自动服务注册制定了，则采用自定服务的端口
		 */
		if (this.registration.getPort() < 0 && this.getPort().get() > 0) {
			this.registration.setPort(this.getPort().get());
		}
		Assert.isTrue(this.registration.getPort() > 0, "service.port has not been set");
		return this.registration;
	}

	@Override
	protected NacosRegistration getManagementRegistration() {
		return null;
	}

	/**
	 * 进行服务实例注册，使用{@link ServiceRegistry#register(Registration)}方法来注册，
	 * 且仅在PROPERTIES(spring.cloud.nacos.discovery.registerEnabled)=true时进行注册
	 */
	@Override
	protected void register() {
		/**
		 * 如果PROPERTIES(spring.cloud.nacos.discovery.registerEnabled)=false，则代表不进行注册
		 */
		if (!this.registration.getNacosDiscoveryProperties().isRegisterEnabled()) {
			log.debug("Registration disabled.");
			return;
		}
		/**
		 * 检查端口
		 */
		if (this.registration.getPort() < 0) {
			this.registration.setPort(getPort().get());
		}
		/**
		 * 使用{@link ServiceRegistry#register(Registration)}进行实例注册
		 */
		super.register();
	}

	@Override
	protected void registerManagement() {
		if (!this.registration.getNacosDiscoveryProperties().isRegisterEnabled()) {
			return;
		}
		super.registerManagement();

	}

	@Override
	protected Object getConfiguration() {
		return this.registration.getNacosDiscoveryProperties();
	}

	@Override
	protected boolean isEnabled() {
		return this.registration.getNacosDiscoveryProperties().isRegisterEnabled();
	}

	@Override
	@SuppressWarnings("deprecation")
	protected String getAppName() {
		String appName = registration.getNacosDiscoveryProperties().getService();
		return StringUtils.isEmpty(appName) ? super.getAppName() : appName;
	}

	@EventListener
	public void onNacosDiscoveryInfoChangedEvent(NacosDiscoveryInfoChangedEvent event) {
		restart();
	}

	private void restart() {
		this.stop();
		this.start();
	}

}
