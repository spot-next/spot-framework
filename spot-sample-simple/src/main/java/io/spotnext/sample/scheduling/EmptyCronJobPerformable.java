package io.spotnext.sample.scheduling;

import org.springframework.stereotype.Service;

import io.spotnext.core.infrastructure.scheduling.support.AbstractCronJobPerformable;
import io.spotnext.core.infrastructure.support.Logger;
import io.spotnext.itemtype.core.scheduling.AbstractCronJob;

@Service
public class EmptyCronJobPerformable extends AbstractCronJobPerformable<AbstractCronJob> {

	@Override
	public PerformResult perform(AbstractCronJob cronJob) {
		Logger.info("Empty cronjob triggered");

		abortIfRequested();

		return SUCCESS;
	}

}
