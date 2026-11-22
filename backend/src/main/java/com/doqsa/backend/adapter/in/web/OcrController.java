package com.doqsa.backend.adapter.in.web;

import com.doqsa.backend.application.model.OcrModels.*;
import com.doqsa.backend.application.port.in.OcrUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/ocr/items")
@RequiredArgsConstructor
public class OcrController {
    private final OcrUseCase ocr;
    public record CreateRequest(@NotBlank @Size(max = 255) String fileName,
                                @NotBlank @Pattern(regexp = "image/(jpeg|png|webp)") String contentType) { }

    @PostMapping
    public ResponseEntity<Created> create(@Valid @RequestBody CreateRequest request) {
        var result = ocr.create(request.fileName(), request.contentType());
        return ResponseEntity.created(URI.create("/api/ocr/items/" + result.id())).body(result);
    }
    @PostMapping("/{id}/upload-complete")
    public View uploaded(@PathVariable @Positive Long id) { return ocr.uploaded(id); }
    @PostMapping("/{id}/upload-url")
    public Upload reissue(@PathVariable @Positive Long id) { return ocr.reissueUpload(id); }
    @GetMapping("/{id}")
    public View get(@PathVariable @Positive Long id) { return ocr.get(id); }
    @GetMapping
    public Page list(@RequestParam(required = false) @Positive Long cursor,
                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return ocr.list(cursor, size); }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive Long id) { ocr.delete(id); }
}
