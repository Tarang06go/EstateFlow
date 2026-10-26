package com.estateflow.dao;

import com.estateflow.model.DocumentRecord;
import java.util.List;
import java.util.Optional;

public interface DocumentDao {
    boolean isAvailable();
    default boolean supportsWorkOrderLink() { return false; }
    DocumentRecord insert(DocumentRecord record);
    List<DocumentRecord> search(String query, boolean includeArchived);
    Optional<DocumentRecord> find(long id);
    int archive(long id);
    int markReplacement(long oldId, long newId);
}
