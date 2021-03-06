/*
 * *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
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
 * *********************************************************************** *
 */

package org.matsim.contrib.dvrp.vrpagent;

import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.schedule.Task.TaskType;
import org.matsim.core.api.internal.HasPersonId;

/**
 * @author Michal Maciejewski (michalm)
 */
public abstract class AbstractTaskEvent extends Event implements HasPersonId {
	public static final String ATTRIBUTE_DVRP_VEHICLE = "dvrpVehicle";
	public static final String ATTRIBUTE_TASK_TYPE = "taskType";
	public static final String ATTRIBUTE_TASK_INDEX = "taskIndex";

	private final Id<DvrpVehicle> dvrpVehicleId;
	private final Id<Person> driverId;
	private final TaskType taskType;
	private final int taskIndex;

	public AbstractTaskEvent(double time, Id<DvrpVehicle> dvrpVehicleId, TaskType taskType, int taskIndex) {
		super(time);
		this.dvrpVehicleId = dvrpVehicleId;
		this.driverId = Id.createPersonId(dvrpVehicleId);
		this.taskType = taskType;
		this.taskIndex = taskIndex;
	}

	public final Id<DvrpVehicle> getDvrpVehicleId() {
		return dvrpVehicleId;
	}

	public Id<Person> getPersonId() {
		return driverId;
	}

	public final TaskType getTaskType() {
		return taskType;
	}

	public final int getTaskIndex() {
		return taskIndex;
	}

	@Override
	public Map<String, String> getAttributes() {
		Map<String, String> attr = super.getAttributes();
		attr.put(ATTRIBUTE_DVRP_VEHICLE, dvrpVehicleId + "");
		attr.put(ATTRIBUTE_TASK_TYPE, taskType + "");
		attr.put(ATTRIBUTE_TASK_INDEX, taskIndex + "");
		return attr;
	}
}
