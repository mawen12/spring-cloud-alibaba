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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.alibaba.cloud.commons.context.support.PropertySourcesUtils;
import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.cloud.nacos.event.NacosDiscoveryInfoChangedEvent;
import com.alibaba.cloud.nacos.util.InetIPv6Utils;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import static com.alibaba.nacos.api.PropertyKeyConst.ACCESS_KEY;
import static com.alibaba.nacos.api.PropertyKeyConst.ENDPOINT;
import static com.alibaba.nacos.api.PropertyKeyConst.ENDPOINT_PORT;
import static com.alibaba.nacos.api.PropertyKeyConst.NAMESPACE;
import static com.alibaba.nacos.api.PropertyKeyConst.NAMING_LOAD_CACHE_AT_START;
import static com.alibaba.nacos.api.PropertyKeyConst.PASSWORD;
import static com.alibaba.nacos.api.PropertyKeyConst.SECRET_KEY;
import static com.alibaba.nacos.api.PropertyKeyConst.SERVER_ADDR;
import static com.alibaba.nacos.api.PropertyKeyConst.USERNAME;

/**
 * Nacos服务发现属性，对应的属性前缀为{@code spring.cloud.nacos.discovery}
 *
 * @author dungu.zpf
 * @author xiaojing
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @author <a href="mailto:lyuzb@lyuzb.com">lyuzb</a>
 * @author <a href="mailto:78552423@qq.com">eshun</a>
 * @author freeman
 */
@ConfigurationProperties("spring.cloud.nacos.discovery")
public class NacosDiscoveryProperties {

	/**
	 * Prefix of {@link NacosDiscoveryProperties}.
	 */
	public static final String PREFIX = "spring.cloud.nacos.discovery";

	private static final Logger log = LoggerFactory.getLogger(NacosDiscoveryProperties.class);

	private static final Pattern PATTERN = Pattern.compile("-(\\w)");

	private static final String IPV4 = "IPv4";

	private static final String IPV6 = "IPv6";

	/**
	 * Nacos 注册中心的服务地址
	 */
	private String serverAddr;

	/**
	 * Nacos 认证用户名
	 */
	private String username;

	/**
	 * Nacos 认证密码
	 */
	private String password;

	/**
	 * 服务的域名，通过该域名可以动态获取服务地址
	 */
	private String endpoint;

	/**
	 * 命名空间，区分不同环境的注册中心，默认为public
	 */
	private String namespace;

	/**
	 * 观察延迟，从Nacos服务器拉取新服务所需要的时间，超过这个时间代表超时，默认30s
	 * 用于{@link com.alibaba.cloud.nacos.discovery.NacosDiscoveryHeartBeatPublisher}中调度任务的执行间隔
	 */
	private long watchDelay = 30000;

	/**
	 * Nacos 注册中心日志文件名称，文件默认位于{@code ${user.home}/nacos/logs/{logName}}
	 */
	private String logName;

	/**
	 * 注册到Nacos的服务名称，从 PROPERTIES(spring.cloud.nacos.discovery.service) -> DEFAULT(spring.application.name) -> DEFAULT(null)
	 */
	@Value("${spring.cloud.nacos.discovery.service:${spring.application.name:}}")
	private String service;

	/**
	 * 当前服务实例的权重，值越大，权重越大，默认为1
	 */
	private float weight = 1;

	/**
	 * 当前实例所在的集群，默认为DEFAULT
	 */
	private String clusterName;

	/**
	 * 当前实例所在的分组，默认为DEFAULT_GROUP
	 */
	private String group = "DEFAULT_GROUP";

	/**
	 * 启动时是否从本地缓存加载注册中心项，默认为false
	 */
	private String namingLoadCacheAtStart = "false";

	/**
	 * 额外需要注册的元信息
	 */
	private Map<String, String> metadata = new HashMap<>();

	/**
	 * 是否注册本地服务，如果只是想要订阅服务，而不像注册服务，需设置为false，默认为true
	 */
	private boolean registerEnabled = true;

	/**
	 * 注册到Nacos上的当前实例ip，如果没有设置，则会自动检测ip
	 *
	 */
	private String ip;

	/**
	 * 注册到Nacos上的当前实例的网口ip
	 */
	private String networkInterface = "";

	/**
	 * 注册实例所采用的ip类型，值有IPv4和IPv6，默认为IPv4。
	 * 当选择了IPv6，但是没有找到IPv6，则会自动切换为IPv4查找ip。
	 */
	private String ipType;

	/**
	 * 当前服务实例注册的端口，如果没有设置，则自动检测端口
	 */
	private int port = -1;

	/**
	 * 服务是否为https，默认为false，即http
	 */
	private boolean secure = false;

	/**
	 * 命名空间的access key
	 */
	private String accessKey;

	/**
	 * 命名空间的secret key
	 */
	private String secretKey;

	/**
	 * 心跳间隔，单位为ms
	 */
	private Integer heartBeatInterval;

	/**
	 * 心跳超时，单位为ms
	 */
	private Integer heartBeatTimeout;

	/**
	 * ip删除超时，单位ms
	 */
	private Integer ipDeleteTimeout;

	/**
	 * 注册的实例是否开始接受请求，默认为true
	 */
	private boolean instanceEnabled = true;

	/**
	 * 注册的实例是否为临时，默认为true
	 */
	private boolean ephemeral = true;

	/**
	 * 是否开启nacos失败容错，开启后，在发生异常时nacos会返回缓存数据
	 */
	private boolean failureToleranceEnabled;

	/**
	 * 注册失败时抛出异常，否则仅日志记录，默认为true
	 */
	private boolean failFast = true;

	/**
	 * 在设置{@link #ipType=IPv6}时，使用该类检测ipv6
	 */
	@Autowired
	private InetIPv6Utils inetIPv6Utils;

	/**
	 * 在设置{@link #ipType=IPv4}时，使用该类检测ip
	 */
	@Autowired
	private InetUtils inetUtils;

	/**
	 *
	 */
	@Autowired
	private Environment environment;

	/**
	 * Nacos服务管理器
	 */
	@Autowired
	private NacosServiceManager nacosServiceManager;

	@Autowired
	private ApplicationEventPublisher applicationEventPublisher;

	@PostConstruct
	public void init() throws Exception {

		metadata.put(PreservedMetadataKeys.REGISTER_SOURCE, "SPRING_CLOUD");
		if (secure) {
			metadata.put("secure", "true");
		}

		serverAddr = Objects.toString(serverAddr, "");
		if (serverAddr.endsWith("/")) {
			serverAddr = serverAddr.substring(0, serverAddr.length() - 1);
		}
		endpoint = Objects.toString(endpoint, "");
		namespace = Objects.toString(namespace, "");
		logName = Objects.toString(logName, "");

		if (StringUtils.isEmpty(ip)) {
			// traversing network interfaces if didn't specify an interface
			if (StringUtils.isEmpty(networkInterface)) {
				if (ipType == null) {
					ip = inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();
					String ipv6Addr = inetIPv6Utils.findIPv6Address();
					metadata.put(IPV6, ipv6Addr);
					if (ipv6Addr != null) {
						metadata.put(IPV6, ipv6Addr);
					}
				}
				else if (IPV4.equalsIgnoreCase(ipType)) {
					ip = inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();
				}
				else if (IPV6.equalsIgnoreCase(ipType)) {
					ip = inetIPv6Utils.findIPv6Address();
					if (StringUtils.isEmpty(ip)) {
						log.warn("There is no available IPv6 found. Spring Cloud Alibaba will automatically find IPv4.");
						ip = inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();
					}
				}
				else {
					throw new IllegalArgumentException(
							"please checking the type of IP " + ipType);
				}
			}
			else {
				NetworkInterface netInterface = NetworkInterface
						.getByName(networkInterface);
				if (null == netInterface) {
					throw new IllegalArgumentException(
							"no such interface " + networkInterface);
				}

				Enumeration<InetAddress> inetAddress = netInterface.getInetAddresses();
				while (inetAddress.hasMoreElements()) {
					InetAddress currentAddress = inetAddress.nextElement();
					if (currentAddress instanceof Inet4Address
							|| currentAddress instanceof Inet6Address
							&& !currentAddress.isLoopbackAddress()) {
						ip = currentAddress.getHostAddress();
						break;
					}
				}

				if (StringUtils.isEmpty(ip)) {
					throw new RuntimeException("cannot find available ip from"
							+ " network interface " + networkInterface);
				}

			}
		}

		this.overrideFromEnv(environment);
		if (nacosServiceManager.isNacosDiscoveryInfoChanged(this)) {
			applicationEventPublisher
					.publishEvent(new NacosDiscoveryInfoChangedEvent(this));
		}
		nacosServiceManager.setNacosDiscoveryProperties(this);
	}

	/**
	 * recommend to use {@link NacosServiceManager#getNamingService()}.
	 * @return NamingService
	 */
	@Deprecated
	public NamingService namingServiceInstance() {
		return nacosServiceManager.getNamingService();
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

	public String getLogName() {
		return logName;
	}

	public void setLogName(String logName) {
		this.logName = logName;
	}

	public void setInetUtils(InetUtils inetUtils) {
		this.inetUtils = inetUtils;
	}

	public float getWeight() {
		return weight;
	}

	public void setWeight(float weight) {
		this.weight = weight;
	}

	public String getClusterName() {
		return clusterName;
	}

	public void setClusterName(String clusterName) {
		this.clusterName = clusterName;
	}

	public String getService() {
		return service;
	}

	public void setService(String service) {
		this.service = service;
	}

	public boolean isRegisterEnabled() {
		return registerEnabled;
	}

	public void setRegisterEnabled(boolean registerEnabled) {
		this.registerEnabled = registerEnabled;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getIpType() {
		return ipType;
	}

	public void setIpType(String ipType) {
		this.ipType = ipType;
	}

	public String getNetworkInterface() {
		return networkInterface;
	}

	public void setNetworkInterface(String networkInterface) {
		this.networkInterface = networkInterface;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public boolean isSecure() {
		return secure;
	}

	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	public Map<String, String> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, String> metadata) {
		this.metadata = metadata;
	}

	public String getServerAddr() {
		return serverAddr;
	}

	public void setServerAddr(String serverAddr) {
		this.serverAddr = serverAddr;
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

	public Integer getHeartBeatInterval() {
		return heartBeatInterval;
	}

	public void setHeartBeatInterval(Integer heartBeatInterval) {
		this.heartBeatInterval = heartBeatInterval;
	}

	public Integer getHeartBeatTimeout() {
		return heartBeatTimeout;
	}

	public void setHeartBeatTimeout(Integer heartBeatTimeout) {
		this.heartBeatTimeout = heartBeatTimeout;
	}

	public Integer getIpDeleteTimeout() {
		return ipDeleteTimeout;
	}

	public void setIpDeleteTimeout(Integer ipDeleteTimeout) {
		this.ipDeleteTimeout = ipDeleteTimeout;
	}

	public String getNamingLoadCacheAtStart() {
		return namingLoadCacheAtStart;
	}

	public void setNamingLoadCacheAtStart(String namingLoadCacheAtStart) {
		this.namingLoadCacheAtStart = namingLoadCacheAtStart;
	}

	public long getWatchDelay() {
		return watchDelay;
	}

	public void setWatchDelay(long watchDelay) {
		this.watchDelay = watchDelay;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
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

	public boolean isInstanceEnabled() {
		return instanceEnabled;
	}

	public void setInstanceEnabled(boolean instanceEnabled) {
		this.instanceEnabled = instanceEnabled;
	}

	public boolean isEphemeral() {
		return ephemeral;
	}

	public void setEphemeral(boolean ephemeral) {
		this.ephemeral = ephemeral;
	}

	public boolean isFailureToleranceEnabled() {
		return failureToleranceEnabled;
	}

	public void setFailureToleranceEnabled(boolean failureToleranceEnabled) {
		this.failureToleranceEnabled = failureToleranceEnabled;
	}

	public boolean isFailFast() {
		return failFast;
	}

	public void setFailFast(boolean failFast) {
		this.failFast = failFast;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		NacosDiscoveryProperties that = (NacosDiscoveryProperties) o;
		return watchDelay == that.watchDelay && Float.compare(that.weight, weight) == 0
				&& registerEnabled == that.registerEnabled && port == that.port
				&& secure == that.secure && instanceEnabled == that.instanceEnabled
				&& ephemeral == that.ephemeral
				&& failureToleranceEnabled == that.failureToleranceEnabled
				&& Objects.equals(serverAddr, that.serverAddr)
				&& Objects.equals(username, that.username)
				&& Objects.equals(password, that.password)
				&& Objects.equals(endpoint, that.endpoint)
				&& Objects.equals(namespace, that.namespace)
				&& Objects.equals(logName, that.logName)
				&& Objects.equals(service, that.service)
				&& Objects.equals(clusterName, that.clusterName)
				&& Objects.equals(group, that.group)
				&& Objects.equals(namingLoadCacheAtStart, that.namingLoadCacheAtStart)
				&& Objects.equals(metadata, that.metadata) && Objects.equals(ip, that.ip)
				&& Objects.equals(networkInterface, that.networkInterface)
				&& Objects.equals(accessKey, that.accessKey)
				&& Objects.equals(secretKey, that.secretKey)
				&& Objects.equals(heartBeatInterval, that.heartBeatInterval)
				&& Objects.equals(heartBeatTimeout, that.heartBeatTimeout)
				&& Objects.equals(failFast, that.failFast)
				&& Objects.equals(ipDeleteTimeout, that.ipDeleteTimeout);
	}

	@Override
	public int hashCode() {
		return Objects.hash(serverAddr, username, password, endpoint, namespace,
				watchDelay, logName, service, weight, clusterName, group,
				namingLoadCacheAtStart, metadata, registerEnabled, ip, networkInterface,
				port, secure, accessKey, secretKey, heartBeatInterval, heartBeatTimeout,
				ipDeleteTimeout, instanceEnabled, ephemeral, failureToleranceEnabled,
				failFast);
	}

	@Override
	public String toString() {
		return "NacosDiscoveryProperties{" + "serverAddr='" + serverAddr + '\''
				+ ", username='" + username + '\'' + ", password='" + password + '\''
				+ ", endpoint='" + endpoint + '\'' + ", namespace='" + namespace + '\''
				+ ", watchDelay=" + watchDelay + ", logName='" + logName + '\''
				+ ", service='" + service + '\'' + ", weight=" + weight
				+ ", clusterName='" + clusterName + '\'' + ", group='" + group + '\''
				+ ", namingLoadCacheAtStart='" + namingLoadCacheAtStart + '\''
				+ ", metadata=" + metadata + ", registerEnabled=" + registerEnabled
				+ ", ip='" + ip + '\'' + ", networkInterface='" + networkInterface + '\''
				+ ", port=" + port + ", secure=" + secure + ", accessKey='" + accessKey
				+ '\'' + ", secretKey='" + secretKey + '\'' + ", heartBeatInterval="
				+ heartBeatInterval + ", heartBeatTimeout=" + heartBeatTimeout
				+ ", ipDeleteTimeout=" + ipDeleteTimeout + ", instanceEnabled="
				+ instanceEnabled + ", ephemeral=" + ephemeral
				+ ", failureToleranceEnabled=" + failureToleranceEnabled + '}'
				+ ", ipDeleteTimeout=" + ipDeleteTimeout + ", failFast=" + failFast + '}';
	}

	public void overrideFromEnv(Environment env) {

		if (StringUtils.isEmpty(this.getServerAddr())) {
			String serverAddr = env
					.resolvePlaceholders("${spring.cloud.nacos.discovery.server-addr:}");
			if (StringUtils.isEmpty(serverAddr)) {
				serverAddr = env.resolvePlaceholders(
						"${spring.cloud.nacos.server-addr:127.0.0.1:8848}");
			}
			this.setServerAddr(serverAddr);
		}
		if (StringUtils.isEmpty(this.getNamespace())) {
			this.setNamespace(env
					.resolvePlaceholders("${spring.cloud.nacos.discovery.namespace:}"));
		}
		if (StringUtils.isEmpty(this.getAccessKey())) {
			this.setAccessKey(env
					.resolvePlaceholders("${spring.cloud.nacos.discovery.access-key:}"));
		}
		if (StringUtils.isEmpty(this.getSecretKey())) {
			this.setSecretKey(env
					.resolvePlaceholders("${spring.cloud.nacos.discovery.secret-key:}"));
		}
		if (StringUtils.isEmpty(this.getLogName())) {
			this.setLogName(
					env.resolvePlaceholders("${spring.cloud.nacos.discovery.log-name:}"));
		}
		if (StringUtils.isEmpty(this.getClusterName())) {
			this.setClusterName(env.resolvePlaceholders(
					"${spring.cloud.nacos.discovery.cluster-name:}"));
		}
		if (StringUtils.isEmpty(this.getEndpoint())) {
			this.setEndpoint(
					env.resolvePlaceholders("${spring.cloud.nacos.discovery.endpoint:}"));
		}
		if (StringUtils.isEmpty(this.getGroup())) {
			this.setGroup(
					env.resolvePlaceholders("${spring.cloud.nacos.discovery.group:}"));
		}
		if (StringUtils.isEmpty(this.getUsername())) {
			this.setUsername(env.resolvePlaceholders("${spring.cloud.nacos.username:}"));
		}
		if (StringUtils.isEmpty(this.getPassword())) {
			this.setPassword(env.resolvePlaceholders("${spring.cloud.nacos.password:}"));
		}
	}

	/**
	 * 从 NacosDiscoveryProperties -> Properties，方便构造NamingService
	 */
	public Properties getNacosProperties() {
		Properties properties = new Properties();
		/**
		 * Nacos服务地址
		 */
		properties.put(SERVER_ADDR, serverAddr);
		/**
		 * Nacos用户名
		 */
		properties.put(USERNAME, Objects.toString(username, ""));
		/**
		 * Nacos密码
		 */
		properties.put(PASSWORD, Objects.toString(password, ""));
		/**
		 * 注册的实例所属的命名空间
		 */
		properties.put(NAMESPACE, namespace);
		/**
		 * 本地日志文件名称
		 */
		properties.put(UtilAndComs.NACOS_NAMING_LOG_NAME, logName);

		if (endpoint.contains(":")) {
			/**
			 * 如果端点包含了端口，则将其解析，分别设置到endpoint和endpoint_port
			 */
			int index = endpoint.indexOf(":");
			properties.put(ENDPOINT, endpoint.substring(0, index));
			properties.put(ENDPOINT_PORT, endpoint.substring(index + 1));
		}
		else {
			/**
			 * 不包含端口，则使用默认，对于http来说就是80端口，对于https来说就是443端口
			 */
			properties.put(ENDPOINT, endpoint);
		}

		/**
		 * Nacos注册中心的访问key
		 */
		properties.put(ACCESS_KEY, accessKey);
		/**
		 * Nacos注册中心的密钥
		 */
		properties.put(SECRET_KEY, secretKey);
		// only used for instance.setClusterName()
//		properties.put(CLUSTER_NAME, clusterName);
		/**
		 * 是否在启动时加载本地注册中心缓存
		 */
		properties.put(NAMING_LOAD_CACHE_AT_START, namingLoadCacheAtStart);

		/**
		 * 将其他属性设置到属性中
		 */
		enrichNacosDiscoveryProperties(properties);
		return properties;
	}

	private void enrichNacosDiscoveryProperties(Properties nacosDiscoveryProperties) {
		/**
		 * 从ENV中读取前缀为spring.cloud.nacos.discovery的属性，并将不在NacosDiscoveryProperties中的值加入
		 */
		Map<String, Object> properties = PropertySourcesUtils.getSubProperties((ConfigurableEnvironment) environment, PREFIX);
		properties.forEach((k, v) -> nacosDiscoveryProperties.putIfAbsent(resolveKey(k), String.valueOf(v)));
	}


	private String resolveKey(String key) {
		Matcher matcher = PATTERN.matcher(key);
		/**
		 * TODO by mawen 方法用途：也许可以将StringBuffer替换为StringBuilder
		 *
		 * 或许是因为存在多个线程访问，但是方法内线程封闭，是安全的，所以应该选择StringBuilder
		 */
		//
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(sb, matcher.group(1).toUpperCase());
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

}
