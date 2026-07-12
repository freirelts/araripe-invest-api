package com.freirelts.araripe_invest_api.infrastructure.jobs;

import com.freirelts.araripe_invest_api.application.jobs.OperationalJobService;
import com.freirelts.araripe_invest_api.domain.jobs.JobName;
import com.freirelts.araripe_invest_api.domain.jobs.JobRunTrigger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@ConditionalOnProperty(name = "araripe.jobs.daily-flow.enabled", havingValue = "true", matchIfMissing = true)
class DailyOperationalFlowScheduler {

	private final OperationalJobService operationalJobService;

	DailyOperationalFlowScheduler(OperationalJobService operationalJobService) {
		this.operationalJobService = operationalJobService;
	}

	@Scheduled(cron = "${araripe.jobs.daily-flow.cron:0 30 18 * * MON-FRI}",
			zone = "${araripe.jobs.daily-flow.zone:America/Sao_Paulo}")
	void runDailyOperationalFlow() {
		operationalJobService.execute(JobName.DAILY_OPERATIONAL_FLOW, LocalDate.now(), JobRunTrigger.SCHEDULED, null);
	}
}
