package com.knowitai.controller;

import com.knowitai.dto.AnalysisResponse;
import com.knowitai.dto.AskRequest;
import com.knowitai.dto.AskResponse;
import com.knowitai.service.ObjectAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/objects")
public class ObjectAnalysisController {

    private final ObjectAnalysisService service;

    public ObjectAnalysisController(ObjectAnalysisService service) {
        this.service = service;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisResponse analyze(@RequestParam("image") MultipartFile image) {
        return service.analyze(image);
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return service.ask(request);
    }
}
