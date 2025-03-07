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

package com.alibaba.cloud.nacos.client;

import java.util.List;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.cloud.nacos.NacosPropertySourceRepository;
import com.alibaba.cloud.nacos.parser.NacosDataParserHandler;
import com.alibaba.cloud.nacos.refresh.NacosContextRefresher;
import com.alibaba.nacos.api.config.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * 基于Nacos的属性源加载器
 *
 * @author xiaojing
 * @author pbting
 */
@Order(0)
public class NacosPropertySourceLocator implements PropertySourceLocator {

	private static final Logger log = LoggerFactory.getLogger(NacosPropertySourceLocator.class);

	private static final String NACOS_PROPERTY_SOURCE_NAME = "NACOS";

	private static final String SEP1 = "-";

	private static final String DOT = ".";

	/**
	 * Nacos属性源构造器
	 */
	private NacosPropertySourceBuilder nacosPropertySourceBuilder;

	/**
	 * Nacos配置属性
	 */
	private NacosConfigProperties nacosConfigProperties;

	/**
	 * Nacos配置管理器
	 */
	private NacosConfigManager nacosConfigManager;

	/**
	 * recommend to use
	 * {@link NacosPropertySourceLocator#NacosPropertySourceLocator(com.alibaba.cloud.nacos.NacosConfigManager)}.
	 * @param nacosConfigProperties nacosConfigProperties
	 */
	@Deprecated
	public NacosPropertySourceLocator(NacosConfigProperties nacosConfigProperties) {
		this.nacosConfigProperties = nacosConfigProperties;
	}

	public NacosPropertySourceLocator(NacosConfigManager nacosConfigManager) {
		this.nacosConfigManager = nacosConfigManager;
		this.nacosConfigProperties = nacosConfigManager.getNacosConfigProperties();
	}

	@Override
	public PropertySource<?> locate(Environment env) {
		// 获取Nacos服务
		ConfigService configService = nacosConfigManager.getConfigService();

		if (null == configService) {
			// Nacos配置服务未发现，直接返回
			log.warn("no instance of config service found, can't load config from nacos");
			return null;
		}
		// 获取请求超时时间
		long timeout = nacosConfigProperties.getTimeout();
		// 构造Nacos属性源构造器
		nacosPropertySourceBuilder = new NacosPropertySourceBuilder(configService, timeout);
		// 获取属性名称dataId，即${prefix}${name}${fileExtension}
		String name = nacosConfigProperties.getName();
		// 获取dataId的前缀
		String dataIdPrefix = nacosConfigProperties.getPrefix();
		if (StringUtils.isEmpty(dataIdPrefix)) {
			// 未指定前缀时，就以该dataId作为前缀
			dataIdPrefix = name;
		}

		if (StringUtils.isEmpty(dataIdPrefix)) {
			// 未指定prefix, name, fileExtension时，取ENV(spring.application.name)
			dataIdPrefix = env.getProperty("spring.application.name");
		}

		// 构造服务属性源，名称为NACOS
		CompositePropertySource composite = new CompositePropertySource(NACOS_PROPERTY_SOURCE_NAME);

		//
		loadApplicationConfiguration(composite, dataIdPrefix, nacosConfigProperties, env);
		return composite;
	}

	/**
	 * load configuration of application.
	 */
	private void loadApplicationConfiguration(CompositePropertySource compositePropertySource, String dataIdPrefix, NacosConfigProperties properties, Environment environment) {
		// 读取文件扩展，目前仅支持Properties和Yaml
		String fileExtension = properties.getFileExtension();
		// 获取配置分组
		String nacosGroup = properties.getGroup();
		// load directly once by default
		loadNacosDataIfPresent(compositePropertySource, dataIdPrefix, nacosGroup, fileExtension, true);
		// load with suffix, which have a higher priority than the default
		loadNacosDataIfPresent(compositePropertySource,
				dataIdPrefix + DOT + fileExtension, nacosGroup, fileExtension, true);
		// Loaded with profile, which have a higher priority than the suffix
		for (String profile : environment.getActiveProfiles()) {
			String dataId = dataIdPrefix + SEP1 + profile + DOT + fileExtension;
			loadNacosDataIfPresent(compositePropertySource, dataId, nacosGroup,
					fileExtension, true);
		}

	}

	private void loadNacosConfiguration(final CompositePropertySource composite,
			List<NacosConfigProperties.Config> configs) {
		for (NacosConfigProperties.Config config : configs) {
			loadNacosDataIfPresent(composite, config.getDataId(), config.getGroup(),
					NacosDataParserHandler.getInstance()
							.getFileExtension(config.getDataId()),
					config.isRefresh());
		}
	}

	private void checkConfiguration(List<NacosConfigProperties.Config> configs,
			String tips) {
		for (int i = 0; i < configs.size(); i++) {
			String dataId = configs.get(i).getDataId();
			if (dataId == null || dataId.trim().length() == 0) {
				throw new IllegalStateException(String.format(
						"the [ spring.cloud.nacos.config.%s[%s] ] must give a dataId",
						tips, i));
			}
		}
	}

	private void loadNacosDataIfPresent(final CompositePropertySource composite, final String dataId, final String group, String fileExtension, boolean isRefreshable) {
		// dataId 未指定，直接返回
		if (null == dataId || dataId.trim().length() < 1) {
			return;
		}
		// group未指定，直接返回
		if (null == group || group.trim().length() < 1) {
			return;
		}
		// 基于Nacos的属性源
		NacosPropertySource propertySource = this.loadNacosPropertySource(dataId, group, fileExtension, isRefreshable);
		this.addFirstPropertySource(composite, propertySource, false);
	}

	private NacosPropertySource loadNacosPropertySource(final String dataId, final String group, String fileExtension, boolean isRefreshable) {
		// 如果还没刷新过，一种是配置没有发生变更，一种是本地还没启动好，未注册对应的监听器
		if (NacosContextRefresher.getRefreshCount() != 0) {
			if (!isRefreshable) {
				// 对于不允许刷新的配置，从仓库中获取该属性
				return NacosPropertySourceRepository.getNacosPropertySource(dataId, group);
			}
		}
		return nacosPropertySourceBuilder.build(dataId, group, fileExtension, isRefreshable);
	}

	/**
	 * Add the nacos configuration to the first place and maybe ignore the empty
	 * configuration.
	 */
	private void addFirstPropertySource(final CompositePropertySource composite,
			NacosPropertySource nacosPropertySource, boolean ignoreEmpty) {
		if (null == nacosPropertySource || null == composite) {
			return;
		}
		if (ignoreEmpty && nacosPropertySource.getSource().isEmpty()) {
			return;
		}
		composite.addFirstPropertySource(nacosPropertySource);
	}

	public void setNacosConfigManager(NacosConfigManager nacosConfigManager) {
		this.nacosConfigManager = nacosConfigManager;
	}

}
