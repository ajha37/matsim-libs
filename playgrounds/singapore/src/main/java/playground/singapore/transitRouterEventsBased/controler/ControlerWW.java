/* *********************************************************************** *
 * project: org.matsim.*
 * ControlerWW.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,  *
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

package playground.singapore.transitRouterEventsBased.controler;

import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.functions.CharyparNagelOpenTimesScoringFunctionFactory;

import playground.singapore.transitRouterEventsBased.TransitRouterWWImplFactory;
import playground.singapore.transitRouterEventsBased.WaitTimeStuckCalculator;


/**
 * A run Controler for a transit router that depends on the travel times and wait times
 * 
 * @author sergioo
 */

public class ControlerWW {

	public static void main(String[] args) {
		Controler controler = new Controler(ScenarioUtils.loadScenario(ConfigUtils.loadConfig(args[0])));
		controler.setOverwriteFiles(true);
		WaitTimeStuckCalculator waitTimeCalculator = new WaitTimeStuckCalculator(controler.getPopulation(), controler.getScenario().getTransitSchedule(), controler.getConfig());
		controler.getEvents().addHandler(waitTimeCalculator);
		TransitRouterWWImplFactory factory = new TransitRouterWWImplFactory(controler, waitTimeCalculator.getWaitTimes());
		controler.addControlerListener(factory);
		controler.setTransitRouterFactory(factory);
		controler.setScoringFunctionFactory(new CharyparNagelOpenTimesScoringFunctionFactory(controler.getConfig().planCalcScore(), controler.getScenario()));
		controler.run();
	}
	
}
