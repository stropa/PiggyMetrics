package com.piggymetrics.account;

import com.piggymetrics.account.service.security.CustomUserInfoTokenServices;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import feign.RequestInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.DefaultOAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.stropa.autodoc.engine.AutodocJavaEngine;
import org.stropa.autodoc.engine.Item;
import org.stropa.autodoc.reporters.DockerContainerDescriber;
import org.stropa.autodoc.reporters.HostnameDescriber;
import org.stropa.autodoc.spring.config.AutodocProperties;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@EnableResourceServer
@EnableDiscoveryClient
@EnableOAuth2Client
@EnableFeignClients
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties
@Configuration
public class AccountApplication extends ResourceServerConfigurerAdapter implements ApplicationContextAware {

	@Autowired
	private ResourceServerProperties sso;

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
	@ConfigurationProperties(prefix = "autodoc")
	AutodocProperties autodocProperties() {
		return new AutodocProperties();
	}


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

		Map<String, String> autodoc = autodocProperties().autodoc();
		Config autodocConfig;
		if (autodoc.isEmpty()) {
			autodocConfig = ConfigFactory.load();
		} else {
			autodocConfig = ConfigFactory.parseMap(autodoc);
		}

		AutodocJavaEngine doc = new AutodocJavaEngine(autodocConfig);

		doc.addInfo(new HostnameDescriber().report(), Collections.emptyList());

		List<Item> items = new DockerContainerDescriber().report();
		doc.addInfo(items, Collections.emptyList());

		doc.writeSnapshot();
	}
}
