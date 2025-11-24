package at.hcw.alcatraz;

import org.springframework.boot.SpringApplication;

public class TestAlcatrazApplication {

	public static void main(String[] args) {
		SpringApplication.from(AlcatrazApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
