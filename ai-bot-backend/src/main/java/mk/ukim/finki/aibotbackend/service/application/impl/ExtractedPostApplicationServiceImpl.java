package mk.ukim.finki.aibotbackend.service.application.impl;

import java.util.Optional;
import mk.ukim.finki.aibotbackend.model.dto.DisplayExtractedPostDto;
import mk.ukim.finki.aibotbackend.model.dto.PostFilterDto;
import mk.ukim.finki.aibotbackend.service.application.ExtractedPostApplicationService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractedPostService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
public class ExtractedPostApplicationServiceImpl implements ExtractedPostApplicationService {
    private final ExtractedPostService extractedPostService;

    public ExtractedPostApplicationServiceImpl(ExtractedPostService extractedPostService) {
        this.extractedPostService = extractedPostService;
    }

    @Override
    public Page<DisplayExtractedPostDto> findAll(PostFilterDto filter, int page, int size) {
        return extractedPostService.findAll(filter, page, size).map(DisplayExtractedPostDto::from);
    }

    @Override
    public Optional<DisplayExtractedPostDto> findById(Long id) {
        return extractedPostService.findById(id).map(DisplayExtractedPostDto::from);
    }

    @Override
    public Optional<DisplayExtractedPostDto> deleteById(Long id) {
        return extractedPostService.deleteById(id).map(DisplayExtractedPostDto::from);
    }
}
