/* *********************************************************************** *
 * project: org.matsim.*
 * RunEmissionToolOnline.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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
package org.matsim.contrib.emissions.example;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;


/**
 * @author benjamin
 *
 */
public class RunEmissionToolOnlineExample {
	
	private static String configFile = "./output/config.xml";
	
	
	public static void main(String[] args) {
		
		Config config = ConfigUtils.loadConfig(configFile, new EmissionsConfigGroup());
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Controler controler = new Controler(scenario);
		
	// controler settings	
		controler.setOverwriteFiles(true);
		controler.setCreateGraphs(false);		
		controler.addControlerListener(new EmissionControlerListener());
		controler.run();
	}
}