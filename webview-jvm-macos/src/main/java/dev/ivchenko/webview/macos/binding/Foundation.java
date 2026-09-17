package dev.ivchenko.webview.macos.binding;

import dev.ivchenko.webview.foreign.NativeLibraries;
import lombok.experimental.UtilityClass;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;

/** Foundation: strings, URLs, data and errors, converted to and from their Java shapes. */
@UtilityClass
public class Foundation {
    static {
        // Loaded for the classes it registers; nothing is looked up by symbol.
        SymbolLookup _ = NativeLibraries.load("/System/Library/Frameworks/Foundation.framework/Foundation");
    }

    /** An autoreleased {@code NSString}; {@code null} becomes {@code nil}. */
    public MemorySegment string(String text) {
        if (text == null) return MemorySegment.NULL;
        try (Arena arena = Arena.ofConfined()) {
            return ObjC.send(ObjC.cls("NSString"), "stringWithUTF8String:", arena.allocateFrom(text));
        }
    }

    /** The Java string of an {@code NSString}; {@code nil} becomes {@code null}. */
    public String string(MemorySegment nsString) {
        if (ObjC.isNull(nsString)) return null;
        return NativeLibraries.string(ObjC.send(nsString, "UTF8String"));
    }

    /** An autoreleased {@code NSURL}. */
    public MemorySegment url(String url) {
        return ObjC.send(ObjC.cls("NSURL"), "URLWithString:", string(url));
    }

    /** {@code -[NSURL absoluteString]}. */
    public String urlString(MemorySegment nsUrl) {
        return string(ObjC.send(nsUrl, "absoluteString"));
    }

    /** An autoreleased {@code NSData} holding a copy of {@code bytes}. */
    public MemorySegment data(byte[] bytes) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
            return ObjC.send(ObjC.cls("NSData"), "dataWithBytes:length:", buffer, bytes.length);
        }
    }

    /** An autoreleased {@code NSError} in the library's own domain. */
    public MemorySegment error(long code, String description) {
        MemorySegment key = string("NSLocalizedDescription");
        MemorySegment userInfo = ObjC.send(ObjC.cls("NSDictionary"), "dictionaryWithObject:forKey:",
                string(description), key);
        return ObjC.send(ObjC.cls("NSError"), "errorWithDomain:code:userInfo:", string("dev.ivchenko.webview"),
                code, userInfo);
    }

    /** {@code -[NSError localizedDescription]}. */
    public String errorDescription(MemorySegment error) {
        return string(ObjC.send(error, "localizedDescription"));
    }

    /** The URL an {@code NSError} from WebKit was loading, or {@code null}. */
    public String errorFailingUrl(MemorySegment error) {
        MemorySegment userInfo = ObjC.send(error, "userInfo");
        return string(ObjC.send(userInfo, "objectForKey:", string("NSErrorFailingURLStringKey")));
    }

    /**
     * The {@code NSRect} behind a {@code frame}-like property, read through key-value coding.
     *
     * <p>KVC boxes the struct into an {@code NSValue} and {@code getValue:size:} copies it out - no struct return, so
     * no {@code objc_msgSend_stret}, which only exists on x86_64.</p>
     */
    public double[] rect(MemorySegment object, String key) {
        MemorySegment value = ObjC.send(object, "valueForKey:", string(key));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rect = arena.allocate(Signatures.NSRECT);
            ObjC.sendVoid(value, "getValue:size:", rect, Signatures.NSRECT.byteSize());
            return new double[] {
                    rect.get(Signatures.C_DOUBLE, 0), rect.get(Signatures.C_DOUBLE, 8),
                    rect.get(Signatures.C_DOUBLE, 16), rect.get(Signatures.C_DOUBLE, 24)};
        }
    }

    /** An {@code NSRect} allocated from {@code arena}. */
    public MemorySegment rect(Arena arena, double x, double y, double width, double height) {
        MemorySegment rect = arena.allocate(Signatures.NSRECT);
        rect.set(Signatures.C_DOUBLE, 0, x);
        rect.set(Signatures.C_DOUBLE, 8, y);
        rect.set(Signatures.C_DOUBLE, 16, width);
        rect.set(Signatures.C_DOUBLE, 24, height);
        return rect;
    }

    /** An {@code NSSize} allocated from {@code arena}. */
    public MemorySegment size(Arena arena, double width, double height) {
        MemorySegment size = arena.allocate(Signatures.NSSIZE);
        size.set(Signatures.C_DOUBLE, 0, width);
        size.set(Signatures.C_DOUBLE, 8, height);
        return size;
    }

    /** An {@code NSPoint} allocated from {@code arena}. */
    public MemorySegment point(Arena arena, double x, double y) {
        MemorySegment point = arena.allocate(Signatures.NSPOINT);
        point.set(Signatures.C_DOUBLE, 0, x);
        point.set(Signatures.C_DOUBLE, 8, y);
        return point;
    }

    /** {@code -[NSObject retain]}, returning the receiver. */
    public MemorySegment retain(MemorySegment object) {
        return ObjC.send(object, "retain");
    }

    /** {@code -[NSObject release]}; {@code nil} is ignored. */
    public void release(MemorySegment object) {
        if (!ObjC.isNull(object)) ObjC.sendVoid(object, "release");
    }

    /** {@code CFBundleShortVersionString} of the bundle that defines {@code cls}, or {@code null}. */
    public String bundleVersion(MemorySegment cls) {
        MemorySegment bundle = ObjC.send(ObjC.cls("NSBundle"), "bundleForClass:", cls);
        return string(ObjC.send(bundle, "objectForInfoDictionaryKey:", string("CFBundleShortVersionString")));
    }
}
