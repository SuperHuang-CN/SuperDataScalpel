package cn.superhuang.data.scalpel.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "cn.superhuang.data.scalpel")
@ConfigurationPropertiesScan(basePackages = "cn.superhuang.data.scalpel")
@EntityScan(basePackages = "cn.superhuang.data.scalpel")
@EnableJpaRepositories(basePackages = "cn.superhuang.data.scalpel")
public class DataScalpelAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataScalpelAdminApplication.class, args);
    }
}
