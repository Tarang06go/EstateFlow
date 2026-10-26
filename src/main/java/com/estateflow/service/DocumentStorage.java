package com.estateflow.service;

import com.estateflow.exception.ConfigurationException;
import com.estateflow.exception.ValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Locale;
import java.util.UUID;

/** Private local storage. Accepted content is PDF/JPEG/PNG, at most 20 MiB. */
public final class DocumentStorage {
    public static final long MAX_BYTES = 20L * 1024 * 1024;
    public static final String ROOT_PROPERTY = "estateflow.documents.root";
    public static final String ROOT_ENV = "ESTATEFLOW_DOCUMENTS_ROOT";
    private final Path root;
    public DocumentStorage(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public static Path defaultRoot() { return resolveRoot(System.getProperty(ROOT_PROPERTY), System.getenv(ROOT_ENV), Path.of(System.getProperty("user.home"))); }
    static Path resolveRoot(String explicit, String environment, Path userHome) {
        if (explicit != null && !explicit.isBlank()) return Path.of(explicit.trim()).toAbsolutePath().normalize();
        if (environment != null && !environment.isBlank()) return Path.of(environment.trim()).toAbsolutePath().normalize();
        return userHome.resolve(".estateflow").resolve("documents").toAbsolutePath().normalize();
    }
    public Stored store(Path source) throws ValidationException {
        if(source==null) throw new ValidationException("Choose a document first.","file");
        String name=source.getFileName().toString(); String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
        if(!java.util.Set.of("pdf","png","jpg","jpeg").contains(ext)) throw new ValidationException("Only PDF, PNG and JPEG files are supported.","file");
        try {
            if(Files.isSymbolicLink(source) || !Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)) throw new ValidationException("The selected file is not a regular file.","file");
            ensureRoot(); String stored=UUID.randomUUID().toString(); Path target=safePath(stored); Path temp=safePath(stored+".tmp");
            try {
                byte[] head=new byte[8]; int headLength=0; long size=0;
                try(InputStream in=Files.newInputStream(source); OutputStream out=Files.newOutputStream(temp,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                    byte[] buffer=new byte[8192]; int count;
                    while((count=in.read(buffer))!=-1){ size+=count; if(size>MAX_BYTES) throw new ValidationException("Files must be between 1 byte and 20 MB.","file");
                        int take=Math.min(count,head.length-headLength); if(take>0){System.arraycopy(buffer,0,head,headLength,take);headLength+=take;} out.write(buffer,0,count); }
                }
                if(size==0) throw new ValidationException("The selected file is empty.","file");
                String type=sniff(java.util.Arrays.copyOf(head,headLength)); if(type==null||!matches(ext,type)) throw new ValidationException("The file content does not match a supported PDF, PNG or JPEG type.","file");
                privateFile(temp);
                try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE); } catch(AtomicMoveNotSupportedException e) { Files.move(temp,target); }
                privateFile(target);
                return new Stored(name.replaceAll("[\\p{Cntrl}]","_").substring(Math.max(0,name.length()-180)),stored,type,size);
            }
            finally { Files.deleteIfExists(temp); }
        } catch(IOException e) { throw new ConfigurationException("Document storage could not write the selected file.",e); }
    }
    public Path resolve(String storageName) throws ConfigurationException {
        if(storageName==null||!storageName.matches("[0-9a-fA-F-]{36}")) throw new ConfigurationException("Document storage name is invalid.");
        try { ensureRoot(); Path p=safePath(storageName); if(Files.isSymbolicLink(p)||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)) throw new ConfigurationException("The stored document is unavailable."); return p; }
        catch(IOException e){throw new ConfigurationException("Document storage is unavailable.",e);}
    }
    public void remove(String name) throws ConfigurationException { try { Path p=safePath(name); if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(p)) Files.delete(p); } catch(IOException e){ throw new ConfigurationException("Temporary document cleanup failed.",e); } }
    private void ensureRoot() throws IOException { Files.createDirectories(root); if(Files.isSymbolicLink(root)||!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid storage directory"); privateFile(root); }
    private static void privateFile(Path path) throws IOException { try { Files.setPosixFilePermissions(path,Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)?java.util.Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE):java.util.Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE)); } catch(UnsupportedOperationException ignored) { } }
    private Path safePath(String n) { Path p=root.resolve(n).normalize(); if(!p.getParent().equals(root)) throw new IllegalArgumentException("Invalid storage path"); return p; }
    private static String sniff(byte[] b) { if(b.length>=5&&b[0]=='%'&&b[1]=='P'&&b[2]=='D'&&b[3]=='F'&&b[4]=='-') return "application/pdf"; if(b.length>=8&&(b[0]&255)==137&&b[1]==80&&b[2]==78&&b[3]==71) return "image/png"; if(b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255) return "image/jpeg"; return null; }
    private static boolean matches(String ext,String type) { return type.equals("application/pdf")&&ext.equals("pdf")||type.equals("image/png")&&ext.equals("png")||type.equals("image/jpeg")&&(ext.equals("jpg")||ext.equals("jpeg")); }
    public record Stored(String displayName,String storageName,String mediaType,long size) { }
}
