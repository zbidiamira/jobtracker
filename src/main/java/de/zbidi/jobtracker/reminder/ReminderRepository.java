package de.zbidi.jobtracker.reminder;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC on job_application on purpose: setting reminded_at must not go through the JPA entity, which would
 * bump {@code @Version} (the user's next status change would get a 409) and {@code @LastModifiedDate} (the
 * application would look changed).
 */
@Repository
public class ReminderRepository {

	/** An application that is due for a reminder. */
	public record DueApplication(long applicationId, long ownerId, String position, Instant lastChangedAt) {
	}

	private final JdbcClient jdbc;

	public ReminderRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** APPLIED, not reminded yet and unchanged since {@code cutoff} (index idx_job_application_reminder_due). */
	public List<DueApplication> findDue(Instant cutoff) {
		return jdbc.sql("""
						SELECT id, owner_id, position, updated_at
						FROM job_application
						WHERE status = 'APPLIED' AND reminded_at IS NULL AND updated_at < ?
						ORDER BY id
						""")
				.param(Timestamp.from(cutoff))
				.query((rs, row) -> new DueApplication(rs.getLong("id"), rs.getLong("owner_id"),
						rs.getString("position"), rs.getTimestamp("updated_at").toInstant()))
				.list();
	}

	/**
	 * @return true if this call set reminded_at; false if it was already set (another run or instance was faster),
	 * so each reminder is sent once
	 */
	public boolean markReminded(long applicationId, Instant remindedAt) {
		return jdbc.sql("UPDATE job_application SET reminded_at = ? WHERE id = ? AND reminded_at IS NULL")
				.param(Timestamp.from(remindedAt))
				.param(applicationId)
				.update() == 1;
	}

}
