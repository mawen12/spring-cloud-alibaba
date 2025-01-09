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

import java.net.URI;
import java.util.Map;
import java.util.Objects;

import com.alibaba.nacos.api.naming.pojo.Instance;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;

/**
 * 基于Nacos的服务实例，这是基于Spring Cloud Common的{@link ServiceInstance}的特定实现
 *
 * @author xiaojing
 * @author changjin wei(魏昌进)
 */
public class NacosServiceInstance implements ServiceInstance {

	/**
	 * 实例所属的服务Id，对应到Nacos就是{@link com.alibaba.nacos.api.naming.pojo.Service#name}
	 */
	private String serviceId;

	/**
	 * 实例Id，对应到Nacos就是{@link Instance#instanceId}
	 */
	private String instanceId;

	/**
	 * 实例ip，对应到Nacos就是{@link Instance#ip}
	 */
	private String host;

	/**
	 * 实例端口，对应到Nacos就是{@link Instance#port}
	 */
	private int port;

	/**
	 * 是否安全
	 */
	private boolean secure;

	/**
	 * 实例的元信息，此处不仅包含{@link Instance#metadata}，还额外包含以下信息：
	 * <ul>
	 *     <li>nacos.instanceId</li>
	 *     <li>nacos.weight</li>
	 *     <li>nacos.healthy</li>
	 *     <li>nacos.cluster</li>
	 *     <li>nacos.ephemeral</li>
	 *     <li>secure</li>
	 * </ul>
	 * @see com.alibaba.cloud.nacos.discovery.NacosServiceDiscovery#hostToServiceInstance(Instance, String)
	 */
	private Map<String, String> metadata;

	@Override
	public String getServiceId() {
		return serviceId;
	}

	@Override
	public String getInstanceId() {
		return instanceId;
	}

	@Override
	public String getHost() {
		return host;
	}

	@Override
	public int getPort() {
		return port;
	}

	@Override
	public boolean isSecure() {
		return secure;
	}

	@Override
	public URI getUri() {
		return DefaultServiceInstance.getUri(this);
	}

	@Override
	public Map<String, String> getMetadata() {
		return metadata;
	}

	@Override
	public String getScheme() {
		return getUri().getScheme();
	}

	public void setServiceId(String serviceId) {
		this.serviceId = serviceId;
	}

	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	public void setMetadata(Map<String, String> metadata) {
		this.metadata = metadata;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		NacosServiceInstance that = (NacosServiceInstance) o;

		return Objects.equals(this.serviceId, that.serviceId)
				&& Objects.equals(this.instanceId, that.instanceId)
				&& Objects.equals(this.host, that.host)
				&& this.port == that.port
				&& this.secure == that.secure
				&& Objects.equals(this.metadata, that.metadata);
	}

	@Override
	public int hashCode() {
		return (instanceId == null) ? 31 : (instanceId.hashCode() + 31);
	}

}
