package com.studysnap.backend.service;

import com.studysnap.backend.service.model.OcrResult;
import org.springframework.web.multipart.MultipartFile;

public interface OcrService {
	OcrResult extractText(MultipartFile image);
}
