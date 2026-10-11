package de.zbidi.jobtracker.posting;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC: postings are only written in bulk by the import job, so there is no JPA entity.
 */
@Repository
public class JobPostingRepository {

	private final JdbcClient jdbc;

	public JobPostingRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public boolean existsByUrl(String url) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM job_posting WHERE url = ?)")
				.param(url)
				.query(Boolean.class)
				.single();
	}

}
