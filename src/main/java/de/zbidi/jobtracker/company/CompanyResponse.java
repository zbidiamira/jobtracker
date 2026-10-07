package de.zbidi.jobtracker.company;

public record CompanyResponse(Long id, String name, String city, String website) {

	public static CompanyResponse from(Company company) {
		return new CompanyResponse(company.getId(), company.getName(), company.getCity(), company.getWebsite());
	}

}
