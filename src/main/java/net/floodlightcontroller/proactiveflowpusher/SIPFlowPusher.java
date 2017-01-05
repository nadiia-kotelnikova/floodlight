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
	
	// flow-mod - for use in the cookie
	public static final int LEARNING_SWITCH_APP_ID = 1;
	// LOOK! This should probably go in some class that encapsulates
	// the app cookie management
	public static final int APP_ID_BITS = 12;
	public static final int APP_ID_SHIFT = (64 - APP_ID_BITS);
	public static final long LEARNING_SWITCH_COOKIE = (long) (LEARNING_SWITCH_APP_ID & ((1 << APP_ID_BITS) - 1)) << APP_ID_SHIFT;

	// more flow-mod defaults
	protected static short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 0; // in seconds
	protected static short FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; // infinite
	protected static short FLOWMOD_PRIORITY = 101;

	// for managing our map sizes
	protected static final int MAX_MACS_PER_SWITCH  = 1000;

	// normally, setup reverse flow as well. Disable only for using cbench for comparison with NOX etc.
	protected static final boolean LEARNING_SWITCH_REVERSE_FLOW = true;

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
				.setCookie((U64.of(SIPFlowPusher.LEARNING_SWITCH_COOKIE)))
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
