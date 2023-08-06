package vedis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VedisServerTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void xFail() {
        // Dummy to check CI.
        throw new RuntimeException("Dummy");
    }

    @Test
    void goingToSuccess() {
        // Dummy to check CI.
        assertTrue(true);
    }
}
