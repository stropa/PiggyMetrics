package com.piggymetrics.account;

import com.piggymetrics.account.service.security.CustomUserInfoTokenServices;
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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
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
import org.stropa.autodoc.org.stropa.autodoc.storage.FileStorage;
import org.stropa.autodoc.spring.config.AutodocProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SpringBootApplication
@EnableResourceServer
@EnableDiscoveryClient
@EnableOAuth2Client
@EnableFeignClients
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties
@Configuration
@ComponentScan(basePackages = {"com.piggymetrics.account", "org.stropa.autodoc"})
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

		Map<String, Object> context = new HashMap<>();
		context.put("applicationContext", applicationContext);

		AutodocJavaEngine doc = new AutodocJavaEngine(context);
		//doc.defaultDocs();

		doc.doc("hostname");
		doc.doc("docker-container");
		doc.doc("application");

		Optional<Item> docker = doc.node("docker-container");
		Optional<Item> application = doc.node("_type:spring-application");
		Optional<Item> host = doc.node("_type:host");

		if (docker.isPresent()) {
			doc.link(application, "runs in", docker);
			doc.link(docker, "deployed on", host);
		} else {
			doc.link(application, "deployed on", host);
		}

		doc.writeDocs(new FileStorage("autodoc.log.json"));
	}

}
