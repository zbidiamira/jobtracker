package de.zbidi.jobtracker.jobsource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlToTextTest {

	@Test
	void blocksBecomeLinesAndListItemsBecomeBullets() {
		assertThat(HtmlToText.convert("<p><strong>Your tasks</strong></p><ul><li>Build APIs</li><li>Run Kafka</li></ul>"))
				.isEqualTo("Your tasks\n- Build APIs\n- Run Kafka");
	}

	@Test
	void entitiesAreDecoded() {
		assertThat(HtmlToText.convert("<p>Java &amp; Spring &lt;3 &quot;clean&quot; &#39;code&#39;&nbsp;now</p>"))
				.isEqualTo("Java & Spring <3 \"clean\" 'code' now");
	}

	@Test
	void lineBreaksAndSpacesAreTidied() {
		assertThat(HtmlToText.convert("<div>  one   two </div><br><br><br><p>three</p>"))
				.isEqualTo("one two\n\nthree");
	}

	@Test
	void plainTextStaysAsItIs() {
		assertThat(HtmlToText.convert("Ihre Aufgaben:\n- Java")).isEqualTo("Ihre Aufgaben:\n- Java");
		assertThat(HtmlToText.convert(null)).isEmpty();
	}

}
