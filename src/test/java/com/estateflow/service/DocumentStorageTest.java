package com.estateflow.service;

import com.estateflow.exception.ConfigurationException;
import com.estateflow.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentStorageTest {

    @Test void storageRootCanBeIsolatedForRecoveryDrills() {
        Path home = Path.of("C:/EstateFlowTestHome");
        assertEquals(Path.of("C:/custom/documents").toAbsolutePath().normalize(),
                DocumentStorage.resolveRoot("C:/custom/documents", "C:/env/documents", home));
        assertEquals(Path.of("C:/env/documents").toAbsolutePath().normalize(),
                DocumentStorage.resolveRoot(" ", "C:/env/documents", home));
        assertEquals(home.resolve(".estateflow/documents").toAbsolutePath().normalize(),
                DocumentStorage.resolveRoot(null, null, home));
    }
    @TempDir Path temp;
    @Test void storesPdfUnderGeneratedNameAndResolvesOnlyStoredName() throws Exception {
        Path source=temp.resolve("lease.pdf");Files.write(source,"%PDF-1.7\ncontent".getBytes());
        DocumentStorage storage=new DocumentStorage(temp.resolve("private"));
        var result=storage.store(source);
        assertEquals("lease.pdf",result.displayName());assertEquals("application/pdf",result.mediaType());
        assertNotEquals("lease.pdf",result.storageName());assertTrue(Files.isRegularFile(storage.resolve(result.storageName())));
        assertThrows(ConfigurationException.class,()->storage.resolve("..\\lease.pdf"));
    }
    @Test void rejectsDisallowedExtensionsAndContentSpoofing() throws Exception {
        DocumentStorage storage=new DocumentStorage(temp.resolve("private"));
        Path executable=temp.resolve("invoice.exe");Files.writeString(executable,"MZ");
        Path spoof=temp.resolve("invoice.pdf");Files.writeString(spoof,"not a PDF");
        assertThrows(ValidationException.class,()->storage.store(executable));
        assertThrows(ValidationException.class,()->storage.store(spoof));
        try(var files=Files.list(temp.resolve("private"))){assertEquals(0,files.count());}
    }
    @Test void rejectsEmptyAndOversizedDocuments() throws Exception {
        DocumentStorage storage=new DocumentStorage(temp.resolve("private"));
        Path empty=temp.resolve("empty.png");Files.createFile(empty);
        Path large=temp.resolve("large.pdf");try(var out=Files.newOutputStream(large)){out.write("%PDF-1.7".getBytes());out.write(new byte[(int)DocumentStorage.MAX_BYTES]);}
        assertThrows(ValidationException.class,()->storage.store(empty));
        assertThrows(ValidationException.class,()->storage.store(large));
    }
    @Test void refusesSymbolicLinkSourceWhenSupported() throws Exception {
        Path actual=temp.resolve("actual.png");Files.write(actual,new byte[]{(byte)137,80,78,71,13,10,26,10});
        Path link=temp.resolve("link.png");try{Files.createSymbolicLink(link,actual);}catch(UnsupportedOperationException|SecurityException|FileSystemException e){return;}
        assertThrows(ValidationException.class,()->new DocumentStorage(temp.resolve("private")).store(link));
    }
}
