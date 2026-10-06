package de.zbidi.jobtracker.recruiter;

import java.util.Objects;

import de.zbidi.jobtracker.company.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "recruiter")
public class Recruiter {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false, length = 300)
	private String email;

	/** {@code null} for agency recruiters who work for several companies. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "company_id")
	private Company company;

	protected Recruiter() {
		// for JPA
	}

	public Recruiter(String name, String email, Company company) {
		this.name = Objects.requireNonNull(name, "name must not be null");
		this.email = Objects.requireNonNull(email, "email must not be null");
		this.company = company;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public Company getCompany() {
		return company;
	}

}
