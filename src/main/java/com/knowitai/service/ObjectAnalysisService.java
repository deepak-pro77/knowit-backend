package com.knowitai.service;

import com.knowitai.ai.VisionAiService;
import com.knowitai.dto.AnalysisResponse;
import com.knowitai.dto.AskRequest;
import com.knowitai.dto.AskResponse;
import com.knowitai.validation.ImageValidator;
import com.knowitai.validation.ValidatedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ObjectAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ObjectAnalysisService.class);

    private final ImageValidator imageValidator;
    private final VisionAiService visionAi;
    private final SafetyAssessmentService safety;

    public ObjectAnalysisService(ImageValidator imageValidator, VisionAiService visionAi,
                                 SafetyAssessmentService safety) {
        this.imageValidator = imageValidator;
        this.visionAi = visionAi;
        this.safety = safety;
    }

    public AnalysisResponse analyze(MultipartFile file) {
        ValidatedImage image = imageValidator.validate(file);
        long start = System.nanoTime();
        AnalysisResponse result = safety.apply(visionAi.analyze(image));
        // Log only metadata. Never the image, never the model's full output.
        log.info("Analyzed image type={} bytes={} confidence={} risk={} took={}ms",
                image.mimeType(), image.data().length, result.identification().confidence(),
                result.safety().riskLevel(), (System.nanoTime() - start) / 1_000_000);
        return result;
    }

    public AskResponse ask(AskRequest request) {
        return new AskResponse(visionAi.answer(request));
    }
}
