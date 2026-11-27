package com.answerbase.core.documents;

import static org.junit.jupiter.api.Assertions.*;

import com.answerbase.core.models.JsonHttp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StorageInventoryTests {
    @Test
    void onlyOldServerKeysCanEnterOrphanReconciliation() {
        var response =
                JsonHttp.JSON.readTree(
                        """
          {"objects":[
            {"key":"organizations/old/original","updated_at":"2020-01-01T00:00:00Z"},
            {"name":"organizations/new/original","updated_at":"2030-01-01T00:00:00Z"},
            {"key":"organizations/uncertain/original","updated_at":"invalid"},
            {"key":"unrelated/original","updated_at":"2020-01-01T00:00:00Z"},
            {"key":"organizations/../original","updated_at":"2020-01-01T00:00:00Z"},
            {"key":"organizations/deleted/original","updated_at":"2020-01-01T00:00:00Z","is_delete_marker":true}
          ]}
          """);
        assertEquals(
                List.of("organizations/old/original"),
                SupabaseDocumentStorage.staleInventoryKeys(
                        response, Instant.parse("2026-01-01T00:00:00Z")));
    }
}
