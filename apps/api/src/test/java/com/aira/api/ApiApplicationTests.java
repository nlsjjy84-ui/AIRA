package com.aira.api;

import static org.junit.jupiter.api.Assertions.assertNull;

import com.aira.api.demo.OfficialDemoBootstrapRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:context-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
		"aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
class ApiApplicationTests {
	@Autowired(required = false)
	OfficialDemoBootstrapRunner demoBootstrapRunner;

	@Test
	void contextLoads() {
		assertNull(demoBootstrapRunner,
				"Official demo bootstrap must remain disabled in the default application context");
	}

}
