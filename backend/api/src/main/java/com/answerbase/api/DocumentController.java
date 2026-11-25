package com.answerbase.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/workspaces/{workspace}/documents")
@Tag(name = "Documents")
@SecurityRequirement(name = "bearerAuth")
class DocumentController {
    private final DocumentService service;

    DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List documents in an accessible workspace, including processing status")
    List<DocumentService.Document> list(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace) {
        return service.list(UUID.fromString(jwt.getSubject()), workspace);
    }

    @GetMapping("/duplicates")
    @Operation(
            summary =
                    "Find exact and potential near duplicates for editor/admin review; up to 100 pairs")
    List<DocumentService.Duplicate> duplicates(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workspace) {
        return service.duplicates(UUID.fromString(jwt.getSubject()), workspace);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary =
                    "Upload a new document (editor/admin); PDF, DOCX, HTML, TXT or MD, up to 5 MB",
            description =
                    "Requires a UUID Idempotency-Key. Identical retries return the existing version. Original bytes are private. Processing is asynchronous; processed means text extracted, not embedded.")
    DocumentService.Version upload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestPart("file") MultipartFile file)
            throws IOException {
        return service.upload(
                UUID.fromString(jwt.getSubject()),
                workspace,
                null,
                key,
                file.getOriginalFilename(),
                file.getBytes());
    }

    @PostMapping(value = "/{document}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Upload a replacement version; prior processed versions remain available")
    DocumentService.Version replace(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @PathVariable UUID document,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestPart("file") MultipartFile file)
            throws IOException {
        return service.upload(
                UUID.fromString(jwt.getSubject()),
                workspace,
                document,
                key,
                file.getOriginalFilename(),
                file.getBytes());
    }

    @GetMapping("/{document}/versions")
    @Operation(summary = "List immutable document versions")
    List<DocumentService.Version> versions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @PathVariable UUID document) {
        return service.versions(UUID.fromString(jwt.getSubject()), workspace, document);
    }

    @GetMapping("/{document}/versions/{version}/original")
    @Operation(
            summary = "Download the private original after checking current workspace membership")
    ResponseEntity<byte[]> original(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @PathVariable UUID document,
            @PathVariable UUID version) {
        return download(
                service.original(UUID.fromString(jwt.getSubject()), workspace, document, version));
    }

    static ResponseEntity<byte[]> download(DocumentService.Original original) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(original.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(original.bytes());
    }

    @GetMapping("/{document}/versions/{version}/chunks")
    @Operation(summary = "Read extracted passages with page or line references, 50 per page")
    List<DocumentService.Chunk> chunks(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @PathVariable UUID document,
            @PathVariable UUID version,
            @RequestParam(defaultValue = "0") int offset) {
        return service.chunks(
                UUID.fromString(jwt.getSubject()), workspace, document, version, offset);
    }

    @PostMapping("/{document}/versions/{version}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retry failed extraction (editor/admin)")
    void retry(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspace,
            @PathVariable UUID document,
            @PathVariable UUID version) {
        service.retry(UUID.fromString(jwt.getSubject()), workspace, document, version);
    }
}
