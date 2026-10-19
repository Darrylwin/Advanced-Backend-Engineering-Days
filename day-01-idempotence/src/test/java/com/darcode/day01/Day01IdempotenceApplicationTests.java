package com.darcode.day01;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class Day01IdempotenceApplicationTests {

    @Test
    void contextLoads() {
    }

}
