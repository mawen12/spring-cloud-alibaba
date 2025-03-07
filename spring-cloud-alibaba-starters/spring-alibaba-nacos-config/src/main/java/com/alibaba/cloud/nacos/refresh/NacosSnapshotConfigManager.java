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

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nacos 快照配置管理器，使用内存保存快照配置
 *
 * @author: ruansheng
 * @date: 2024-01-22
 */
public final class NacosSnapshotConfigManager {

	private NacosSnapshotConfigManager() {
	}

	private static final Logger log = LoggerFactory.getLogger(NacosSnapshotConfigManager.class);

	/**
	 * 保存配置的快照信息
	 */
	private static final Map<String/* {dataId}@{group} */, String/* 配置字符串 */> CONFIG_INFO_SNAPSHOT_MAP = new ConcurrentHashMap<>(8);

	/**
	 * 可存储的最大快照总数
	 */
	private static final int MAX_SNAPSHOT_COUNT = 100;

	private static String formatConfigSnapshotKey(String dataId, String group) {
		return dataId + "@" + group;
	}

	/**
	 * 获取并移除配置
	 *
	 * @param dataId
	 * @param group
	 * @return
	 */
	public static String getAndRemoveConfigSnapshot(String dataId, String group) {
		// 获取快照的配置
		// TODO by mawen 是否可以简化为直接使用 remove
		String configInfo = CONFIG_INFO_SNAPSHOT_MAP.get(formatConfigSnapshotKey(dataId, group));
		// 移除快照配置
		removeConfigSnapshot(dataId, group);
		// 返回配置
		return configInfo;
	}

	/**
	 * 保存配置
	 *
	 * @param dataId
	 * @param group
	 * @param configInfo
	 */
	public static void putConfigSnapshot(String dataId, String group, String configInfo) {
		try {
			// 理论上，容量限制永远不会被触发，这部分代码作为额外的容错层
			if (CONFIG_INFO_SNAPSHOT_MAP.size() > MAX_SNAPSHOT_COUNT) {
				// 获取配置迭代器，并移除第一个，需要注意该值并非最早注册的
				Iterator<Map.Entry<String, String>> iterator = CONFIG_INFO_SNAPSHOT_MAP.entrySet().iterator();
				iterator.next();
				iterator.remove();
			}
			String snapshotKey = formatConfigSnapshotKey(dataId, group);
			if (configInfo == null) {
				// 移除不存在的配置
				CONFIG_INFO_SNAPSHOT_MAP.remove(snapshotKey);
			}
			else {
				// 放入配置
				CONFIG_INFO_SNAPSHOT_MAP.put(snapshotKey, configInfo);
			}
		}
		catch (Exception e) {
			log.warn("remove nacos config snapshot error", e);
		}
	}

	public static void removeConfigSnapshot(String dataId, String group) {
		CONFIG_INFO_SNAPSHOT_MAP.remove(formatConfigSnapshotKey(dataId, group));
	}

}
