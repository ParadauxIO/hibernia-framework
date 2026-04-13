package io.paradaux.hibernia.framework.exceptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionsTest {

    // ── BadCommandException ───────────────────────────────────────────────────

    @Test
    void badCommandException_storesMessage() {
        BadCommandException ex = new BadCommandException("bad command");
        assertEquals("bad command", ex.getMessage());
    }

    @Test
    void badCommandException_isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new BadCommandException("x"));
    }

    // ── ConflictException ─────────────────────────────────────────────────────

    @Test
    void conflictException_storesMessage() {
        ConflictException ex = new ConflictException("conflict");
        assertEquals("conflict", ex.getMessage());
    }

    @Test
    void conflictException_storesMessageAndCause() {
        Throwable cause = new IllegalStateException("root");
        ConflictException ex = new ConflictException("conflict", cause);
        assertEquals("conflict", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void conflictException_isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new ConflictException("x"));
    }

    // ── ExceedsLimitException ────────────────────────────────────────────────

    @Test
    void exceedsLimitException_storesMessage() {
        ExceedsLimitException ex = new ExceedsLimitException("limit exceeded");
        assertEquals("limit exceeded", ex.getMessage());
    }

    @Test
    void exceedsLimitException_isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new ExceedsLimitException("x"));
    }

    // ── InternalException ────────────────────────────────────────────────────

    @Test
    void internalException_storesMessage() {
        InternalException ex = new InternalException("internal error");
        assertEquals("internal error", ex.getMessage());
    }

    @Test
    void internalException_isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new InternalException("x"));
    }

    // ── NoPermissionException ────────────────────────────────────────────────

    @Test
    void noPermissionException_storesMessage() {
        NoPermissionException ex = new NoPermissionException("no permission");
        assertEquals("no permission", ex.getMessage());
    }

    @Test
    void noPermissionException_isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new NoPermissionException("x"));
    }

    // ── NotFoundException ────────────────────────────────────────────────────

    @Test
    void notFoundException_storesMessage() {
        NotFoundException ex = new NotFoundException("not found");
        assertEquals("not found", ex.getMessage());
    }

    @Test
    void notFoundException_isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new NotFoundException("x"));
    }

    // ── Throwable behaviour ──────────────────────────────────────────────────

    @Test
    void exceptionsCanBeCaughtAsRuntimeException() {
        assertThrows(RuntimeException.class, () -> { throw new BadCommandException("test"); });
        assertThrows(RuntimeException.class, () -> { throw new ConflictException("test"); });
        assertThrows(RuntimeException.class, () -> { throw new ExceedsLimitException("test"); });
        assertThrows(RuntimeException.class, () -> { throw new InternalException("test"); });
        assertThrows(RuntimeException.class, () -> { throw new NoPermissionException("test"); });
        assertThrows(RuntimeException.class, () -> { throw new NotFoundException("test"); });
    }
}
