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

package com.alibaba.cloud.nacos.configdata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.cloud.nacos.NacosPropertiesPrefixer;
import com.alibaba.cloud.nacos.NacosPropertySourceRepository;
import com.alibaba.cloud.nacos.client.NacosPropertySource;
import com.alibaba.cloud.nacos.parser.NacosDataParserHandler;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import org.apache.commons.logging.Log;

import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoader;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.PropertySource;

import static com.alibaba.cloud.nacos.configdata.ConfigPreference.LOCAL;
import static com.alibaba.cloud.nacos.configdata.ConfigPreference.REMOTE;
import static com.alibaba.cloud.nacos.configdata.NacosConfigDataResource.NacosItemConfig;
import static org.springframework.boot.context.config.ConfigData.Option;

/**
 * 从{@link NacosConfigDataResource}读取配置数据的{@link ConfigDataLoader}实现
 *
 * <p>遵循{@link ConfigDataLoader}的统一规范，将配置在{@code META-INF/spring.factories}
 *
 * @author freeman
 * @since 2021.0.1.0
 */
public class NacosConfigDataLoader implements ConfigDataLoader<NacosConfigDataResource> {

	private final Log log;

	public NacosConfigDataLoader(DeferredLogFactory logFactory) {
		this.log = logFactory.getLog(getClass());
	}

	@Override
	public ConfigData load(ConfigDataLoaderContext context, NacosConfigDataResource resource) {
		return doLoad(context, resource);
	}

	public ConfigData doLoad(ConfigDataLoaderContext context, NacosConfigDataResource resource) {
		try {
			// 获取配置服务
			ConfigService configService = getBean(context, NacosConfigManager.class).getConfigService();
			// 获取Nacos配置中心属性
			NacosConfigProperties properties = getBean(context, NacosConfigProperties.class);
			// 读取配置项
			NacosItemConfig config = resource.getConfig();
			// 从Nacos注册中心拉取配置，并转换为属性源
			List<PropertySource<?>> propertySources = pullConfig(configService, config.getGroup(), config.getDataId(), config.getSuffix(), properties.getTimeout());

			NacosPropertySource propertySource = new NacosPropertySource(propertySources, config.getGroup(), config.getDataId(), new Date(), config.isRefreshEnabled());

			NacosPropertySourceRepository.collectNacosPropertySource(propertySource);

			return new ConfigData(propertySources, getOptions(context, resource));
		}
		catch (Exception e) {
			log.error("Error getting properties from nacos: " + resource, e);
			if (!resource.isOptional()) {
				throw new ConfigDataResourceNotFoundException(resource, e);
			}
		}
		return null;
	}

	private Option[] getOptions(ConfigDataLoaderContext context, NacosConfigDataResource resource) {
		List<Option> options = new ArrayList<>();
		options.add(Option.IGNORE_IMPORTS);
		options.add(Option.IGNORE_PROFILES);
		if (getPreference(context, resource) == REMOTE) {
			// mark it as 'PROFILE_SPECIFIC' config, it has higher priority,
			// will override the none profile specific config.
			// fixed https://github.com/alibaba/spring-cloud-alibaba/issues/2455
			options.add(Option.PROFILE_SPECIFIC);
		}
		return options.toArray(new Option[0]);
	}

	private ConfigPreference getPreference(ConfigDataLoaderContext context, NacosConfigDataResource resource) {
		Binder binder = context.getBootstrapContext().get(Binder.class);
		String prefix = NacosPropertiesPrefixer.getPrefix(binder);


		ConfigPreference preference = binder.bind(prefix + ".config.preference", ConfigPreference.class).orElse(LOCAL);
		String specificPreference = resource.getConfig().getPreference();
		if (specificPreference != null) {
			try {
				preference = ConfigPreference.valueOf(specificPreference.toUpperCase());
			}
			catch (IllegalArgumentException ignore) {
				// illegal preference value, just ignore.
				log.error(String.format(
						"illegal preference value: %s, using default preference: %s",
						specificPreference, preference));
			}
		}
		return preference;
	}

	private List<PropertySource<?>> pullConfig(ConfigService configService, String group, String dataId, String suffix, long timeout) throws NacosException, IOException {
		// 从Nacos Server读取指定配置
		String config = configService.getConfig(dataId, group, timeout);
		// 写入日志
		logLoadInfo(group, dataId, config);
		// fixed issue: https://github.com/alibaba/spring-cloud-alibaba/issues/2906 .
		String configName = group + "@" + dataId;
		// 解析配置，从String -> List<PropertySource>
		return NacosDataParserHandler.getInstance().parseNacosData(configName, config, suffix);
	}

	private void logLoadInfo(String group, String dataId, String config) {
		if (config != null) {
			log.info(String.format("[Nacos Config] Load config[dataId=%s, group=%s] success", dataId, group));
		}
		else {
			log.warn(String.format("[Nacos Config] config[dataId=%s, group=%s] is empty", dataId, group));
		}
		if (log.isDebugEnabled()) {
			log.debug(String.format("[Nacos Config] config[dataId=%s, group=%s] content: \n%s", dataId, group, config));
		}
	}

	protected <T> T getBean(ConfigDataLoaderContext context, Class<T> type) {
		if (context.getBootstrapContext().isRegistered(type)) {
			return context.getBootstrapContext().get(type);
		}
		return null;
	}

}
