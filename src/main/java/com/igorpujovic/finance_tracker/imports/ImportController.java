package com.igorpujovic.finance_tracker.imports;

import com.igorpujovic.finance_tracker.common.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService service;

    public ImportController(ImportService service) {
        this.service = service;
    }

    /**
     * Returns 201 with the batch summary whatever the file contained. A statement with
     * rejected lines in it is a successful import of the rest, not a failed request:
     * the only 400s are about the upload itself.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportBatchResponse> upload(@RequestPart("file") MultipartFile file) {
        ImportBatch batch = service.importStatement(file.getOriginalFilename(), bytesOf(file));
        return ResponseEntity
                .created(URI.create("/api/imports/" + batch.getId()))
                .body(ImportBatchResponse.of(batch));
    }

    @GetMapping
    public PageResponse<ImportBatchResponse> list(
            @PageableDefault(size = 20, sort = {ImportBatch_.IMPORTED_AT, ImportBatch_.ID},
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(service.list(pageable), ImportBatchResponse::of);
    }

    @GetMapping("/{id}")
    public ImportBatchResponse get(@PathVariable Long id) {
        ImportBatch batch = service.get(id);
        return ImportBatchResponse.withErrors(batch, service.errorsOf(id));
    }

    /**
     * A statement small enough to accept is small enough to read whole, and the
     * duplicate check needs every fingerprint in the file before it writes anything,
     * so there is nothing to be gained by streaming it.
     */
    private byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("The uploaded statement could not be read", e);
        }
    }
}
