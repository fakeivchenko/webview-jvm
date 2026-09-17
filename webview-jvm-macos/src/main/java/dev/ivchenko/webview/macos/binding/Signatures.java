package dev.ivchenko.webview.macos.binding;

import dev.ivchenko.webview.foreign.CLayouts;
import lombok.experimental.UtilityClass;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;

/**
 * Every native signature the macOS backend binds, in one place.
 *
 * <p>Almost everything here is a shape of {@code objc_msgSend}: the runtime's trampoline takes whatever the target
 * method takes, so one downcall handle per distinct signature serves every selector of that signature. The receiver
 * and selector come first in each.</p>
 *
 * <p>No method that returns a struct is bound. On x86_64 such a call has to go through {@code objc_msgSend_stret},
 * which does not exist on arm64; avoiding struct returns keeps the bindings identical on both.</p>
 */
@UtilityClass
public class Signatures {
    /** {@code NSInteger}, {@code NSUInteger}: 64-bit on every supported Mac. */
    public final ValueLayout.OfLong C_LONG = CLayouts.C_LONG_LONG;

    /** {@code int}. */
    public final ValueLayout.OfInt C_INT = CLayouts.C_INT;

    /** {@code short}. */
    public final ValueLayout.OfShort C_SHORT = CLayouts.C_SHORT;

    /** {@code CGFloat}. */
    public final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE;

    /** {@code BOOL}: one byte on both architectures. */
    public final ValueLayout.OfBoolean C_BOOL = CLayouts.C_BOOL;

    /** {@code id}, {@code SEL}, {@code Class}, any {@code T*}. */
    public final AddressLayout C_POINTER = CLayouts.C_POINTER;

    // --- structs, laid out flat: the ABI treats them exactly as it treats the nested originals ---

    /** {@code NSPoint}. */
    public final StructLayout NSPOINT = MemoryLayout.structLayout(
            C_DOUBLE.withName("x"), C_DOUBLE.withName("y"));

    /** {@code NSSize}. */
    public final StructLayout NSSIZE = MemoryLayout.structLayout(
            C_DOUBLE.withName("width"), C_DOUBLE.withName("height"));

    /** {@code NSRect}. */
    public final StructLayout NSRECT = MemoryLayout.structLayout(
            C_DOUBLE.withName("x"), C_DOUBLE.withName("y"), C_DOUBLE.withName("width"), C_DOUBLE.withName("height"));

    /** An Objective-C block literal with one captured {@code long}, see {@link ObjC#block}. */
    public final StructLayout BLOCK = MemoryLayout.structLayout(
            C_POINTER.withName("isa"), C_INT.withName("flags"), C_INT.withName("reserved"),
            C_POINTER.withName("invoke"), C_POINTER.withName("descriptor"), C_LONG.withName("context"));

    /** The descriptor every block literal points at. */
    public final StructLayout BLOCK_DESCRIPTOR = MemoryLayout.structLayout(
            C_LONG.withName("reserved"), C_LONG.withName("size"));

    // --- C functions ---

    /** {@code int f(void)} */
    public final FunctionDescriptor INT_VOID = FunctionDescriptor.of(C_INT);

    /** {@code T* f(void)} */
    public final FunctionDescriptor POINTER_VOID = FunctionDescriptor.of(C_POINTER);

    /** {@code void f(T*)} */
    public final FunctionDescriptor VOID_POINTER = FunctionDescriptor.ofVoid(C_POINTER);

    /** {@code T* f(U*)} */
    public final FunctionDescriptor POINTER_POINTER = FunctionDescriptor.of(C_POINTER, C_POINTER);

    /** {@code void f(T*, U*, V*)} */
    public final FunctionDescriptor VOID_POINTER_POINTER_POINTER =
            FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER);

    /** {@code Class objc_allocateClassPair(Class, const char*, size_t)} */
    public final FunctionDescriptor POINTER_POINTER_POINTER_LONG =
            FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_LONG);

    /** {@code BOOL class_addMethod(Class, SEL, IMP, const char*)} */
    public final FunctionDescriptor BOOL_POINTER_POINTER_POINTER_POINTER =
            FunctionDescriptor.of(C_BOOL, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

    // --- objc_msgSend shapes: receiver, selector, then the method's own arguments ---

    /** {@code id -[receiver selector]} */
    public final FunctionDescriptor MSG_ID = FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER);

    /** {@code void -[receiver selector]} */
    public final FunctionDescriptor MSG_VOID = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER);

    /** {@code NSInteger -[receiver selector]} */
    public final FunctionDescriptor MSG_LONG = FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER);

    /** {@code id -[receiver selector:id]} */
    public final FunctionDescriptor MSG_ID_ID = FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER);

    /** {@code void -[receiver selector:id]} */
    public final FunctionDescriptor MSG_VOID_ID = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER);

    /** {@code void -[receiver selector:NSInteger]} */
    public final FunctionDescriptor MSG_VOID_LONG = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_LONG);

    /** {@code void -[receiver selector:BOOL]} */
    public final FunctionDescriptor MSG_VOID_BOOL = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_BOOL);

    /** {@code BOOL -[receiver selector:NSInteger]} */
    public final FunctionDescriptor MSG_BOOL_LONG = FunctionDescriptor.of(C_BOOL, C_POINTER, C_POINTER, C_LONG);

    /** {@code id -[receiver selector:id selector:id]} */
    public final FunctionDescriptor MSG_ID_ID_ID =
            FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

    /** {@code void -[receiver selector:id selector:id]} */
    public final FunctionDescriptor MSG_VOID_ID_ID =
            FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_POINTER);

    /** {@code void -[receiver selector:id selector:BOOL]} */
    public final FunctionDescriptor MSG_VOID_ID_BOOL =
            FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_BOOL);

    /** {@code id -[receiver selector:const void* selector:NSUInteger]} */
    public final FunctionDescriptor MSG_ID_POINTER_LONG =
            FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_LONG);

    /** {@code void -[receiver selector:void* selector:NSUInteger]} */
    public final FunctionDescriptor MSG_VOID_POINTER_LONG =
            FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_LONG);

    /** {@code id -[receiver selector:id selector:NSInteger selector:BOOL]} */
    public final FunctionDescriptor MSG_ID_ID_LONG_BOOL =
            FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_LONG, C_BOOL);

    /** {@code id -[receiver selector:id selector:NSInteger selector:id]} */
    public final FunctionDescriptor MSG_ID_ID_LONG_ID =
            FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_LONG, C_POINTER);

    /** {@code id -[receiver selector:id selector:id selector:NSInteger selector:id]} */
    public final FunctionDescriptor MSG_ID_ID_ID_LONG_ID =
            FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_LONG, C_POINTER);

    /** {@code id -[receiver selector:NSRect selector:NSUInteger selector:NSUInteger selector:BOOL]} */
    public final FunctionDescriptor MSG_ID_RECT_LONG_LONG_BOOL =
            FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, NSRECT, C_LONG, C_LONG, C_BOOL);

    /** {@code id -[receiver selector:NSRect selector:id]} */
    public final FunctionDescriptor MSG_ID_RECT_ID =
            FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, NSRECT, C_POINTER);

    /** {@code void -[receiver selector:NSSize]} */
    public final FunctionDescriptor MSG_VOID_SIZE = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, NSSIZE);

    /**
     * {@code +[NSEvent otherEventWithType:location:modifierFlags:timestamp:windowNumber:context:subtype:data1:data2:]}
     */
    public final FunctionDescriptor MSG_OTHER_EVENT = FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER,
            C_LONG, NSPOINT, C_LONG, C_DOUBLE, C_LONG, C_POINTER, C_SHORT, C_LONG, C_LONG);

    // --- callbacks ---

    /** A delegate method with one argument: {@code -(void)method:(id)a}, receiving {@code self} and {@code _cmd} first. */
    public final FunctionDescriptor DELEGATE_1 = VOID_POINTER_POINTER_POINTER;

    /** A delegate method with two arguments. */
    public final FunctionDescriptor DELEGATE_2 = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_POINTER);

    /** A delegate method with three arguments. */
    public final FunctionDescriptor DELEGATE_3 =
            FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

    /** {@code void (^)(id result, NSError* error)}, receiving the block itself first. */
    public final FunctionDescriptor COMPLETION_BLOCK = VOID_POINTER_POINTER_POINTER;

    /** {@code dispatch_function_t}. */
    public final FunctionDescriptor DISPATCH_FUNCTION = VOID_POINTER;
}
