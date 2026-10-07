package de.zbidi.jobtracker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Test clock whose time can be set and moved forward, so audit timestamps are predictable.
 */
public class MutableClock extends Clock {

	private volatile Instant instant;
	private final ZoneId zone;

	public MutableClock(Instant instant, ZoneId zone) {
		this.instant = instant;
		this.zone = zone;
	}

	public void setInstant(Instant instant) {
		this.instant = instant;
	}

	public void advance(Duration duration) {
		this.instant = instant.plus(duration);
	}

	@Override
	public Instant instant() {
		return instant;
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(instant, zone);
	}

}
