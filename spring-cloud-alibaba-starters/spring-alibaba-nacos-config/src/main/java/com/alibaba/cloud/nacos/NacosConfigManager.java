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

package com.alibaba.cloud.nacos;

import java.util.Objects;
import java.util.Properties;

import com.alibaba.cloud.nacos.diagnostics.analyzer.NacosConnectionFailureException;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 单例设计模式
 * Nacos配置管理器，提供服务的相关操作
 * <p>
 * 需要注意的是，Nacos配置中心服务并未被注册为Bean
 *
 * @author zkzlx
 */
public class NacosConfigManager {

	private static final Logger log = LoggerFactory.getLogger(NacosConfigManager.class);

	/**
	 * Nacos配置中心服务
	 */
	private static ConfigService service;

	/**
	 * Nacos配置管理器实例
	 */
	private static NacosConfigManager INSTANCE;

	/**
	 * 构造{@link ConfigService}的属性信息
	 */
	private NacosConfigProperties nacosConfigProperties;

	public NacosConfigManager(NacosConfigProperties nacosConfigProperties) {
		this.nacosConfigProperties = nacosConfigProperties;
	}

	public static NacosConfigManager getInstance() {
		return INSTANCE;
	}

	public static NacosConfigManager getInstance(NacosConfigProperties properties) {
		if (INSTANCE != null) {
			return INSTANCE;
		}
		/**
		 * 线程安全的初始化
		 */
		synchronized (NacosConfigManager.class) {
			if (INSTANCE == null) {
				INSTANCE = new NacosConfigManager(properties);
				INSTANCE.createConfigService(properties);
			}
		}
		return INSTANCE;
	}

	/**
	 * Compatible with old design,It will be perfected in the future.
	 */
	private ConfigService createConfigService(NacosConfigProperties nacosConfigProperties) {
		try {
			/**
			 * 检查确保返回已创建的配置中心服务
			 */
			if (Objects.isNull(service)) {
				/**
				 * 使用{@link NacosFactory#createConfigService(Properties)}来创建配置中心服务
				 */
				service = NacosFactory.createConfigService(nacosConfigProperties.assembleConfigServiceProperties());
			}
		}
		catch (NacosException e) {
			log.error(e.getMessage());
			throw new NacosConnectionFailureException(nacosConfigProperties.getServerAddr(), e.getMessage(), e);
		}
		return service;
	}

	public ConfigService getConfigService() {
		if (Objects.isNull(service)) {
			createConfigService(this.nacosConfigProperties);
		}
		return service;
	}

	public NacosConfigProperties getNacosConfigProperties() {
		return nacosConfigProperties;
	}

}
