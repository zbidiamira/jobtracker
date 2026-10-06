package de.zbidi.jobtracker;

import org.springframework.boot.SpringApplication;

public class TestJobtrackerApplication {

	public static void main(String[] args) {
		SpringApplication.from(JobtrackerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
