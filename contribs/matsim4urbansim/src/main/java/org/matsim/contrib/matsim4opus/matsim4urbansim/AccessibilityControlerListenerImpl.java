package org.matsim.contrib.matsim4opus.matsim4urbansim;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.matsim4opus.config.ConfigurationUtils;
import org.matsim.contrib.matsim4opus.config.modules.AccessibilityConfigModule;
import org.matsim.contrib.matsim4opus.gis.SpatialGrid;
import org.matsim.contrib.matsim4opus.gis.Zone;
import org.matsim.contrib.matsim4opus.gis.ZoneLayer;
import org.matsim.contrib.matsim4opus.improvedpseudopt.PtMatrix;
import org.matsim.contrib.matsim4opus.interfaces.SpatialGridDataExchangeInterface;
import org.matsim.contrib.matsim4opus.utils.LeastCostPathTreeExtended;
import org.matsim.contrib.matsim4opus.utils.helperObjects.AggregateObject2NearestNode;
import org.matsim.contrib.matsim4opus.utils.helperObjects.Benchmark;
import org.matsim.contrib.matsim4opus.utils.helperObjects.Distances;
import org.matsim.contrib.matsim4opus.utils.misc.ProgressBar;
import org.matsim.contrib.matsim4opus.utils.network.NetworkUtil;
import org.matsim.core.api.experimental.facilities.ActivityFacility;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.facilities.ActivityFacilitiesImpl;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NetworkImpl;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.roadpricing.RoadPricingScheme;
import org.matsim.roadpricing.RoadPricingSchemeImpl;
import org.matsim.roadpricing.RoadPricingSchemeImpl.Cost;
import org.matsim.utils.LeastCostPathTree;

import com.vividsolutions.jts.geom.Point;

/**
 * improvements aug'12
 * - accessibility calculation of unified for cell- and zone-base approach
 * - large computing savings due reduction of "least cost path tree" execution:
 *   In a pre-processing step all nearest nodes of measuring points (origins) are determined. 
 *   The "least cost path tree" for measuring points with the same nearest node are now only executed once. 
 *   Only the cost calculations from the measuring point to the network is done individually.
 *   
 * improvements nov'12
 * - bug fixed aggregatedOpportunities method for compound cost factors like time and distance    
 * 
 * improvements jan'13
 * - added pt for accessibility calculation
 * 
 * improvements june'13
 * - take "main" (reference to matsim4urbansim) out
 * - aggregation of opportunities adjusted to handle facilities
 * - zones are taken out
 * 
 *     
 * @author thomas
 *
 */
public class AccessibilityControlerListenerImpl{
	
	static final Logger log = Logger.getLogger(AccessibilityControlerListenerImpl.class);
	
	public static final String FREESEED_FILENAME= "freeSpeedAccessibility_cellsize_";
	public static final String CAR_FILENAME 	= "carAccessibility_cellsize_";
	public static final String BIKE_FILENAME 	= "bikeAccessibility_cellsize_";
	public static final String WALK_FILENAME 	= "walkAccessibility_cellsize_";
	public static final String PT_FILENAME 		= "ptAccessibility_cellsize_";
	
	static int ZONE_BASED 	= 0;
	static int PARCEL_BASED 	= 1;
	
	// start points, measuring accessibility (cell based approach)
	ZoneLayer<Id> measuringPointsCell;
	// start points, measuring accessibility (zone based approach)
	ZoneLayer<Id> measuringPointsZone;
	// containing parcel coordinates for accessibility feedback
	ActivityFacilitiesImpl parcels; 
	// destinations, opportunities like jobs etc ...
	AggregateObject2NearestNode[] aggregatedFacilities;
	
	// storing the accessibility results
	SpatialGrid freeSpeedGrid;
	SpatialGrid carGrid;
	SpatialGrid bikeGrid;
	SpatialGrid walkGrid;
	SpatialGrid ptGrid;
	
	// storing pt matrix
	PtMatrix ptMatrix;
	
	ArrayList<SpatialGridDataExchangeInterface> spatialGridDataExchangeListenerList = null;
	
	// accessibility parameter
	boolean useRawSum	= false;
	double logitScaleParameter;
	double inverseOfLogitScaleParameter;
	double betaCarTT;		// in MATSim this is [utils/h]: cnScoringGroup.getTraveling_utils_hr() - cnScoringGroup.getPerforming_utils_hr() 
	double betaCarTTPower;
	double betaCarLnTT;
	double betaCarTD;		// in MATSim this is [utils/money * money/meter] = [utils/meter]: cnScoringGroup.getMarginalUtilityOfMoney() * cnScoringGroup.getMonetaryDistanceCostRateCar()
	double betaCarTDPower;
	double betaCarLnTD;
	double betaCarTMC;		// in MATSim this is [utils/money]: cnScoringGroup.getMarginalUtilityOfMoney()
	double betaCarTMCPower;
	double betaCarLnTMC;
	double betaBikeTT;	// in MATSim this is [utils/h]: cnScoringGroup.getTravelingBike_utils_hr() - cnScoringGroup.getPerforming_utils_hr()
	double betaBikeTTPower;
	double betaBikeLnTT;
	double betaBikeTD;	// in MATSim this is 0 !!! since getMonetaryDistanceCostRateBike doesn't exist: 
	double betaBikeTDPower;
	double betaBikeLnTD;
	double betaBikeTMC;	// in MATSim this is [utils/money]: cnScoringGroup.getMarginalUtilityOfMoney()
	double betaBikeTMCPower;
	double betaBikeLnTMC;
	double betaWalkTT;	// in MATSim this is [utils/h]: cnScoringGroup.getTravelingWalk_utils_hr() - cnScoringGroup.getPerforming_utils_hr()
	double betaWalkTTPower;
	double betaWalkLnTT;
	double betaWalkTD;	// in MATSim this is 0 !!! since getMonetaryDistanceCostRateWalk doesn't exist: 
	double betaWalkTDPower;
	double betaWalkLnTD;
	double betaWalkTMC;	// in MATSim this is [utils/money]: cnScoringGroup.getMarginalUtilityOfMoney()
	double betaWalkTMCPower;
	double betaWalkLnTMC;
	double betaPtTT;		// in MATSim this is [utils/h]: cnScoringGroup.getTraveling_utils_hr() - cnScoringGroup.getPerforming_utils_hr() 
	double betaPtTTPower;
	double betaPtLnTT;
	double betaPtTD;		// in MATSim this is [utils/money * money/meter] = [utils/meter]: cnScoringGroup.getMarginalUtilityOfMoney() * cnScoringGroup.getMonetaryDistanceCostRateCar()
	double betaPtTDPower;
	double betaPtLnTD;
	double betaPtTMC;		// in MATSim this is [utils/money]: cnScoringGroup.getMarginalUtilityOfMoney()
	double betaPtTMCPower;
	double betaPtLnTMC;
	
	double constCar;
	double constBike;
	double constWalk;
	double constPt;
	
//	boolean usingCarParameterFromMATSim;	// free speed and congested car
//	boolean usingBikeParameterFromMATSim;	// bicycle
//	boolean usingWalkParameterFromMATSim;	// traveling on foot
//	boolean usingPtParameterFromMATSim;	// public transport
	
	double VijCarTT, VijCarTTPower, VijCarLnTT, VijCarTD, VijCarTDPower, VijCarLnTD, VijCarTMC, VijCarTMCPower, VijCarLnTMC,
		   VijWalkTT, VijWalkTTPower, VijWalkLnTT, VijWalkTD, VijWalkTDPower, VijWalkLnTD, VijWalkTMC, VijWalkTMCPower, VijWalkLnTMC,
		   VijBikeTT, VijBikeTTPower, VijBikeLnTT, VijBikeTD, VijBikeTDPower, VijBikeLnTD, VijBikeTMC, VijBikeTMCPower, VijBikeLnTMC,
		   VijFreeTT, VijFreeTTPower, VijFreeLnTT, VijFreeTD, VijFreeTDPower, VijFreeLnTD, VijFreeTC, VijFreeTCPower, VijFreeLnTC,
		   VijPtTT, VijPtTTPower, VijPtLnTT, VijPtTD, VijPtTDPower, VijPtLnTD, VijPtTMC, VijPtTMCPower, VijPtLnTMC;
	
	double depatureTime;
	double bikeSpeedMeterPerHour = -1;
	double walkSpeedMeterPerHour = -1;
	Benchmark benchmark;
	
	RoadPricingSchemeImpl scheme;
	
	/**
	 * setting parameter for accessibility calculation
	 * @param scenario
	 */
	final void initAccessibilityParameter(Scenario scenario){
		
		AccessibilityConfigModule moduleAPCM = ConfigurationUtils.getAccessibilityParameterConfigModule(scenario);
		
		PlanCalcScoreConfigGroup planCalcScoreConfigGroup = scenario.getConfig().planCalcScore() ;
		
		useRawSum			= moduleAPCM.isUsingRawSumsWithoutLn();
		logitScaleParameter = planCalcScoreConfigGroup.getBrainExpBeta() ;
		inverseOfLogitScaleParameter = 1/(logitScaleParameter); // logitScaleParameter = same as brainExpBeta on 2-aug-12. kai
		walkSpeedMeterPerHour = scenario.getConfig().plansCalcRoute().getWalkSpeed() * 3600.;
		bikeSpeedMeterPerHour = scenario.getConfig().plansCalcRoute().getBikeSpeed() * 3600.; // should be something like 15000
		
//		usingCarParameterFromMATSim = moduleAPCM.isUsingCarParametersFromMATSim();
//		usingBikeParameterFromMATSim= moduleAPCM.isUsingBikeParametersFromMATSim();
//		usingWalkParameterFromMATSim= moduleAPCM.isUsingWalkParametersFromMATSim();
//		usingPtParameterFromMATSim	= moduleAPCM.isUsingPtParametersFromMATSim();
		
		betaCarTT 	   	= planCalcScoreConfigGroup.getPerforming_utils_hr() - planCalcScoreConfigGroup.getTraveling_utils_hr() ;
		betaCarTD		= planCalcScoreConfigGroup.getMarginalUtilityOfMoney() * planCalcScoreConfigGroup.getMonetaryDistanceCostRateCar();
		betaCarTMC		= - planCalcScoreConfigGroup.getMarginalUtilityOfMoney() ;
		
		betaBikeTT		= planCalcScoreConfigGroup.getTravelingBike_utils_hr() - planCalcScoreConfigGroup.getPerforming_utils_hr();
		betaBikeTD		= planCalcScoreConfigGroup.getMarginalUtlOfDistanceOther();
		betaBikeTMC		= - planCalcScoreConfigGroup.getMarginalUtilityOfMoney();
		
		betaWalkTT		= planCalcScoreConfigGroup.getTravelingWalk_utils_hr() - planCalcScoreConfigGroup.getPerforming_utils_hr();
		betaWalkTD		= planCalcScoreConfigGroup.getMarginalUtlOfDistanceWalk();
		betaWalkTMC		= - planCalcScoreConfigGroup.getMarginalUtilityOfMoney();
		
		betaPtTT		= planCalcScoreConfigGroup.getTravelingPt_utils_hr() - planCalcScoreConfigGroup.getPerforming_utils_hr();
		betaPtTD		= planCalcScoreConfigGroup.getMarginalUtilityOfMoney() * planCalcScoreConfigGroup.getMonetaryDistanceCostRatePt();
		betaPtTMC		= - planCalcScoreConfigGroup.getMarginalUtilityOfMoney() ;
		
		constCar		= scenario.getConfig().planCalcScore().getConstantCar();
		constBike		= scenario.getConfig().planCalcScore().getConstantBike();
		constWalk		= scenario.getConfig().planCalcScore().getConstantWalk();
		constPt			= scenario.getConfig().planCalcScore().getConstantPt();
		
		depatureTime 	= moduleAPCM.getTimeOfDay(); // by default = 8.*3600;	
		// printParameterSettings(); // use only for debugging since otherwise it clutters the logfile (settings are printed as part of config dump)
	}
	
	/**
	 * displays settings
	 */
	final void printParameterSettings(){
		log.info("Computing and writing grid based accessibility measures with following settings:" );
		log.info("Returning raw sum (not logsum): " + useRawSum);
		log.info("Logit Scale Parameter: " + logitScaleParameter);
		log.info("Inverse of logit Scale Parameter: " + inverseOfLogitScaleParameter);
		log.info("Walk speed (meter/h): " + this.walkSpeedMeterPerHour + " ("+this.walkSpeedMeterPerHour/3600. +" meter/s)");
		log.info("Bike speed (meter/h): " + this.bikeSpeedMeterPerHour + " ("+this.bikeSpeedMeterPerHour/3600. +" meter/s)");
//		log.info("Using Car (congested and free speed) Parameter from MATSim: " + usingCarParameterFromMATSim);
//		log.info("Using Bicycle Parameter from MATSim: " + usingBikeParameterFromMATSim);
//		log.info("Using Walk Parameter from MATSim: " + usingWalkParameterFromMATSim);
//		log.info("Using Pt Parameter from MATSim: " + usingPtParameterFromMATSim);
		log.info("Depature time (in seconds): " + depatureTime);
		log.info("Beta Car Travel Time: " + betaCarTT );
		log.info("Beta Car Travel Time Power2: " + betaCarTTPower );
		log.info("Beta Car Ln Travel Time: " + betaCarLnTT );
		log.info("Beta Car Travel Distance: " + betaCarTD );
		log.info("Beta Car Travel Distance Power2: " + betaCarTDPower );
		log.info("Beta Car Ln Travel Distance: " + betaCarLnTD );
		log.info("Beta Car Travel Monetary Cost: " + betaCarTMC );
		log.info("Beta Car Travel Monetary Cost Power2: " + betaCarTMCPower );
		log.info("Beta Car Ln Travel Monetary Cost: " + betaCarLnTMC );
		log.info("Beta Bike Travel Time: " + betaBikeTT );
		log.info("Beta Bike Travel Time Power2: " + betaBikeTTPower );
		log.info("Beta Bike Ln Travel Time: " + betaBikeLnTT );
		log.info("Beta Bike Travel Distance: " + betaBikeTD );
		log.info("Beta Bike Travel Distance Power2: " + betaBikeTDPower );
		log.info("Beta Bike Ln Travel Distance: " + betaBikeLnTD );
		log.info("Beta Bike Travel Monetary Cost: " + betaBikeTMC );
		log.info("Beta Bike Travel Monetary Cost Power2: " + betaBikeTMCPower );
		log.info("Beta Bike Ln Travel Monetary Cost: " + betaBikeLnTMC );
		log.info("Beta Walk Travel Time: " + betaWalkTT );
		log.info("Beta Walk Travel Time Power2: " + betaWalkTTPower );
		log.info("Beta Walk Ln Travel Time: " + betaWalkLnTT );
		log.info("Beta Walk Travel Distance: " + betaWalkTD );
		log.info("Beta Walk Travel Distance Power2: " + betaWalkTDPower );
		log.info("Beta Walk Ln Travel Distance: " + betaWalkLnTD );
		log.info("Beta Walk Travel Monetary Cost: " + betaWalkTMC );
		log.info("Beta Walk Travel Monetary Cost Power2: " + betaWalkTMCPower );
		log.info("Beta Walk Ln Travel Monetary Cost: " + betaWalkLnTMC );
	}
	
	/**
	 * This aggregates the disjutilities Vjk to get from node j to all k that are attached to j.
	 * Finally the sum(Vjk) is assigned to node j, which is done in this method.
	 * 
	 *     j---k1 
	 *     |\
	 *     | \
	 *     k2 k3
	 * 
	 * @param opportunities such as workplaces, either given at a parcel- or zone-level
	 * @param network giving the road network
	 * @return the sum of disutilities Vjk, i.e. the disutilities to reach all opportunities k that are assigned to j from node j 
	 */
	final AggregateObject2NearestNode[] aggregatedOpportunities(final ActivityFacilitiesImpl opportunities, NetworkImpl network){
	
		log.info("Aggregating " + opportunities.getFacilities().size() + " opportunities with identical nearest node ...");
		Map<Id, AggregateObject2NearestNode> opportunityClusterMap = new ConcurrentHashMap<Id, AggregateObject2NearestNode>();
		ProgressBar bar = new ProgressBar( opportunities.getFacilities().size() );
	
		Iterator<ActivityFacility> oppIterator = opportunities.getFacilities().values().iterator();
		
		while(oppIterator.hasNext()){
			
			bar.update();
			
			ActivityFacility opprotunity = oppIterator.next();
			Node nearestNode = network.getNearestNode( opprotunity.getCoord() );
			
			// get Euclidian distance to nearest node
			double distance_meter 	= NetworkUtil.getEuclidianDistance(opprotunity.getCoord(), nearestNode.getCoord());
			double walkTravelTime_h = distance_meter / this.walkSpeedMeterPerHour;
			
			double VjkWalkTravelTime	= this.betaWalkTT * walkTravelTime_h;
			double VjkWalkPowerTravelTime=0.; // this.betaWalkTTPower * (walkTravelTime_h * walkTravelTime_h);
			double VjkWalkLnTravelTime	= 0.; // this.betaWalkLnTT * Math.log(walkTravelTime_h);
			
			double VjkWalkDistance 		= this.betaWalkTD * distance_meter;
			double VjkWalkPowerDistnace	= 0.; //this.betaWalkTDPower * (distance_meter * distance_meter);
			double VjkWalkLnDistance 	= 0.; //this.betaWalkLnTD * Math.log(distance_meter);
			
			double VjkWalkMoney			= this.betaWalkTMC * 0.; 			// no monetary costs for walking
			double VjkWalkPowerMoney	= 0.; //this.betaWalkTDPower * 0.; 	// no monetary costs for walking
			double VjkWalkLnMoney		= 0.; //this.betaWalkLnTMC *0.; 	// no monetary costs for walking
			
			double Vjk					= Math.exp(this.logitScaleParameter * (VjkWalkTravelTime + VjkWalkPowerTravelTime + VjkWalkLnTravelTime +
					   														   VjkWalkDistance   + VjkWalkPowerDistnace   + VjkWalkLnDistance +
					   														   VjkWalkMoney      + VjkWalkPowerMoney      + VjkWalkLnMoney) );
			// add Vjk to sum
			if( opportunityClusterMap.containsKey( nearestNode.getId() ) ){
				AggregateObject2NearestNode jco = opportunityClusterMap.get( nearestNode.getId() );
				jco.addObject( opprotunity.getId(), Vjk);
			}
			else // assign Vjk to given network node
				opportunityClusterMap.put(
						nearestNode.getId(),
						new AggregateObject2NearestNode(opprotunity.getId(), 
														null,
														null,
														nearestNode.getCoord(), 
														nearestNode, 
														Vjk));
		}
		// convert map to array
		AggregateObject2NearestNode jobClusterArray []  = new AggregateObject2NearestNode[ opportunityClusterMap.size() ];
		Iterator<AggregateObject2NearestNode> jobClusterIterator = opportunityClusterMap.values().iterator();

		for(int i = 0; jobClusterIterator.hasNext(); i++)
			jobClusterArray[i] = jobClusterIterator.next();
		
		log.info("Aggregated " + opportunities.getFacilities().size() + " number of opportunities to " + jobClusterArray.length + " nodes.");
		
		return jobClusterArray;
	}
	
	/**
	 * @param ttc
	 * @param lcptFreeSpeedCarTravelTime
	 * @param lcptCongestedCarTravelTime
	 * @param lcptTravelDistance
	 * @param network
	 * @param inverseOfLogitScaleParameter
	 * @param accCsvWriter
	 * @param measuringPointIterator
	 */
	final void accessibilityComputation(TravelTime ttc,
											LeastCostPathTreeExtended lcptExtFreeSpeedCarTravelTime,
											LeastCostPathTreeExtended lcptExtCongestedCarTravelTime,
											LeastCostPathTree lcptTravelDistance, 
											PtMatrix ptMatrix,
											NetworkImpl network,
											Iterator<Zone<Id>> measuringPointIterator,
											int numberOfMeasuringPoints, 
											int mode,
											Controler contorler) {

//		grids = new double[numberOfMeasuringPoints][4];
		
		GeneralizedCostSum gcs = new GeneralizedCostSum();

		// this data structure condense measuring points (origins) that have the same nearest node on the network ...
		Map<Id,ArrayList<Zone<Id>>> aggregatedMeasurementPoints = new ConcurrentHashMap<Id, ArrayList<Zone<Id>>>();

		// go through all measuring points ...
		while( measuringPointIterator.hasNext() ){

			Zone<Id> measurePoint = measuringPointIterator.next();
			Point point = measurePoint.getGeometry().getCentroid();
			// get coordinate from origin (start point)
			Coord coordFromZone = new CoordImpl( point.getX(), point.getY());
			// captures the distance (as walk time) between a cell centroid and the road network
			Link nearestLink = network.getNearestLinkExactly(coordFromZone);
			// determine nearest network node (from- or toNode) based on the link 
			Node fromNode = NetworkUtil.getNearestNode(coordFromZone, nearestLink);
			
			// this is used as a key for hash map lookups
			Id id = fromNode.getId();
			
			// create new entry if key does not exist!
			if(!aggregatedMeasurementPoints.containsKey(id))
				aggregatedMeasurementPoints.put(id, new ArrayList<Zone<Id>>());
			// assign measure point (origin) to it's nearest node
			aggregatedMeasurementPoints.get(id).add(measurePoint);
		}
		
		log.info("");
		log.info("Number of measure points: " + numberOfMeasuringPoints);
		log.info("Number of aggregated measure points: " + aggregatedMeasurementPoints.size());
		log.info("");
		

		ProgressBar bar = new ProgressBar( aggregatedMeasurementPoints.size() );
		
		// contains all nodes that have a measuring point (origin) assigned
		Iterator<Id> keyIterator = aggregatedMeasurementPoints.keySet().iterator();
		// contains all network nodes
		Map<Id, Node> networkNodesMap = network.getNodes();
		
		// go through all nodes (key's) that have a measuring point (origin) assigned
		while( keyIterator.hasNext() ){
			
			bar.update();
			
			Id nodeId = keyIterator.next();
			Node fromNode = networkNodesMap.get( nodeId );
			
			// run dijkstra on network
			// this is done once for all origins in the "origins" list, see below
			lcptExtFreeSpeedCarTravelTime.calculateExtended(network, fromNode, depatureTime);
			lcptExtCongestedCarTravelTime.calculateExtended(network, fromNode, depatureTime);		
			lcptTravelDistance.calculate(network, fromNode, depatureTime);
			
			// get list with origins that are assigned to "fromNode"
			ArrayList<Zone<Id>> origins = aggregatedMeasurementPoints.get( nodeId );
			Iterator<Zone<Id>> originsIterator = origins.iterator();
			
			while( originsIterator.hasNext() ){
				
				Zone<Id> measurePoint = originsIterator.next();
				Point point = measurePoint.getGeometry().getCentroid();
				// get coordinate from origin (start point)
				Coord coordFromZone = new CoordImpl( point.getX(), point.getY());
				assert( coordFromZone!=null );
				// captures the distance (as walk time) between a cell centroid and the road network
				LinkImpl nearestLink = (LinkImpl)network.getNearestLinkExactly(coordFromZone);
				
				// captures the distance (as walk time) between a zone centroid and its nearest node
				Distances distance = NetworkUtil.getDistance2Node(nearestLink, point, fromNode);
				
				double distanceMeasuringPoint2Road_meter 	= distance.getDistancePoint2Road(); // distance measuring point 2 road (link or node)
				double distanceRoad2Node_meter 				= distance.getDistanceRoad2Node();	// distance intersection 2 node (only for orthogonal distance), this is zero if projection is on a node 
				
				// traveling on foot from measuring point to the network (link or node)
				double walkTravelTimeMeasuringPoint2Road_h 	= distanceMeasuringPoint2Road_meter / this.walkSpeedMeterPerHour;

				// get free speed and congested car travel times on a certain link
				double freeSpeedTravelTimeOnNearestLink_meterpersec = nearestLink.getFreespeedTravelTime(depatureTime);
				double carTravelTimeOnNearestLink_meterpersec= nearestLink.getLength() / ttc.getLinkTravelTime(nearestLink, depatureTime, null, null);
				// travel time in hours to get from link intersection (position on a link given by orthogonal projection from measuring point) to the corresponding node
				double road2NodeFreeSpeedTime_h				= distanceRoad2Node_meter / (freeSpeedTravelTimeOnNearestLink_meterpersec * 3600);
				double road2NodeCongestedCarTime_h 			= distanceRoad2Node_meter / (carTravelTimeOnNearestLink_meterpersec * 3600.);
				double road2NodeBikeTime_h					= distanceRoad2Node_meter / this.bikeSpeedMeterPerHour;
				double road2NodeWalkTime_h					= distanceRoad2Node_meter / this.walkSpeedMeterPerHour;
				double road2NodeToll_money 					= getToll(nearestLink); // tnicolai: add this to car disutility ??? depends on the road pricing scheme ...
				
				// this contains the current toll based on the toll scheme
				double toll_money 							= 0.;
				if(this.scheme != null && RoadPricingScheme.TOLL_TYPE_CORDON.equals(this.scheme.getType()))
					toll_money = road2NodeToll_money;
				else if(this.scheme != null && RoadPricingScheme.TOLL_TYPE_DISTANCE.equals(this.scheme.getType()))
					toll_money = road2NodeToll_money * distanceRoad2Node_meter;
				
				gcs.reset();

				// goes through all opportunities, e.g. jobs, (nearest network node) and calculate the accessibility
				for ( int i = 0; i < this.aggregatedFacilities.length; i++ ) {
					
					// get stored network node (this is the nearest node next to an aggregated work place)
					Node destinationNode = this.aggregatedFacilities[i].getNearestNode();
					Id nodeID = destinationNode.getId();
					
					// disutilities on the road network
					double congestedCarDisutility = - lcptExtCongestedCarTravelTime.getTree().get( nodeID ).getCost();	// travel disutility congested car on road network (including toll)
					double freeSpeedCarDisutility = - lcptExtFreeSpeedCarTravelTime.getTree().get( nodeID ).getCost();	// travel disutility free speed car on road network (including toll)
					double travelDistance_meter = lcptTravelDistance.getTree().get( nodeID ).getCost(); 				// travel link distances on road network for bicycle and walk

					// travel times and distances for pseudo pt
					double ptTravelTime_h		= Double.MAX_VALUE;	// travel time with pt
					double ptTotalWalkTime_h	= Double.MAX_VALUE;	// total walking time including (i) to get to pt stop and (ii) to get from destination pt stop to destination location
					double ptTravelDistance_meter=Double.MAX_VALUE; // total travel distance including walking and pt distance from/to origin/destination location
					double ptTotalWalkDistance_meter=Double.MAX_VALUE;// total walk distance  including (i) to get to pt stop and (ii) to get from destination pt stop to destination location
					if(ptMatrix != null){
						ptTravelTime_h 			= ptMatrix.getPtTravelTime_seconds(fromNode.getCoord(), destinationNode.getCoord()) / 3600.;
						ptTotalWalkTime_h		= ptMatrix.getTotalWalkTravelTime_seconds(fromNode.getCoord(), destinationNode.getCoord()) / 3600.;
						
						ptTotalWalkDistance_meter=ptMatrix.getTotalWalkTravelDistance_meter(fromNode.getCoord(), destinationNode.getCoord());
						ptTravelDistance_meter  = ptMatrix.getPtTravelDistance_meter(fromNode.getCoord(), destinationNode.getCoord());
					}
					double ptDisutility = constPt + (ptTotalWalkTime_h * betaWalkTT) + (ptTravelTime_h * betaPtTT) + (ptTotalWalkDistance_meter * betaWalkTD) + (ptTravelDistance_meter * betaPtTD);
					
					// disutilities to get on or off the network
					double walkDisutilityMeasuringPoint2Road = (walkTravelTimeMeasuringPoint2Road_h * betaWalkTT) + (distanceMeasuringPoint2Road_meter * betaWalkTD);
					double expVhiWalk = Math.exp(this.logitScaleParameter * walkDisutilityMeasuringPoint2Road);
					double sumExpVjkWalk = aggregatedFacilities[i].getSumVjk();
					
					// total disutility congested car
					double congestedCarDisutilityRoad2Node = (road2NodeCongestedCarTime_h * betaCarTT) + (distanceRoad2Node_meter * betaCarTD) + (toll_money * betaCarTMC); 
					double expVijCongestedCar = Math.exp(this.logitScaleParameter * (constCar + congestedCarDisutilityRoad2Node + congestedCarDisutility) );
					double expVhkCongestedCar = expVhiWalk * expVijCongestedCar * sumExpVjkWalk;
					gcs.addCongestedCarCost( expVhkCongestedCar );
					
					// total disutility free speed car
					double freeSpeedCarDisutilityRoad2Node = (road2NodeFreeSpeedTime_h * betaCarTT) + (distanceRoad2Node_meter * betaCarTD) + (toll_money * betaCarTMC); 
					double expVijFreeSpeedCar = Math.exp(this.logitScaleParameter * (constCar + freeSpeedCarDisutilityRoad2Node + freeSpeedCarDisutility) );
					double expVhkFreeSpeedCar = expVhiWalk * expVijFreeSpeedCar * sumExpVjkWalk;
					gcs.addFreeSpeedCost( expVhkFreeSpeedCar );
					
					// total disutility bicycle
					double bikeDisutilityRoad2Node = (road2NodeBikeTime_h * betaBikeTT) + (distanceRoad2Node_meter * betaBikeTD); // toll or money ???
					double bikeDisutility = ((travelDistance_meter/this.bikeSpeedMeterPerHour) * betaBikeTT) + (travelDistance_meter * betaBikeTD);// toll or money ???
					double expVijBike = Math.exp(this.logitScaleParameter * (constBike + bikeDisutility + bikeDisutilityRoad2Node));
					double expVhkBike = expVhiWalk * expVijBike * sumExpVjkWalk;
					gcs.addBikeCost( expVhkBike );
					
					// total disutility walk
					double walkDisutilityRoad2Node = (road2NodeWalkTime_h * betaWalkTT) + (distanceRoad2Node_meter * betaWalkTD);  // toll or money ???
					double walkDisutility = ( (travelDistance_meter / this.walkSpeedMeterPerHour) * betaWalkTT) + ( travelDistance_meter * betaWalkTD);// toll or money ???
					double expVijWalk = Math.exp(this.logitScaleParameter * (constWalk + walkDisutility + walkDisutilityRoad2Node));
					double expVhkWalk = expVhiWalk * expVijWalk * sumExpVjkWalk;
					gcs.addWalkCost( expVhkWalk );
					
					double expVijPt = Math.exp(this.logitScaleParameter * ptDisutility);
					double expVhkPt = expVijPt * sumExpVjkWalk;
					gcs.addPtCost( expVhkPt );
				}
				
				// aggregated value
				double freeSpeedAccessibility, carAccessibility, bikeAccessibility, walkAccessibility, ptAccessibility;
				if(!useRawSum){ 	// get log sum
					freeSpeedAccessibility = inverseOfLogitScaleParameter * Math.log( gcs.getFreeSpeedSum() );
					carAccessibility = inverseOfLogitScaleParameter * Math.log( gcs.getCarSum() );
					bikeAccessibility= inverseOfLogitScaleParameter * Math.log( gcs.getBikeSum() );
					walkAccessibility= inverseOfLogitScaleParameter * Math.log( gcs.getWalkSum() );
					ptAccessibility	 = inverseOfLogitScaleParameter * Math.log( gcs.getPtSum() );
				}
				else{ 				// get raw sum
					freeSpeedAccessibility = inverseOfLogitScaleParameter * gcs.getFreeSpeedSum();
					carAccessibility = inverseOfLogitScaleParameter * gcs.getCarSum();
					bikeAccessibility= inverseOfLogitScaleParameter * gcs.getBikeSum();
					walkAccessibility= inverseOfLogitScaleParameter * gcs.getWalkSum();
					ptAccessibility  = inverseOfLogitScaleParameter * gcs.getPtSum();
				}

				if(mode == PARCEL_BASED){ // only for cell-based accessibility computation
					// assign log sums to current starZone object and spatial grid
					freeSpeedGrid.setValue(freeSpeedAccessibility, measurePoint.getGeometry().getCentroid());
					carGrid.setValue(carAccessibility , measurePoint.getGeometry().getCentroid());
					bikeGrid.setValue(bikeAccessibility , measurePoint.getGeometry().getCentroid());
					walkGrid.setValue(walkAccessibility , measurePoint.getGeometry().getCentroid());
					ptGrid.setValue(ptAccessibility, measurePoint.getGeometry().getCentroid());
				}
				
//				grids[Integer.parseInt(measurePoint.getAttribute().toString())][0] = freeSpeedAccessibility;
//				grids[Integer.parseInt(measurePoint.getAttribute().toString())][1] = carAccessibility;
//				grids[Integer.parseInt(measurePoint.getAttribute().toString())][2] = bikeAccessibility;
//				grids[Integer.parseInt(measurePoint.getAttribute().toString())][3] = walkAccessibility;
				
				writeCSVData(measurePoint, coordFromZone, fromNode, 
						freeSpeedAccessibility, carAccessibility,
						bikeAccessibility, walkAccessibility, ptAccessibility);
			}
		}
	}

	/**
	 * @param nearestLink
	 */
	double getToll(Link nearestLink) {
		if(scheme != null){
			Cost cost = scheme.getLinkCostInfo(nearestLink.getId(), depatureTime, null);
			if(cost != null)
				return cost.amount;
		}
		return 0.;
	}
	
	/**
	 * This adds listeners to write out accessibility results for parcels in UrbanSim format
	 * @param l
	 */
	public void addSpatialGridDataExchangeListener(SpatialGridDataExchangeInterface l){
		if(this.spatialGridDataExchangeListenerList == null)
			this.spatialGridDataExchangeListenerList = new ArrayList<SpatialGridDataExchangeInterface>();
		
		log.info("Adding new SpatialGridDataExchange listener...");
		this.spatialGridDataExchangeListenerList.add(l);
		log.info("... done!");
	}

	/**
	 * Writes measured accessibilities as csv format to disc
	 * 
	 * @param measurePoint
	 * @param coordFromZone
	 * @param fromNode
	 * @param freeSpeedAccessibility
	 * @param carAccessibility
	 * @param bikeAccessibility
	 * @param walkAccessibility
	 */
	void writeCSVData(
			Zone<Id> measurePoint, Coord coordFromZone,
			Node fromNode, double freeSpeedAccessibility,
			double carAccessibility, double bikeAccessibility,
			double walkAccessibility, double ptAccessibility) {
		// this is just a stub and does nothing. 
		// this needs to be implemented/overwritten by an inherited class
	}
	
	// ////////////////////////////////////////////////////////////////////
	// inner classes
	// ////////////////////////////////////////////////////////////////////
	
	
	/**
	 * stores travel disutilities for different modes
	 * @author thomas
	 *
	 */
	public static class GeneralizedCostSum {
		
		private double sumFREESPEED = 0.;
		private double sumCAR  	= 0.;
		private double sumBIKE 	= 0.;
		private double sumWALK 	= 0.;
		private double sumPt   	= 0.;
		
		public void reset() {
			this.sumFREESPEED 	= 0.;
			this.sumCAR		  	= 0.;
			this.sumBIKE	  	= 0.;
			this.sumWALK	  	= 0.;
			this.sumPt		  	= 0.;
		}
		
		public void addFreeSpeedCost(double cost){
			this.sumFREESPEED += cost;
		}
		
		public void addCongestedCarCost(double cost){
			this.sumCAR += cost;
		}
		
		public void addBikeCost(double cost){
			this.sumBIKE += cost;
		}
		
		public void addWalkCost(double cost){
			this.sumWALK += cost;
		}
		
		public void addPtCost(double cost){
			this.sumPt += cost;
		}
		
		public double getFreeSpeedSum(){
			return this.sumFREESPEED;
		}
		
		public double getCarSum(){
			return this.sumCAR;
		}
		
		public double getBikeSum(){
			return this.sumBIKE;
		}
		
		public double getWalkSum(){
			return this.sumWALK;
		}
		
		public double getPtSum(){
			return this.sumPt;
		}
	}

}
