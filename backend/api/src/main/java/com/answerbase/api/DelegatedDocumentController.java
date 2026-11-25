package com.answerbase.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/delegated/workspaces/{workspace}/documents")
@Tag(name = "Delegated documents")
class DelegatedDocumentController {
    private final DocumentService service;
    private final DelegationService delegation;

    DelegatedDocumentController(DocumentService service, DelegationService delegation) {
        this.service = service;
        this.delegation = delegation;
    }

    @GetMapping
    @Operation(summary = "List documents in an accessible workspace, including processing status")
    List<DocumentService.Document> list(
            @RequestHeader("X-Support-Grant") String grant, @PathVariable UUID workspace) {
        return delegation.documentOperation(
                grant, workspace, "list", user -> service.list(user, workspace));
    }

    @GetMapping("/duplicates")
    @Operation(
            summary =
                    "Find exact and potential near duplicates for editor/admin review; up to 100 pairs")
    List<DocumentService.Duplicate> duplicates(
            @RequestHeader("X-Support-Grant") String grant, @PathVariable UUID workspace) {
        return delegation.documentOperation(
                grant, workspace, "duplicates", user -> service.duplicates(user, workspace));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary =
                    "Upload a new document (editor/admin); PDF, DOCX, HTML, TXT or MD, up to 5 MB",
            description =
                    "Requires a UUID Idempotency-Key. Identical retries return the existing version. Original bytes are private. Processing is asynchronous; processed means text extracted, not embedded.")
    DocumentService.Version upload(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestPart("file") MultipartFile file)
            throws IOException {
        byte[] bytes = file.getBytes();
        return delegation.documentOperation(
                grant,
                workspace,
                "upload",
                user ->
                        service.upload(
                                user, workspace, null, key, file.getOriginalFilename(), bytes));
    }

    @PostMapping(value = "/{document}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Upload a replacement version; prior processed versions remain available")
    DocumentService.Version replace(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID document,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestPart("file") MultipartFile file)
            throws IOException {
        byte[] bytes = file.getBytes();
        return delegation.documentOperation(
                grant,
                workspace,
                "version",
                user ->
                        service.upload(
                                user, workspace, document, key, file.getOriginalFilename(), bytes));
    }

    @GetMapping("/{document}/versions")
    @Operation(summary = "List immutable document versions")
    List<DocumentService.Version> versions(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID document) {
        return delegation.documentOperation(
                grant, workspace, "versions", user -> service.versions(user, workspace, document));
    }

    @GetMapping("/{document}/versions/{version}/original")
    @Operation(
            summary = "Download the private original after checking current workspace membership")
    ResponseEntity<byte[]> original(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID document,
            @PathVariable UUID version) {
        return delegation.documentOperation(
                grant,
                workspace,
                "download",
                user -> download(service.original(user, workspace, document, version)));
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
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID document,
            @PathVariable UUID version,
            @RequestParam(defaultValue = "0") int offset) {
        return delegation.documentOperation(
                grant,
                workspace,
                "chunks",
                user -> service.chunks(user, workspace, document, version, offset));
    }

    @PostMapping("/{document}/versions/{version}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retry failed extraction (editor/admin)")
    void retry(
            @RequestHeader("X-Support-Grant") String grant,
            @PathVariable UUID workspace,
            @PathVariable UUID document,
            @PathVariable UUID version) {
        delegation.documentOperation(
                grant,
                workspace,
                "retry",
                user -> {
                    service.retry(user, workspace, document, version);
                    return null;
                });
    }
}
