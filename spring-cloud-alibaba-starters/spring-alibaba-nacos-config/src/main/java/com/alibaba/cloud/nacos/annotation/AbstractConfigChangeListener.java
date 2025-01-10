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

package com.alibaba.cloud.nacos.annotation;

import java.util.Map;

import com.alibaba.nacos.api.config.ConfigChangeEvent;
import com.alibaba.nacos.api.config.ConfigChangeItem;
import com.alibaba.nacos.api.config.listener.AbstractSharedListener;
import com.alibaba.nacos.client.config.impl.ConfigChangeHandler;

/**
 * 配置变更监听器抽象类
 */
public abstract class AbstractConfigChangeListener extends AbstractSharedListener implements TargetRefreshable {

	/**
	 * 上一次配置的内容
	 */
	String lastContent;

	/**
	 * 目标
	 */
	Object target;

	@Override
	public Object getTarget() {
		return target;
	}

	@Override
	public void setTarget(Object target) {
		this.target = target;
	}

	public AbstractConfigChangeListener(Object target) {
		this.target = target;
	}

	protected void setLastContent(String lastContent) {
		this.lastContent = lastContent;
	}

	@Override
	public void innerReceive(String dataId, String group, String configInfo) {

		// Map<ADDED/MODIFIED/DELETED, 配置变更元素>
		Map<String, ConfigChangeItem> data = null;
		try {
			/**
			 * 将获取到的配置内容与上一次配置内容进行比对，并返回发生变化的配置内容
			 */
			data = ConfigChangeHandler.getInstance().parseChangeData(lastContent, configInfo, type(dataId));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		/**
		 * 构造配置变更时间
		 */
		ConfigChangeEvent event = new ConfigChangeEvent(data);
		/**
		 *
		 */
		receiveConfigChange(event);
		/**
		 * 更新配置内容
		 */
		lastContent = configInfo;
	}

	/**
	 * 根据dataId确认配置内容格式，其遵循Spring配置风格，仅支持YAML和PROPERTIES
	 *
	 * @see com.alibaba.cloud.nacos.NacosConfigProperties#fileExtension
	 * @param dataId
	 * @return
	 */
	private String type(String dataId) {
		/**
		 * dataId的后缀就是配置内容格式
		 */
		if (dataId.endsWith(".yml") || dataId.endsWith(".yaml")) {
			return "yaml";
		}
		return "properties";
	}

	abstract void receiveConfigChange(ConfigChangeEvent event);
}
