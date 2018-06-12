package com.piggymetrics.account;

import com.google.common.collect.Maps;
import com.piggymetrics.account.service.security.CustomUserInfoTokenServices;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigUtil;
import feign.RequestInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.ResourceServerProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.security.oauth2.client.feign.OAuth2FeignRequestInterceptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.*;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.DefaultOAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.stropa.autodoc.org.stropa.autodoc.storage.FileStorage;
import org.stropa.autodoc.spring.config.AutodocProperties;
import org.stropa.autodoc.storage.ESStorage;

import java.util.*;
import java.util.stream.Collectors;

import static org.stropa.autodoc.engine.AutodocJavaEngine.*;

@SpringBootApplication
@EnableResourceServer
@EnableDiscoveryClient
@EnableOAuth2Client
@EnableFeignClients
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties
@Configuration
public class AccountApplication extends ResourceServerConfigurerAdapter implements ApplicationContextAware {
	private static final Logger log = LoggerFactory.getLogger(AccountApplication.class);

	@Autowired
	private ResourceServerProperties sso;

	@Autowired
	private AutodocProperties autodocProperties;

	@Value("${autodoc.storage.type}")
	private String autodocStorageType;

	public static void main(String[] args) {
		SpringApplication.run(AccountApplication.class, args);
	}

	@Bean
	@ConfigurationProperties(prefix = "security.oauth2.client")
	public ClientCredentialsResourceDetails clientCredentialsResourceDetails() {
		return new ClientCredentialsResourceDetails();
	}

	@Bean
	public RequestInterceptor oauth2FeignRequestInterceptor(){
		return new OAuth2FeignRequestInterceptor(new DefaultOAuth2ClientContext(), clientCredentialsResourceDetails());
	}

	@Bean
	public OAuth2RestTemplate clientCredentialsRestTemplate() {
		return new OAuth2RestTemplate(clientCredentialsResourceDetails());
	}

	@Bean
	public ResourceServerTokenServices tokenServices() {
		return new CustomUserInfoTokenServices(sso.getUserInfoUri(), sso.getClientId());
	}

	@Override
	public void configure(HttpSecurity http) throws Exception {
		http.authorizeRequests()
				.antMatchers("/" , "/demo").permitAll()
				.anyRequest().authenticated();
	}

	@Bean
	@ConfigurationProperties("")
	AutodocProperties autodocProperties() {
		return new AutodocProperties();
	}


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

		System.out.println(autodocStorageType);

		Map<String, Object> map = new HashMap();
		MutablePropertySources propertySources = ((AbstractEnvironment) applicationContext.getEnvironment()).getPropertySources();
		for(Iterator it = propertySources.iterator(); it.hasNext(); ) {
			PropertySource propertySource = (PropertySource) it.next();
			diveIntoPropertySource(map, propertySource, new ArrayList<>());
		}

		System.out.println(map);
		Map<String, Object> autodocConfigMap = Maps.filterKeys(map, key -> key != null && key.startsWith("autodoc"));
		autodocConfigMap = autodocConfigMap.entrySet().stream().collect(
				Collectors.toMap(e -> e.getKey().substring(("autodoc" + ".").length()), e -> e.getValue())
		);

		//Map<String, String> autodoc = autodocProperties.autodoc();
		Config autodocConfig;
		if (autodocConfigMap.isEmpty()) {
			System.out.println("Autodoc config is empty, loading defaults");
			autodocConfig = ConfigFactory.load();
		} else {
			autodocConfig = ConfigFactory.parseMap(autodocConfigMap);
			System.out.println("Got config for autodoc: " + autodocConfig.toString());
		}

		//AutodocJavaEngine docEngine = new AutodocJavaEngine(autodocConfig).ableToDoc("java", "spring", "docker", "networking");


		doc("hostname");
		doc("docker-container");
		doc("application");


		writeDocs(new FileStorage("autodoc.log"));
	}







	private void diveIntoPropertySource(Map<String, Object> map, PropertySource propertySource, List<PropertySource> seenAlready) {
		if (propertySource instanceof MapPropertySource) {
            map.putAll(((MapPropertySource) propertySource).getSource());
        } else if (propertySource instanceof CompositePropertySource) {
			for (PropertySource ps : ((CompositePropertySource) propertySource).getPropertySources()) {
				if (seenAlready.contains(ps)) {
					// skip
				} else {
					seenAlready.add(ps);
					diveIntoPropertySource(map, ps, seenAlready);
				}
			}
		}
	}
}
