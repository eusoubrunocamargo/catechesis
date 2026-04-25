package com.catechesis.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.catechesis.backend.church.Church;
import com.catechesis.backend.church.ChurchRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

	@Autowired
	private ChurchRepository churchRepository;

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void canPersistAndLoadAChurch() {
		Church saved = churchRepository.save(
				new Church("Paróquia de Teste", "America/Sao_Paulo")
		);

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();

		UUID id = saved.getId();
		Church reloaded = churchRepository.findById(id).orElseThrow();

		assertThat(reloaded.getDisplayName()).isEqualTo("Paróquia de Teste");
		assertThat(reloaded.getTimezone()).isEqualTo("America/Sao_Paulo");
	}
}