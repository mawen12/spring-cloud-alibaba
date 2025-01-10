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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.alibaba.cloud.nacos.utils.PropertySourcesUtils;
import com.alibaba.cloud.nacos.utils.StringUtils;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import static com.alibaba.nacos.api.PropertyKeyConst.ACCESS_KEY;
import static com.alibaba.nacos.api.PropertyKeyConst.CLUSTER_NAME;
import static com.alibaba.nacos.api.PropertyKeyConst.CONFIG_LONG_POLL_TIMEOUT;
import static com.alibaba.nacos.api.PropertyKeyConst.CONFIG_RETRY_TIME;
import static com.alibaba.nacos.api.PropertyKeyConst.ENABLE_REMOTE_SYNC_CONFIG;
import static com.alibaba.nacos.api.PropertyKeyConst.ENCODE;
import static com.alibaba.nacos.api.PropertyKeyConst.ENDPOINT;
import static com.alibaba.nacos.api.PropertyKeyConst.ENDPOINT_PORT;
import static com.alibaba.nacos.api.PropertyKeyConst.MAX_RETRY;
import static com.alibaba.nacos.api.PropertyKeyConst.NAMESPACE;
import static com.alibaba.nacos.api.PropertyKeyConst.PASSWORD;
import static com.alibaba.nacos.api.PropertyKeyConst.RAM_ROLE_NAME;
import static com.alibaba.nacos.api.PropertyKeyConst.SECRET_KEY;
import static com.alibaba.nacos.api.PropertyKeyConst.SERVER_ADDR;
import static com.alibaba.nacos.api.PropertyKeyConst.USERNAME;

/**
 * Nacos配置中心相关属性
 *
 * @author leijuan
 * @author xiaojing
 * @author pbting
 * @author <a href="mailto:lyuzb@lyuzb.com">lyuzb</a>
 */
public class NacosConfigProperties {

	/**
	 * COMMAS , .
	 */
	public static final String COMMAS = ",";

	/**
	 * SEPARATOR , .
	 */
	public static final String SEPARATOR = "[,]";

	/**
	 * Nacos Server默认的命名空间，默认为public
	 */
	public static final String DEFAULT_NAMESPACE = "public";

	/**
	 * Nacos Server默认的地址，默认为127.0.0.1:8848，即本地启动
	 */
	public static final String DEFAULT_ADDRESS = "127.0.0.1:8848";

	private static final Pattern PATTERN = Pattern.compile("-(\\w)");

	private static final Logger log = LoggerFactory.getLogger(NacosConfigProperties.class);

	/**
	 * Spring的环境上下文
	 */
	@Autowired
	@JsonIgnore
	private Environment environment;
	/**
	 * Nacos配置中心的地址，
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.server-addr)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.server-addr)</li>
	 * </ul>
	 */
	private String serverAddr;
	/**
	 * Nacos登录用户名
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.username)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.username)</li>
	 * </ul>
	 */
	private String username;
	/**
	 * Nacos登录用户的密码
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.password)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.password)</li>
	 * </ul>
	 */
	private String password;
	/**
	 * Nacos配置内容的编码
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.encode)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.encode)</li>
	 * </ul>
	 */
	private String encode;
	/**
	 * Nacos配置所属的分组，默认为DEFAULT_GROUP
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.group) -> DEFAULT(DEFAULT_GROUP)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.group) -> DEFAULT(DEFAULT_GROUP)</li>
	 * </ul>
	 */
	private String group = "DEFAULT_GROUP";
	/**
	 * Nacos配置的dataId前缀
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.prefix)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.prefix)</li>
	 * </ul>
	 */
	private String prefix;
	/**
	 * Nacos配置的dataId后缀，该值还是文件内容格式，支持以下格式：
	 * <ul>
	 *     <li>properties</li>
	 *     <li>yaml</li>
	 *     <li>text</li>
	 *     <li>json</li>
	 *     <li>xml</li>
	 *     <li>html</li>
	 * </ul>
	 * <p>
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.file-extension) -> DEFAULT(properties)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.file-extension) -> DEFAULT(properties)</li>
	 * </ul>
	 *
	 * @see com.alibaba.nacos.config.server.enums.FileTypeEnum
	 */
	private String fileExtension = "properties";
	/**
	 * 从Nacos读取配置的超时时间，默认为3s
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.timeout) -> DEFAULT(3000)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.timeout) -> DEFAULT(3000)</li>
	 * </ul>
	 */
	private int timeout = 3000;
	/**
	 * Nacos最大容忍服务器断线重连错误次数
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.max-retry)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.max-retry)</li>
	 * </ul>
	 */
	private String maxRetry;
	/**
	 * Nacos读取配置的长轮询超时时间
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.config-long-poll-timeout)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.config-long-poll-timeout)</li>
	 * </ul>
	 */
	private String configLongPollTimeout;
	/**
	 * Nacos读取配置失败的重试时间
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.config-retry-time)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.config-retry-time)</li>
	 * </ul>
	 */
	private String configRetryTime;
	/**
	 * 是否注册监听器到Nacos上，当配置发生变更时，客户端是否及时得到通知，默认为false，代表不需要及时通知，
	 * 设置为true，带来网络负载。
	 * <p>
	 * 更好的方案是使用{@link ConfigService#getConfigAndSignListener(String, String, long, Listener)}，注册一个监听器
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.enable-remote-sync-config) -> DEFAULT(false)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.enable-remote-sync-config) -> DEFAULT(false)</li>
	 * </ul>
	 */
	private boolean enableRemoteSyncConfig = false;
	/**
	 * Nacos的端点，通过服务的域名可以找到动态的服务器地址，设置了该值，{@link #serverAddr}可以不再设置
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.endpoint)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.endpoint)</li>
	 * </ul>
	 */
	private String endpoint;
	/**
	 * Nacos配置所在的命名空间，用来区分不同环境的配置，默认为public
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.namespace) -> DEFAULT(public)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.namespace) -> DEFAULT(public)</li>
	 * </ul>
	 */
	private String namespace;
	/**
	 * Nacos命名空间的访问密钥
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.access-key)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.access-key)</li>
	 * </ul>
	 */
	private String accessKey;
	/**
	 * Nacos命名空间的密钥
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.secret-key)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.secret-key)</li>
	 * </ul>
	 */
	private String secretKey;
	/**
	 * 阿里云RAM的角色名称
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.ram-role-name)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.ram-role-name)</li>
	 * </ul>
	 */
	private String ramRoleName;
	/**
	 * Nacos配置中心的上下文路径，默认为nacos
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.context-path) -> DEFAULT(nacos)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.context-path) -> DEFAULT(nacos)</li>
	 * </ul>
	 */
	private String contextPath;
	/**
	 * Nacos配置所在的集群名称，默认为空
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.cluster-name)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.cluster-name)</li>
	 * </ul>
	 */
	private String clusterName;
	/**
	 * Nacos配置的dataId名称，其完整名称格式为${prefix}${name}${fileExtension}
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.name)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.name)</li>
	 * </ul>
	 */
	private String name;
	/**
	 * 一组共享的配置集合
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.shared-configs[0])</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.shared-configs[0])</li>
	 * </ul>
	 */
	private List<Config> sharedConfigs;
	/**
	 * 一组扩展配置的集合
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.extension-configs[0])</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.extension-configs[0])</li>
	 * </ul>
	 */
	private List<Config> extensionConfigs;
	/**
	 * 刷新配置的主开关，默认为true
	 * <ul>
	 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.refresh-enabled)</li>
	 *     <li>Spring：PROPERTIES(spring.nacos.config.refresh-enabled)</li>
	 * </ul>
	 */
	private boolean refreshEnabled = true;

	@PostConstruct
	public void init() {
		/**
		 * 覆盖PROPERTIES
		 */
		this.overrideFromEnv();
	}

	private void overrideFromEnv() {
		if (environment == null) {
			return;
		}

		/**
		 * 读取Nacos通用配置的前缀
		 */
		String prefix = NacosPropertiesPrefixer.getPrefix(environment);

		if (StringUtils.isEmpty(this.getServerAddr())) {
			/**
			 * 本机未设置Nacos的服务器地址，从 PROPERTIES(${prefix}.config.server-addr) -> PROPERTIES(${prefix}.server-addr:127.0.0.1:8848)
			 */
			String serverAddr = environment.resolvePlaceholders("${" + prefix + ".config.server-addr:}");
			if (StringUtils.isEmpty(serverAddr)) {
				serverAddr = environment.resolvePlaceholders("${" + prefix + ".server-addr:127.0.0.1:8848}");
			}
			this.setServerAddr(serverAddr);
		}
		if (StringUtils.isEmpty(this.getUsername())) {
			/**
			 * 解析Nacos服务器的用户名，从 PROPERTIES(${prefix}.username)
			 */
			this.setUsername(environment.resolvePlaceholders("${" + prefix + ".username:}"));
		}
		if (StringUtils.isEmpty(this.getPassword())) {
			/**
			 * 解析Nacos服务器的用户名，从 PROPERTIES(${prefix}.password)
			 */
			this.setPassword(environment.resolvePlaceholders("${" + prefix + ".password:}"));
		}
	}

	// todo sts support

	public String getServerAddr() {
		return serverAddr;
	}

	public void setServerAddr(String serverAddr) {
		this.serverAddr = serverAddr;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public String getFileExtension() {
		return fileExtension;
	}

	public void setFileExtension(String fileExtension) {
		this.fileExtension = fileExtension;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public String getMaxRetry() {
		return maxRetry;
	}

	public void setMaxRetry(String maxRetry) {
		this.maxRetry = maxRetry;
	}

	public String getConfigLongPollTimeout() {
		return configLongPollTimeout;
	}

	public void setConfigLongPollTimeout(String configLongPollTimeout) {
		this.configLongPollTimeout = configLongPollTimeout;
	}

	public String getConfigRetryTime() {
		return configRetryTime;
	}

	public void setConfigRetryTime(String configRetryTime) {
		this.configRetryTime = configRetryTime;
	}

	public Boolean getEnableRemoteSyncConfig() {
		return enableRemoteSyncConfig;
	}

	public void setEnableRemoteSyncConfig(Boolean enableRemoteSyncConfig) {
		this.enableRemoteSyncConfig = enableRemoteSyncConfig;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getAccessKey() {
		return accessKey;
	}

	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public String getRamRoleName() {
		return ramRoleName;
	}

	public void setRamRoleName(String ramRoleName) {
		this.ramRoleName = ramRoleName;
	}

	public String getEncode() {
		return encode;
	}

	public void setEncode(String encode) {
		this.encode = encode;
	}

	public String getContextPath() {
		return contextPath;
	}

	public void setContextPath(String contextPath) {
		this.contextPath = contextPath;
	}

	public String getClusterName() {
		return clusterName;
	}

	public void setClusterName(String clusterName) {
		this.clusterName = clusterName;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Environment getEnvironment() {
		return environment;
	}

	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	public List<Config> getSharedConfigs() {
		return sharedConfigs;
	}

	public void setSharedConfigs(List<Config> sharedConfigs) {
		this.sharedConfigs = sharedConfigs;
	}

	public List<Config> getExtensionConfigs() {
		return extensionConfigs;
	}

	public void setExtensionConfigs(List<Config> extensionConfigs) {
		this.extensionConfigs = extensionConfigs;
	}

	public boolean isRefreshEnabled() {
		return refreshEnabled;
	}

	public void setRefreshEnabled(boolean refreshEnabled) {
		this.refreshEnabled = refreshEnabled;
	}

	/**
	 * recommend to use {@link NacosConfigProperties#sharedConfigs} .
	 * @return string
	 */
	@Deprecated
	@DeprecatedConfigurationProperty(reason = "use spring.config.import instead")
	public String getSharedDataids() {
		return null == getSharedConfigs() ? null
				: getSharedConfigs().stream().map(Config::getDataId)
				.collect(Collectors.joining(COMMAS));
	}

	/**
	 * recommend to use {@link NacosConfigProperties#sharedConfigs} and not use it at the
	 * same time .
	 * @param sharedDataids the dataids for configurable multiple shared configurations ,
	 *     multiple separated by commas .
	 */
	@Deprecated
	public void setSharedDataids(String sharedDataids) {
		if (null != sharedDataids && sharedDataids.trim().length() > 0) {
			List<Config> list = new ArrayList<>();
			Stream.of(sharedDataids.split(SEPARATOR))
					.forEach(dataId -> list.add(new Config(dataId.trim())));
			this.compatibleSharedConfigs(list);
		}
	}

	/**
	 * Not providing support,the need to refresh is specified by the respective refresh
	 * configuration and not use it at the same time .
	 * @return string
	 */
	@Deprecated
	public String getRefreshableDataids() {
		return null == getSharedConfigs() ? null
				: getSharedConfigs().stream().filter(Config::isRefresh)
				.map(Config::getDataId).collect(Collectors.joining(COMMAS));
	}

	/**
	 * Not providing support,the need to refresh is specified by the respective refresh
	 * configuration and not use it at the same time .
	 * @param refreshableDataids refreshable dataids ,multiple separated by commas .
	 */
	@Deprecated
	public void setRefreshableDataids(String refreshableDataids) {
		if (null != refreshableDataids && refreshableDataids.trim().length() > 0) {
			List<Config> list = new ArrayList<>();
			Stream.of(refreshableDataids.split(SEPARATOR)).forEach(
					dataId -> list.add(new Config(dataId.trim()).setRefresh(true)));
			this.compatibleSharedConfigs(list);
		}
	}

	private void compatibleSharedConfigs(List<Config> configList) {
		if (null != this.getSharedConfigs()) {
			configList.addAll(this.getSharedConfigs());
		}
		List<Config> result = new ArrayList<>();
		configList.stream()
				.collect(Collectors.groupingBy(cfg -> (cfg.getGroup() + cfg.getDataId()),
						LinkedHashMap::new, Collectors.toList()))
				.forEach((key, list) -> {
					list.stream()
							.reduce((a, b) -> new Config(a.getDataId(), a.getGroup(),
									a.isRefresh() || (b != null && b.isRefresh())))
							.ifPresent(result::add);
				});
		this.setSharedConfigs(result);
	}

	/**
	 * recommend to use
	 * {@link com.alibaba.cloud.nacos.NacosConfigProperties#extensionConfigs} and not use
	 * it at the same time .
	 * @return extensionConfigs
	 */
	@Deprecated
	@DeprecatedConfigurationProperty(reason = "use spring.config.import instead")
	public List<Config> getExtConfig() {
		return this.getExtensionConfigs();
	}

	@Deprecated
	public void setExtConfig(List<Config> extConfig) {
		this.setExtensionConfigs(extConfig);
	}

	/**
	 * recommend to use {@link NacosConfigManager#getConfigService()}.
	 * @return ConfigService
	 */
	@Deprecated
	public ConfigService configServiceInstance() {
		// The following code will be migrated
		return NacosConfigManager.getInstance(this).getConfigService();
	}

	/**
	 * recommend to use {@link NacosConfigProperties#assembleConfigServiceProperties()}.
	 * @return ConfigServiceProperties
	 */
	@Deprecated
	public Properties getConfigServiceProperties() {
		return this.assembleConfigServiceProperties();
	}

	/**
	 * assemble properties for configService. (cause by rename : Remove the interference
	 * of auto prompts when writing,because autocue is based on get method.
	 * @return properties
	 */
	public Properties assembleConfigServiceProperties() {
		Properties properties = new Properties();
		properties.put(SERVER_ADDR, Objects.toString(this.serverAddr, ""));
		properties.put(USERNAME, Objects.toString(this.username, ""));
		properties.put(PASSWORD, Objects.toString(this.password, ""));
		properties.put(ENCODE, Objects.toString(this.encode, ""));
		properties.put(NAMESPACE, this.resolveNamespace());
		properties.put(ACCESS_KEY, Objects.toString(this.accessKey, ""));
		properties.put(SECRET_KEY, Objects.toString(this.secretKey, ""));
		properties.put(RAM_ROLE_NAME, Objects.toString(this.ramRoleName, ""));
		properties.put(CLUSTER_NAME, Objects.toString(this.clusterName, ""));
		properties.put(MAX_RETRY, Objects.toString(this.maxRetry, ""));
		properties.put(CONFIG_LONG_POLL_TIMEOUT,
				Objects.toString(this.configLongPollTimeout, ""));
		properties.put(CONFIG_RETRY_TIME, Objects.toString(this.configRetryTime, ""));
		properties.put(ENABLE_REMOTE_SYNC_CONFIG,
				Objects.toString(this.enableRemoteSyncConfig, ""));
		String endpoint = Objects.toString(this.endpoint, "");
		if (endpoint.contains(":")) {
			int index = endpoint.indexOf(":");
			properties.put(ENDPOINT, endpoint.substring(0, index));
			properties.put(ENDPOINT_PORT, endpoint.substring(index + 1));
		}
		else {
			properties.put(ENDPOINT, endpoint);
		}

		enrichNacosConfigProperties(properties);

		// set default value when serverAddr and endpoint is empty
		if (StringUtils.isEmpty(this.serverAddr) && StringUtils.isEmpty(this.endpoint)) {
			properties.put(SERVER_ADDR, DEFAULT_ADDRESS);
		}

		return properties;
	}

	/**
	 * refer
	 * https://github.com/alibaba/spring-cloud-alibaba/issues/2872
	 * https://github.com/alibaba/spring-cloud-alibaba/issues/2869 .
	 */
	private String resolveNamespace() {
		if (DEFAULT_NAMESPACE.equals(this.namespace)) {
			log.info("set nacos config namespace 'public' to ''");
			return "";
		}
		else {
			return Objects.toString(this.namespace, "");
		}
	}

	protected void enrichNacosConfigProperties(Properties nacosConfigProperties) {
		if (environment == null) {
			return;
		}
		String prefix = NacosPropertiesPrefixer.getPrefix(environment);

		Map<String, Object> properties = PropertySourcesUtils.getSubProperties((ConfigurableEnvironment) environment, prefix + ".config");
		properties.forEach((k, v) -> nacosConfigProperties.putIfAbsent(resolveKey(k), String.valueOf(v)));
	}

	protected String resolveKey(String key) {
		Matcher matcher = PATTERN.matcher(key);
		/**
		 * TODO by mawen 应该使用 StringBuilder
		 */
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(sb, matcher.group(1).toUpperCase());
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	@Override
	public String toString() {
		return "NacosConfigProperties{" + "serverAddr='" + serverAddr + '\''
				+ ", encode='" + encode + '\'' + ", group='" + group + '\'' + ", prefix='"
				+ prefix + '\'' + ", fileExtension='" + fileExtension + '\''
				+ ", timeout=" + timeout + ", maxRetry='" + maxRetry + '\''
				+ ", configLongPollTimeout='" + configLongPollTimeout + '\''
				+ ", configRetryTime='" + configRetryTime + '\''
				+ ", enableRemoteSyncConfig=" + enableRemoteSyncConfig + ", endpoint='"
				+ endpoint + '\'' + ", namespace='" + namespace + '\'' + ", accessKey='"
				+ accessKey + '\'' + ", secretKey='" + secretKey + '\''
				+ ", ramRoleName='" + ramRoleName + '\'' + ", contextPath='" + contextPath
				+ '\'' + ", clusterName='" + clusterName + '\'' + ", name='" + name + '\''
				+ '\'' + ", shares=" + sharedConfigs + ", extensions=" + extensionConfigs
				+ ", refreshEnabled=" + refreshEnabled + '}';
	}

	public static class Config {

		/**
		 * 扩展配置的dataId
		 * <ul>
		 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.shared-configs[0].dataId)</li>
		 *     <li>Spring：PROPERTIES(spring.nacos.config.shared-configs[0].dataId)</li>
		 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.extension-configs[0].dataId)</li>
		 *     <li>Spring：PROPERTIES(spring.nacos.config.extension-configs[0].dataId)</li>
		 * </ul>
		 */
		private String dataId;

		/**
		 * 扩展配置的分组，默认为DEFAULT_GROUP
		 * <ul>
		 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.shared-configs[0].group) -> DEFAULT(DEFAULT_GROUP)</li>
		 *     <li>Spring：PROPERTIES(spring.nacos.config.shared-configs[0].group) -> DEFAULT(DEFAULT_GROUP)</li>
		 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.extension-configs[0].group) -> DEFAULT(DEFAULT_GROUP)</li>
		 *     <li>Spring：PROPERTIES(spring.nacos.config.extension-configs[0].group) -> DEFAULT(DEFAULT_GROUP)</li>
		 * </ul>
		 */
		private String group = "DEFAULT_GROUP";

		/**
		 * 使用支持动态刷新，默认false，即不支持
		 * <ul>
		 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.shared-configs[0].refresh) -> DEFAULT(false)</li>
		 *     <li>Spring：PROPERTIES(spring.nacos.config.shared-configs[0].refresh) -> DEFAULT(false)</li>
		 *     <li>Spring Cloud：PROPERTIES(spring.cloud.nacos.config.extension-configs[0].refresh) -> DEFAULT(false)</li>
		 *     <li>Spring：PROPERTIES(spring.nacos.config.extension-configs[0].refresh) -> DEFAULT(false)</li>
		 * </ul>
		 */
		private boolean refresh = false;

		public Config() {
		}

		public Config(String dataId) {
			this.dataId = dataId;
		}

		public Config(String dataId, String group) {
			this(dataId);
			this.group = group;
		}

		public Config(String dataId, boolean refresh) {
			this(dataId);
			this.refresh = refresh;
		}

		public Config(String dataId, String group, boolean refresh) {
			this(dataId, group);
			this.refresh = refresh;
		}

		public String getDataId() {
			return dataId;
		}

		public Config setDataId(String dataId) {
			this.dataId = dataId;
			return this;
		}

		public String getGroup() {
			return group;
		}

		public Config setGroup(String group) {
			this.group = group;
			return this;
		}

		public boolean isRefresh() {
			return refresh;
		}

		public Config setRefresh(boolean refresh) {
			this.refresh = refresh;
			return this;
		}

		@Override
		public String toString() {
			return "Config{" + "dataId='" + dataId + '\'' + ", group='" + group + '\''
					+ ", refresh=" + refresh + '}';
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Config config = (Config) o;
			return refresh == config.refresh && Objects.equals(dataId, config.dataId)
					&& Objects.equals(group, config.group);
		}

		@Override
		public int hashCode() {
			return Objects.hash(dataId, group, refresh);
		}

	}

}
