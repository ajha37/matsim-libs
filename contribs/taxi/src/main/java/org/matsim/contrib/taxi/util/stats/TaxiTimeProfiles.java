/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.contrib.taxi.util.stats;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.schedule.Schedule.ScheduleStatus;
import org.matsim.contrib.dvrp.schedule.ScheduleInquiry;
import org.matsim.contrib.dvrp.schedule.Task;
import org.matsim.contrib.taxi.passenger.TaxiRequest.TaxiRequestStatus;
import org.matsim.contrib.taxi.passenger.TaxiRequests;
import org.matsim.contrib.taxi.schedule.TaxiTaskType;
import org.matsim.contrib.util.timeprofile.TimeProfileCollector.ProfileCalculator;
import org.matsim.contrib.util.timeprofile.TimeProfiles;

public class TaxiTimeProfiles {
	public static ProfileCalculator createIdleVehicleCounter(final Fleet fleet, final ScheduleInquiry scheduleInquiry) {
		return TimeProfiles.createSingleValueCalculator("Idle",
				() -> fleet.getVehicles().values().stream().filter(scheduleInquiry::isIdle).count());
	}

	public static ProfileCalculator createCurrentTaxiTaskTypeCounter(final Fleet fleet) {
		String[] header = TimeProfiles.combineValuesIntoStrings(TaxiTaskType.values());
		return TimeProfiles.createProfileCalculator(header, () -> calculateTaxiTaskTypeCounts(fleet));
	}

	public static Long[] calculateTaxiTaskTypeCounts(Fleet fleet) {
		Map<Task.TaskType, Long> countsByType = fleet.getVehicles()
				.values()
				.stream()
				.map(DvrpVehicle::getSchedule)
				.filter(schedule -> schedule.getStatus() == ScheduleStatus.STARTED)
				.collect(groupingBy(schedule -> schedule.getCurrentTask().getTaskType(), counting()));

		return Arrays.stream(TaxiTaskType.values())
				.map(type -> countsByType.getOrDefault(type, 0L))
				.toArray(Long[]::new);
	}

	public static ProfileCalculator createRequestsWithStatusCounter(final Collection<? extends Request> requests,
			final TaxiRequestStatus requestStatus) {
		return TimeProfiles.createSingleValueCalculator(requestStatus.name(),
				() -> TaxiRequests.countRequestsWithStatus(requests.stream(), requestStatus));
	}
}
