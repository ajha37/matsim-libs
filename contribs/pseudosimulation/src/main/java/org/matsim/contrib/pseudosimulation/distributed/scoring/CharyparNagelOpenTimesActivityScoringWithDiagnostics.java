/* *********************************************************************** *
 * project: org.matsim.*
 * CharyparNagelOpenTimesScoringFunction.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package org.matsim.contrib.pseudosimulation.distributed.scoring;

import java.util.Iterator;
import java.util.Set;

import org.matsim.api.core.v01.population.Activity;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.OpeningTime;

/**
 * Same as CharyparNagelScoringFunction, but retrieves opening time information
 * from facility object of an activity instead of the config file.
 *
 * @author meisterk
 *
 */
public class CharyparNagelOpenTimesActivityScoringWithDiagnostics extends org.matsim.deprecated.scoring.functions.CharyparNagelActivityScoring {

	private final ActivityFacilities facilities;

	public CharyparNagelOpenTimesActivityScoringWithDiagnostics(final ScoringParameters params, final ActivityFacilities facilities) {
		super(params);
		this.facilities = facilities;
	}

	@Override
	protected OptionalTime[] getOpeningInterval(Activity act) {

		// openInterval has two values
		// openInterval[0] will be the opening time
		// openInterval[1] will be the closing time
		OptionalTime[] openInterval = {OptionalTime.undefined(), OptionalTime.undefined()};

		boolean foundAct = false;

		ActivityFacility facility = this.facilities.getFacilities().get(act.getFacilityId());
		Iterator<String> facilityActTypeIterator = facility.getActivityOptions().keySet().iterator();
		String facilityActType = null;
		Set<OpeningTime> opentimes = null;

		while (facilityActTypeIterator.hasNext() && !foundAct) {

			facilityActType = facilityActTypeIterator.next();
			if (act.getType().equals(facilityActType)) {
				foundAct = true;

				// choose appropriate opentime:
				// either wed or wkday
				// if none is given, use undefined opentimes
				opentimes = facility.getActivityOptions().get(facilityActType).getOpeningTimes();
				if (opentimes != null && opentimes.size()>0) {
					// ignoring lunch breaks with the following procedure:
					// if there is only one wed/wkday open time interval, use it
					// if there are two or more, use the earliest start time and the latest end time
					double opening = Double.MAX_VALUE;
					double closing = Double.MIN_VALUE;

					for (OpeningTime opentime : opentimes) {
						opening = Math.min(opening, opentime.getStartTime());
						closing = Math.max(closing, opentime.getEndTime());
					}

					openInterval[0] = OptionalTime.defined(opening);
					openInterval[1] = OptionalTime.defined(closing);
				}

			}

		}

		if (!foundAct) {
			return new OptionalTime[]{OptionalTime.defined(0), OptionalTime.defined(Double.MAX_VALUE)};
		}

		return openInterval;

	}

    @Override
    public void handleActivity(Activity act) {
        getScore();
        super.handleActivity(act);
    }
}
