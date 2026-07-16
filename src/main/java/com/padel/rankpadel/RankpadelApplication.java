package com.padel.rankpadel;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RankpadelApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(
				System.getProperty("app.zona-horaria", "America/Argentina/Buenos_Aires")));
		SpringApplication.run(RankpadelApplication.class, args);
	}

}
