package com.bluecollar;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class BackendApplicationTest {

    @Test
    void main() {
        try (MockedStatic<SpringApplication> springApplicationMockedStatic = mockStatic(SpringApplication.class)) {
            springApplicationMockedStatic.when(() -> SpringApplication.run(BackendApplication.class, new String[]{}))
                    .thenReturn(mock(ConfigurableApplicationContext.class));

            BackendApplication.main(new String[]{});

            springApplicationMockedStatic.verify(() -> SpringApplication.run(BackendApplication.class, new String[]{}));
        }
    }
}
