package com.freirelts.araripe_invest_api.adapters.inbound.rest.legal;

import com.freirelts.araripe_invest_api.application.legal.LegalTermsService;
import com.freirelts.araripe_invest_api.application.legal.LegalTermsService.LegalTerms;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/legal")
class LegalTermsController {

	private final LegalTermsService legalTermsService;

	LegalTermsController(LegalTermsService legalTermsService) {
		this.legalTermsService = legalTermsService;
	}

	@GetMapping("/terms/current")
	LegalTerms currentTerms() {
		return legalTermsService.currentTerms();
	}
}
