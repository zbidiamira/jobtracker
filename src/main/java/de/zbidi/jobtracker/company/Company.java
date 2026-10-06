package de.zbidi.jobtracker.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "company")
public class Company {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(length = 100)
	private String city;

	@Column(length = 300)
	private String website;

	protected Company() {
		// for JPA
	}

	public Company(String name, String city, String website) {
		this.name = name;
		this.city = city;
		this.website = website;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCity() {
		return city;
	}

	public String getWebsite() {
		return website;
	}

}
