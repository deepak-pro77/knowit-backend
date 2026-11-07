package com.knowitai.ai;

import com.knowitai.dto.AnalysisResponse;
import com.knowitai.dto.AskRequest;
import com.knowitai.validation.ValidatedImage;

/**
 * The only thing the rest of the app knows about "the AI".
 * To switch provider later, write another implementation of this interface.
 */
public interface VisionAiService {

    AnalysisResponse analyze(ValidatedImage image);

    String answer(AskRequest request);
}
