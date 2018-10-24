package org.springframework.cloud.gateway.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Configuration;


/**
 * GatewayClassPathWarningAutoConfiguration 检测有没有正确导入spring-boot-starter-webflux dependency.
 * 而不是spring-boot-starter-web 依赖
 */
@Configuration
@AutoConfigureBefore(GatewayAutoConfiguration.class)
public class GatewayClassPathWarningAutoConfiguration {

	private static final Log log = LogFactory.getLog(GatewayClassPathWarningAutoConfiguration.class);
	private static final String BORDER = "\n\n**********************************************************\n\n";

	/**
	 * 检查项目是否错误的导入依赖
	 * @ConditionalOnClass 判断是否存在
	 */
	@Configuration
	@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
	protected static class SpringMvcFoundOnClasspathConfiguration {

		public SpringMvcFoundOnClasspathConfiguration() {
			log.warn(BORDER+"Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway at this time. "+
					"Please remove spring-boot-starter-web dependency."+BORDER);
		}

	}

	/**
	 * 检查项目是否争取导入依赖
	 * @ConditionalOnMissingClass 注解如果不存在此类会加载这个bean
	 */
	@Configuration
	@ConditionalOnMissingClass("org.springframework.web.reactive.DispatcherHandler")
	protected static class WebfluxMissingFromClasspathConfiguration {

		public WebfluxMissingFromClasspathConfiguration() {
			log.warn(BORDER+"Spring Webflux is missing from the classpath, which is required for Spring Cloud Gateway at this time. "+
					"Please add spring-boot-starter-webflux dependency."+BORDER);
		}

	}
}
