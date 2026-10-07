package de.zbidi.jobtracker.common;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

	@Test
	void mapsPageMetadataAndContent() {
		var page = new PageImpl<>(List.of("d", "e", "f"), PageRequest.of(1, 3), 7);

		PageResponse<String> response = PageResponse.from(page);

		assertThat(response.content()).containsExactly("d", "e", "f");
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.size()).isEqualTo(3);
		assertThat(response.totalElements()).isEqualTo(7);
		assertThat(response.totalPages()).isEqualTo(3);
	}

}
