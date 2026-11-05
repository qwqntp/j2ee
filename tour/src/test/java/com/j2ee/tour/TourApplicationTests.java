package com.j2ee.tour;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("Requires PostgreSQL; covered by the manual read-only startup check")
class TourApplicationTests {

	@Test
	@Disabled("Requires PostgreSQL; covered by the manual read-only startup check")
	void contextLoads() {
	}

}
