package com.studysnap.backend.service.impl;

import com.studysnap.backend.service.OcrService;
import com.studysnap.backend.service.model.OcrResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(prefix = "studysnap.ocr", name = "provider", havingValue = "stub", matchIfMissing = true)
public class StubOcrService implements OcrService {

	@Override
	public OcrResult extractText(MultipartFile image) {
		String filename = image.getOriginalFilename() == null ? "uploaded image" : image.getOriginalFilename();
		String extracted = "Extracted notes from image: " + filename;
		double confidence = filename.toLowerCase().contains("blurry") ? 0.72 : 0.91;
		return new OcrResult(extracted, confidence);
	}
}
