package net.floodlightcontroller.proactiveflowpusher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;


import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
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


public class SIPFlowPusher implements IFloodlightModule, IOFSwitchListener {

	protected static Logger logger;
	protected IFloodlightProviderService floodlightProvider;
	protected IOFSwitchService switchService;
	

	// more flow-mod defaults
	public static final long SIP_FLOW_PUSHER_COOKIE = 0x5555;
	protected static short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 0; // in seconds
	protected static short FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; // infinite
	protected static short FLOWMOD_PRIORITY = 101;


	@Override
	public void switchAdded(DatapathId switchId) {

	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void switchActivated(DatapathId switchId) {
		IOFSwitch sw = switchService.getSwitch(switchId);
		OFFactory myFactory = sw.getOFFactory(); /* Use the factory version appropriate for the switch in question. */
		logger.info("Switch {} is connected;", switchId.toString());

		/*Creating Matches*/
		Match  udpDstMatch = myFactory.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
				.setExact(MatchField.UDP_DST, TransportPort.of(5060))
				.build();

		Match  udpSrcMatch = myFactory.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
	            .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
	            .setExact(MatchField.UDP_SRC, TransportPort.of(5060))
	            .build();
    
		/*Creating Actions*/
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		OFActionOutput action = myFactory.actions().buildOutput()
				.setMaxLen(0xffFFffFF)
				.setPort(OFPort.CONTROLLER)
				.build();
		actionList.add(action);
		
		/*Creating Instructions*/
		ArrayList<OFInstruction> instructionList = new ArrayList<OFInstruction>();
		OFInstructionApplyActions applyActions = myFactory.instructions().buildApplyActions()
				.setActions(actionList)
				.build();
		instructionList.add(applyActions);
    
		/*Creating FlowMod*/
		OFFlowAdd flowAdd = myFactory.buildFlowAdd()
				.setMatch(udpDstMatch)
				.setCookie((U64.of(SIPFlowPusher.SIP_FLOW_PUSHER_COOKIE)))
				.setIdleTimeout(SIPFlowPusher.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
				.setHardTimeout(SIPFlowPusher.FLOWMOD_DEFAULT_HARD_TIMEOUT)
				.setPriority(SIPFlowPusher.FLOWMOD_PRIORITY)
				.setBufferId(OFBufferId.NO_BUFFER)
				.setInstructions(instructionList)
				.build();
    
		OFFlowAdd flowAdd2 = flowAdd.createBuilder()
				.setMatch(udpSrcMatch)
				.build();
		
		sw.write(flowAdd);
		sw.write(flowAdd2);
		logger.info("SIP flows were pushed to the switch.");

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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = 
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IOFSwitchService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		logger = LoggerFactory.getLogger(SIPFlowPusher.class);
		
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		switchService.addOFSwitchListener(this);
	}

}
