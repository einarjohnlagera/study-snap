package com.studysnap.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "studysnap")
@Getter
public class StudySnapProperties {
	private final Limits limits = new Limits();
	private final Ocr ocr = new Ocr();

	@Getter
	@Setter
	public static class Limits {
		private int maxNotesChars = 12000;
		private long maxImageBytes = 5_000_000;
	}

	@Getter
	@Setter
	public static class Ocr {
		private double confidenceThreshold = 0.8;
	}
}
