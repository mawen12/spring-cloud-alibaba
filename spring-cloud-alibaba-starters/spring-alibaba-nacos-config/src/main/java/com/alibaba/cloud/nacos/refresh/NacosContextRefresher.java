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

package com.alibaba.cloud.nacos.refresh;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.cloud.nacos.NacosPropertySourceRepository;
import com.alibaba.cloud.nacos.client.NacosPropertySource;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractSharedListener;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;

/**
 * 在应用启动后，该类将注册监听器到所有应用级的dataId，当数据发生变更时，监听器将刷新配置
 *
 * @see com.alibaba.cloud.nacos.annotation.NacosConfigListener
 * @see com.alibaba.cloud.nacos.annotation.NacosConfigKeysListener
 * @see com.alibaba.cloud.nacos.annotation.NacosConfigRefreshableListener
 *
 * @author juven.xuxb
 * @author pbting
 * @author freeman
 */
public class NacosContextRefresher implements ApplicationListener<ApplicationReadyEvent>, ApplicationContextAware {

	private final static Logger log = LoggerFactory.getLogger(NacosContextRefresher.class);

	/**
	 * 配置刷新总次数，当为0时，代表还未收到过刷新。一种是配置没有发生过变更，另一种是本地还没启动好，还没注册对应的监听器
	 */
	private static final AtomicLong REFRESH_COUNT = new AtomicLong(0);

	private final boolean isRefreshEnabled;
	private final NacosRefreshHistory nacosRefreshHistory;
	private NacosConfigProperties nacosConfigProperties;
	private ConfigService configService;

	private NacosConfigManager configManager;

	private ApplicationContext applicationContext;

	/**
	 * 应用是否启动标识，在首次收到{@link ApplicationReadyEvent}事件后，更新状态
	 */
	private AtomicBoolean ready = new AtomicBoolean(false);

	/**
	 * 监听Nacos配置的监听器
	 */
	private Map<String, Listener> listenerMap = new ConcurrentHashMap<>(16);

	public NacosContextRefresher(NacosConfigManager nacosConfigManager, NacosRefreshHistory refreshHistory) {
		this.configManager = nacosConfigManager;
		this.nacosConfigProperties = nacosConfigManager.getNacosConfigProperties();
		this.nacosRefreshHistory = refreshHistory;
		this.isRefreshEnabled = this.nacosConfigProperties.isRefreshEnabled();
	}

	public static long getRefreshCount() {
		return REFRESH_COUNT.get();
	}

	public static void refreshCountIncrement() {
		REFRESH_COUNT.incrementAndGet();
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		// 可能存在多个Spring上下文启动，但仅处理一个
		if (this.ready.compareAndSet(false, true)) {
			// 在应用启动后，注册Nacos监听器
			this.registerNacosListenersForApplications();
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	/**
	 * register Nacos Listeners.
	 */
	private void registerNacosListenersForApplications() {
		if (isRefreshEnabled()) {
			for (NacosPropertySource propertySource : NacosPropertySourceRepository.getAll()) {
				// 获取
				if (!propertySource.isRefreshable()) {
					continue;
				}
				String dataId = propertySource.getDataId();
				registerNacosListener(propertySource.getGroup(), dataId);
			}
		}
	}

	private void registerNacosListener(final String groupKey, final String dataKey) {
		String key = NacosPropertySourceRepository.getMapKey(dataKey, groupKey);
		Listener listener = listenerMap.computeIfAbsent(key,
				lst -> new AbstractSharedListener() {
					@Override
					public void innerReceive(String dataId, String group, String configInfo) {

						log.info("[Nacos Config] Receive Nacos config change: dataId={}, group={}", dataKey, groupKey);
						// 增加刷新次数
						refreshCountIncrement();
						// 写入刷新历史
						nacosRefreshHistory.addRefreshRecord(dataId, group, configInfo);
						//
						NacosSnapshotConfigManager.putConfigSnapshot(dataId, group, configInfo);
						NacosConfigRefreshEvent event = new NacosConfigRefreshEvent(this, null, "Refresh Nacos config");
						event.setDataId(dataId);
						event.setGroup(group);
						applicationContext.publishEvent(event);
						if (log.isDebugEnabled()) {
							log.debug(String.format("Publish Nacos config Refresh Event group=%s,dataId=%s,configInfo=%s", group, dataId, configInfo));
						}
					}
				});
		try {
			if (configService == null && configManager != null) {
				configService = configManager.getConfigService();
			}
			configService.addListener(dataKey, groupKey, listener);
			log.info("[Nacos Config] Listening config: dataId={}, group={}", dataKey, groupKey);
		}
		catch (NacosException e) {
			log.warn(String.format("register fail for nacos listener ,dataId=[%s],group=[%s]", dataKey, groupKey), e);
		}
	}

	public NacosConfigProperties getNacosConfigProperties() {
		return nacosConfigProperties;
	}

	public NacosContextRefresher setNacosConfigProperties(NacosConfigProperties nacosConfigProperties) {
		this.nacosConfigProperties = nacosConfigProperties;
		return this;
	}

	public boolean isRefreshEnabled() {
		if (null == nacosConfigProperties) {
			return isRefreshEnabled;
		}
		// Compatible with older configurations
		if (nacosConfigProperties.isRefreshEnabled() && !isRefreshEnabled) {
			return false;
		}
		return isRefreshEnabled;
	}

}
