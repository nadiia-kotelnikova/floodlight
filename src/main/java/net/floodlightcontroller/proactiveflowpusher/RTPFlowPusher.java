package net.floodlightcontroller.proactiveflowpusher;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetQueue;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.routing.Path;


public class RTPFlowPusher implements IFloodlightModule, IOFSwitchListener, IRTPFlowPusher{
	
	protected static Logger logger;
	protected IFloodlightProviderService floodlightProvider;
	protected IOFSwitchService switchService;

	//-----------------------------------------------------
	// more flow-mod defaults
	public static final long RTP_FLOW_PUSHER_COOKIE = 0x7777;
	protected static short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 10; // in seconds
	protected static short FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; // infinite
	protected static short FLOWMOD_PRIORITY = 101;
	//----------------------------------------------------
	@Override
	public void switchAdded(DatapathId switchId) {
		
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		// TODO Auto-generated method stub

	}
	
	@Override
	public void switchActivated(DatapathId switchId) {
		// TODO Auto-generated method stub
	
	}

	public synchronized void flowPusher(ArrayList<String> parameters, Path shortestPath){
		Integer nodeInd = 1;
		for (NodePortTuple node : shortestPath.getPath()){
			DatapathId nodeID = node.getNodeId();
			IOFSwitch sw = switchService.getSwitch(nodeID);
			OFPort nodePort = node.getPortId();
			
			OFFactory myFactory = OFFactories.getFactory(OFVersion.OF_13);
			//OFFactory myFactory = sw.getOFFactory(); 	// Use the factory version appropriate for the switch in question. 
			System.out.println(parameters.toString());
			if (nodeInd % 2 == 0){
				
				
				if (parameters.get(0).equals("audio")){
					logger.info("Flow was pushed to Switch {} Port {}", nodeID.toString(), nodePort.toString());
					Match forwardMatch = matcher(myFactory, parameters, "forward");
					OFFlowAdd forwardFlow = flowCreator(0, 4, nodePort, myFactory, forwardMatch);
					sw.write(forwardFlow);
				} else if (parameters.get(0) == "video"){
					Match forwardMatch = matcher(myFactory, parameters, "forward");
					OFFlowAdd forwardFlow = flowCreator(6, 6, nodePort, myFactory, forwardMatch);
					sw.write(forwardFlow);
				}
			}
			else{
				if (parameters.get(0).equals("audio")){
					logger.info("Flow was pushed to Switch {} Port {}", nodeID.toString(), nodePort.toString());
					Match backwardMatch = matcher(myFactory, parameters, "backward");
					OFFlowAdd backwardFlow = flowCreator(0, 4, nodePort, myFactory, backwardMatch);
					sw.write(backwardFlow);
				} else if (parameters.get(0) == "video"){
					Match backwardMatch = matcher(myFactory, parameters, "backward");
					OFFlowAdd backwardFlow = flowCreator(6, 6, nodePort, myFactory, backwardMatch);
					sw.write(backwardFlow);
				}
			}
			nodeInd ++;
			logger.info("RTP flows were pushed");
		}
	}
	
	/* Creating a Matcher for Forward and Backward directions
	 * Returns Match
	 */
	public Match matcher(OFFactory myFactory, ArrayList<String> list, String direction) {
		switch(direction){
		case "forward":
			Match  forward = myFactory.buildMatch()
			.setExact(MatchField.ETH_TYPE, EthType.IPv4)
			.setExact(MatchField.IPV4_DST, IPv4Address.of(list.get(4)))		//DST IP Address
			.setExact(MatchField.IPV4_SRC, IPv4Address.of(list.get(2)))		//SRC IP Address
			.setExact(MatchField.IP_PROTO, IpProtocol.UDP)						//UDP Protocol
			.setExact(MatchField.UDP_DST, TransportPort.of(Integer.parseInt(list.get(3))))		//DST Port
			.setExact(MatchField.UDP_SRC, TransportPort.of(Integer.parseInt(list.get(1))))		//SRC Port
			.build();
			return forward;
		case "backward":
			Match  backward = myFactory.buildMatch()
			.setExact(MatchField.ETH_TYPE, EthType.IPv4)
			.setExact(MatchField.IPV4_DST, IPv4Address.of(list.get(2)))
			.setExact(MatchField.IPV4_SRC, IPv4Address.of(list.get(4)))
			.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
			.setExact(MatchField.UDP_DST, TransportPort.of(Integer.parseInt(list.get(1))))
			.setExact(MatchField.UDP_SRC, TransportPort.of(Integer.parseInt(list.get(3))))
			.build();
			return backward;
		default:
			break;
		}
		return null;
	}
	
	
	/* Creating a Flow for specific Queue, Meter and OFPort
	 * Returns Flow
	 */
	public OFFlowAdd flowCreator(Integer queueID, Integer meterID, OFPort port, OFFactory factory, Match  match){
		ArrayList<OFAction> actionsList = new ArrayList<OFAction>();
		ArrayList<OFInstruction> instructionsList = new ArrayList<OFInstruction>();
		
		OFActionSetQueue setQueue = factory.actions().buildSetQueue()
		        .setQueueId(queueID)
		        .build();
		OFActionOutput setPort = factory.actions().buildOutput()
				.setPort(port)
				.setMaxLen(0xffFFffFF)
				.build();
		
		actionsList.add(setQueue);
		actionsList.add(setPort);
		
		OFInstructionApplyActions applyActions = factory.instructions().buildApplyActions()
				.setActions(actionsList)
				.build();
		OFInstructionMeter meter = factory.instructions().buildMeter()
			    .setMeterId(meterID)
			    .build();
		
		instructionsList.add(meter);
		instructionsList.add(applyActions);

		OFFlowAdd flow = factory.buildFlowAdd()
				.setMatch(match)
				.setCookie((U64.of(RTPFlowPusher.RTP_FLOW_PUSHER_COOKIE)))
				.setIdleTimeout(RTPFlowPusher.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
				.setHardTimeout(RTPFlowPusher.FLOWMOD_DEFAULT_HARD_TIMEOUT)
				.setPriority(RTPFlowPusher.FLOWMOD_PRIORITY)
				.setBufferId(OFBufferId.NO_BUFFER)
				.setInstructions(instructionsList)
				.build();
		return flow;
	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
		// TODO Auto-generated method stub

	}

	@Override
	public void switchChanged(DatapathId switchId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void switchDeactivated(DatapathId switchId) {
		// TODO Auto-generated method stub

	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = 
				new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(IRTPFlowPusher.class);
	    return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		 Map<Class<? extends IFloodlightService>, IFloodlightService> m = 
				 new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		 m.put(IRTPFlowPusher.class, this);
		  return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = 
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IOFSwitchService.class);
		//l.add(ISIPAnalyzer.class);
		return l;
	}
	
	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		//sipAnalyzer = context.getServiceImpl(ISIPAnalyzer.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		logger = LoggerFactory.getLogger(SIPFlowPusher.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		switchService.addOFSwitchListener(this);

	}

}
