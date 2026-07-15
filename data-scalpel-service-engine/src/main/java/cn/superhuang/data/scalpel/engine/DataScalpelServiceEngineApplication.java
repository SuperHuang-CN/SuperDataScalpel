package cn.superhuang.data.scalpel.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "cn.superhuang.data.scalpel.engine")
@EntityScan(basePackages = "cn.superhuang.data.scalpel.engine")
@EnableJpaRepositories(basePackages = "cn.superhuang.data.scalpel.engine")
public class DataScalpelServiceEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataScalpelServiceEngineApplication.class, args);
    }
}
