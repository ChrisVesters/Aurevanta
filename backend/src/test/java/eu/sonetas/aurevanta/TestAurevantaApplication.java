package eu.sonetas.aurevanta;

import org.springframework.boot.SpringApplication;

public class TestAurevantaApplication {

	public static void main(String[] args) {
		SpringApplication.from(AurevantaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
