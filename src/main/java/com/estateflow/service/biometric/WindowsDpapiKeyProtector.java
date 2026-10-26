package com.estateflow.service.biometric;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/** DPAPI CurrentUser protection. The process-local native library arena lives for the process lifetime. */
public final class WindowsDpapiKeyProtector implements KeyProtector {
    private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
    private static final AddressLayout ADDRESS = ValueLayout.ADDRESS;
    private static final MemoryLayout BLOB = MemoryLayout.structLayout(INT.withName("cbData"),
            MemoryLayout.paddingLayout(4), ADDRESS.withName("pbData")).withByteAlignment(8);
    private static final long DATA_POINTER_OFFSET = 8;
    private static final Arena LIBRARIES = Arena.ofShared();
    private static final MethodHandle PROTECT, UNPROTECT, LOCAL_FREE, LAST_ERROR;

    static {
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup crypt = SymbolLookup.libraryLookup("Crypt32.dll", LIBRARIES);
            SymbolLookup kernel = SymbolLookup.libraryLookup("Kernel32.dll", LIBRARIES);
            PROTECT = linker.downcallHandle(crypt.find("CryptProtectData").orElseThrow(),
                    FunctionDescriptor.of(INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, INT, ADDRESS));
            UNPROTECT = linker.downcallHandle(crypt.find("CryptUnprotectData").orElseThrow(),
                    FunctionDescriptor.of(INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, INT, ADDRESS));
            LOCAL_FREE = linker.downcallHandle(kernel.find("LocalFree").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS));
            LAST_ERROR = linker.downcallHandle(kernel.find("GetLastError").orElseThrow(), FunctionDescriptor.of(INT));
        } catch (Throwable e) { throw new ExceptionInInitializerError(e); }
    }

    public WindowsDpapiKeyProtector() {
        String arch=System.getProperty("os.arch", "").toLowerCase();
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows") || !(arch.equals("amd64")||arch.equals("x86_64")))
            throw new IllegalStateException("Windows 64-bit DPAPI is required for face-template keys.");
    }

    @Override public byte[] protect(byte[] key) { return transform(key, true); }
    @Override public byte[] unprotect(byte[] protectedKey) { return transform(protectedKey, false); }

    private byte[] transform(byte[] source, boolean protect) {
        if (source == null || source.length == 0 || source.length > 65536) throw new IllegalArgumentException("Invalid DPAPI input.");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inputData = arena.allocate(source.length, 1);
            inputData.copyFrom(MemorySegment.ofArray(source));
            MemorySegment input = arena.allocate(BLOB);
            input.set(INT, 0, source.length);
            input.set(ADDRESS, DATA_POINTER_OFFSET, inputData);
            MemorySegment output = arena.allocate(BLOB);
            int result;
            try {
                if (protect) result = (int) PROTECT.invokeExact(input, MemorySegment.NULL, MemorySegment.NULL,
                        MemorySegment.NULL, MemorySegment.NULL, 1, output);
                else result = (int) UNPROTECT.invokeExact(input, MemorySegment.NULL, MemorySegment.NULL,
                        MemorySegment.NULL, MemorySegment.NULL, 1, output);
            } catch (Throwable e) { throw new IllegalStateException("Windows DPAPI operation failed.", e); }
            finally { inputData.fill((byte) 0); }
            if (result == 0) throw new IllegalStateException("Windows DPAPI operation failed (code " + lastError() + ").");
            MemorySegment nativeOutput = output.get(ADDRESS, DATA_POINTER_OFFSET);
            MemorySegment bytes = null;
            try {
                long length = output.get(INT, 0);
                if (nativeOutput.address() == 0 || length < 1 || length > 65536) throw new IllegalStateException("Windows DPAPI returned invalid output.");
                bytes = nativeOutput.reinterpret(length, arena, ignored -> { });
                return bytes.toArray(ValueLayout.JAVA_BYTE);
            } finally {
                if(bytes!=null) bytes.fill((byte)0);
                if (nativeOutput.address() != 0) {
                    try {
                        MemorySegment freeResult=(MemorySegment)LOCAL_FREE.invokeExact(nativeOutput);
                        if(freeResult.address()!=0) throw new IllegalStateException("Windows DPAPI output memory could not be released (code " + lastError() + ").");
                    } catch (Throwable e) { throw new IllegalStateException("Windows DPAPI output cleanup failed.", e); }
                }
            }
        }
    }

    private static int lastError() {
        try { return (int) LAST_ERROR.invokeExact(); }
        catch (Throwable e) { throw new IllegalStateException("Could not read the Windows cryptographic error code.", e); }
    }
}
