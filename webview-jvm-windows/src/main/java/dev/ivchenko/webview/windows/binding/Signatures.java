package dev.ivchenko.webview.windows.binding;

import dev.ivchenko.webview.foreign.CLayouts;
import lombok.experimental.UtilityClass;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;

/**
 * Every native signature the Windows backend binds, in one place.
 *
 * <p>Win32 types map onto four layouts: {@code HANDLE}/{@code HWND}/pointers are {@code void*},
 * {@code UINT}/{@code DWORD}/{@code BOOL}/{@code HRESULT} are 32-bit, and the pointer-sized integers
 * {@code WPARAM}/{@code LPARAM}/{@code LRESULT}/{@code LONG_PTR} are 64-bit. COM methods are ordinary C functions whose
 * first argument is the object, so they share these shapes.</p>
 *
 * <p>Loading this class has no side effects - it opens no library and creates no {@link java.lang.foreign.Linker}.</p>
 */
@UtilityClass
public class Signatures {
    /** {@code int}, {@code UINT}, {@code DWORD}, {@code BOOL}, {@code HRESULT}, {@code LSTATUS}. */
    public final ValueLayout.OfInt C_INT = CLayouts.C_INT;

    /** {@code WPARAM}, {@code LPARAM}, {@code LRESULT}, {@code LONG_PTR}: pointer-sized. */
    public final ValueLayout.OfLong C_LONG_PTR = CLayouts.C_LONG_LONG;

    /** {@code WORD}, {@code ATOM}. */
    public final ValueLayout.OfShort C_SHORT = CLayouts.C_SHORT;

    /** C {@code bool}. */
    public final ValueLayout.OfBoolean C_BOOL = CLayouts.C_BOOL;

    /** Any {@code T*}. */
    public final AddressLayout C_POINTER = CLayouts.C_POINTER;

    /** {@code struct RECT { LONG left, top, right, bottom; }} */
    public final MemoryLayout RECT = MemoryLayout.structLayout(
            C_INT.withName("left"), C_INT.withName("top"), C_INT.withName("right"), C_INT.withName("bottom"));

    /** {@code struct POINT { LONG x, y; }} */
    public final MemoryLayout POINT = MemoryLayout.structLayout(C_INT.withName("x"), C_INT.withName("y"));

    /** {@code struct MSG { HWND hwnd; UINT message; WPARAM wParam; LPARAM lParam; DWORD time; POINT pt; }} */
    public final MemoryLayout MSG = MemoryLayout.structLayout(
            C_POINTER.withName("hwnd"),
            C_INT.withName("message"),
            MemoryLayout.paddingLayout(4),
            C_LONG_PTR.withName("wParam"),
            C_LONG_PTR.withName("lParam"),
            C_INT.withName("time"),
            POINT.withName("pt"),
            MemoryLayout.paddingLayout(4));

    /** {@code struct WNDCLASSEXW} */
    public final MemoryLayout WNDCLASSEXW = MemoryLayout.structLayout(
            C_INT.withName("cbSize"),
            C_INT.withName("style"),
            C_POINTER.withName("lpfnWndProc"),
            C_INT.withName("cbClsExtra"),
            C_INT.withName("cbWndExtra"),
            C_POINTER.withName("hInstance"),
            C_POINTER.withName("hIcon"),
            C_POINTER.withName("hCursor"),
            C_POINTER.withName("hbrBackground"),
            C_POINTER.withName("lpszMenuName"),
            C_POINTER.withName("lpszClassName"),
            C_POINTER.withName("hIconSm"));

    /** {@code GUID}: 16 bytes. */
    public final MemoryLayout GUID = MemoryLayout.sequenceLayout(16, CLayouts.C_CHAR);

    // --- shapes ---

    /** {@code void f(T*)} */
    public final FunctionDescriptor VOID_POINTER = FunctionDescriptor.ofVoid(C_POINTER);

    /** {@code int f(void)} */
    public final FunctionDescriptor INT_VOID = FunctionDescriptor.of(C_INT);

    /** {@code int f(T*)} - {@code HRESULT (this)}, {@code BOOL f(HWND)}, {@code ULONG AddRef(this)}. */
    public final FunctionDescriptor INT_POINTER = FunctionDescriptor.of(C_INT, C_POINTER);

    /** {@code int f(T*, int)} */
    public final FunctionDescriptor INT_POINTER_INT = FunctionDescriptor.of(C_INT, C_POINTER, C_INT);

    /** {@code int f(T*, U*)} - {@code HRESULT (this, arg)}. */
    public final FunctionDescriptor INT_POINTER_POINTER = FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER);

    /** {@code int f(T*, U*, V*)} - {@code HRESULT (this, arg, arg)}, {@code QueryInterface}. */
    public final FunctionDescriptor INT_POINTER_POINTER_POINTER =
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER);

    /** {@code int f(T*, U*, int)} - {@code AddWebResourceRequestedFilter}, {@code GetWindowTextW}. */
    public final FunctionDescriptor INT_POINTER_POINTER_INT =
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT);

    /** {@code int f(T*, int, U*)} - every {@code *CompletedHandler::Invoke(this, HRESULT, arg)}. */
    public final FunctionDescriptor INT_POINTER_INT_POINTER =
            FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER);

    /** {@code int f(T*, int, int, int)} - {@code AdjustWindowRectEx}. */
    public final FunctionDescriptor INT_POINTER_INT_INT_INT =
            FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_INT, C_INT);

    /** {@code int f(T*, U*, int, int)} - {@code GetMessageW}. */
    public final FunctionDescriptor INT_POINTER_POINTER_INT_INT =
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT, C_INT);

    /** {@code int f(T*, U*, int, int, int)} - {@code PeekMessageW}. */
    public final FunctionDescriptor INT_POINTER_POINTER_INT_INT_INT =
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT, C_INT, C_INT);

    /** {@code int f(int, int, long, long)} - {@code PostThreadMessageW}. */
    public final FunctionDescriptor INT_INT_INT_LONG_LONG =
            FunctionDescriptor.of(C_INT, C_INT, C_INT, C_LONG_PTR, C_LONG_PTR);

    /** {@code int f(T*, U*, int, int, int, int, int)} - {@code SetWindowPos}. */
    public final FunctionDescriptor INT_POINTER_POINTER_INT_X5 =
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT, C_INT, C_INT, C_INT, C_INT);

    /** {@code int f(T*, U*, V*, int, W*, X*, Y*)} - {@code RegGetValueW}. */
    public final FunctionDescriptor INT_POINTER_X3_INT_POINTER_X3 =
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_INT, C_POINTER, C_POINTER, C_POINTER);

    /** {@code int f(T*, U*, int, V*, W*, X*)} - {@code CreateWebResourceResponse}. */
    public final FunctionDescriptor INT_POINTER_POINTER_INT_POINTER_X3 =
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_INT, C_POINTER, C_POINTER, C_POINTER);

    /** {@code int f(T*, RECT)} - {@code ICoreWebView2Controller::put_Bounds}, by value. */
    public final FunctionDescriptor INT_POINTER_RECT = FunctionDescriptor.of(C_INT, C_POINTER, RECT);

    /** {@code int f(bool, int, T*, U*, V*)} - {@code CreateWebViewEnvironmentWithOptionsInternal}. */
    public final FunctionDescriptor INT_BOOL_INT_POINTER_X3 =
            FunctionDescriptor.of(C_INT, C_BOOL, C_INT, C_POINTER, C_POINTER, C_POINTER);

    /** {@code long f(T*, int, long, long)} - {@code WNDPROC}, {@code DefWindowProcW}. */
    public final FunctionDescriptor LONG_POINTER_INT_LONG_LONG =
            FunctionDescriptor.of(C_LONG_PTR, C_POINTER, C_INT, C_LONG_PTR, C_LONG_PTR);

    /** {@code long f(T*, int)} - {@code GetWindowLongPtrW}, {@code DispatchMessageW} has one arg. */
    public final FunctionDescriptor LONG_POINTER_INT = FunctionDescriptor.of(C_LONG_PTR, C_POINTER, C_INT);

    /** {@code long f(T*)} - {@code DispatchMessageW}. */
    public final FunctionDescriptor LONG_POINTER = FunctionDescriptor.of(C_LONG_PTR, C_POINTER);

    /** {@code long f(T*, int, long)} - {@code SetWindowLongPtrW}. */
    public final FunctionDescriptor LONG_POINTER_INT_LONG =
            FunctionDescriptor.of(C_LONG_PTR, C_POINTER, C_INT, C_LONG_PTR);

    /** {@code short f(T*)} - {@code RegisterClassExW}. */
    public final FunctionDescriptor SHORT_POINTER = FunctionDescriptor.of(C_SHORT, C_POINTER);

    /** {@code T* f(U*)} - {@code GetModuleHandleW}. */
    public final FunctionDescriptor POINTER_POINTER = FunctionDescriptor.of(C_POINTER, C_POINTER);

    /** {@code T* f(U*, V*)} - {@code GetProcAddress}, {@code LoadCursorW}. */
    public final FunctionDescriptor POINTER_POINTER_POINTER = FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER);

    /** {@code T* f(U*, int)} - {@code SHCreateMemStream}. */
    public final FunctionDescriptor POINTER_POINTER_INT = FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT);

    /** {@code T* f(U*, V*, int)} - {@code LoadLibraryExW}. */
    public final FunctionDescriptor POINTER_POINTER_POINTER_INT =
            FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_INT);

    /** {@code T* f(int, U*, V*, int, int, int, int, int, W*, X*, Y*, Z*)} - {@code CreateWindowExW}. */
    public final FunctionDescriptor CREATE_WINDOW_EX = FunctionDescriptor.of(C_POINTER,
            C_INT, C_POINTER, C_POINTER, C_INT, C_INT, C_INT, C_INT, C_INT,
            C_POINTER, C_POINTER, C_POINTER, C_POINTER);
}
