package net.floodlightcontroller.proactiveflowpusher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;


import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
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
import net.floodlightcontroller.packet.UDP;


public class test implements IFloodlightModule, IOFSwitchListener {

	protected static Logger logger;
	protected IFloodlightProviderService floodlightProvider;
	protected IOFSwitchService switchService;
	
	// flow-mod - for use in the cookie
	public static final int LEARNING_SWITCH_APP_ID = 1;
	// LOOK! This should probably go in some class that encapsulates
	// the app cookie management
	public static final int APP_ID_BITS = 12;
	public static final int APP_ID_SHIFT = (64 - APP_ID_BITS);
	public static final long COOKIE = 0x2016;

	// more flow-mod defaults
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
		if (switchId.equals(DatapathId.of("1c:48:cc:37:ab:fd:1a:c1"))){

			IOFSwitch sw = switchService.getSwitch(switchId);
			OFFactory factory = sw.getOFFactory(); /* Use the factory version appropriate for the switch in question. */
			logger.info("Switch {} is connected;", switchId.toString());

			/*Creating Matches*/
			Match  udpDstMatch = factory.buildMatch()
					.setExact(MatchField.IN_PORT, OFPort.of(6))
					.build();
			
			Match  udpSrcMatch = factory.buildMatch()
					.setExact(MatchField.IN_PORT, OFPort.of(5))
					.build();
			
			Match  udpDstMatch1 = factory.buildMatch()
					.setExact(MatchField.ETH_TYPE, EthType.IPv4)
					//.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
					.setExact(MatchField.IPV4_DST, IPv4Address.of("10.33.25.1"))
					.setExact(MatchField.IPV4_SRC, IPv4Address.of("10.33.25.2"))
					//.setExact(MatchField.UDP_DST, TransportPort.of(5202))
					.build();
			
			Match  udpSrcMatch1 = factory.buildMatch()
					.setExact(MatchField.ETH_TYPE, EthType.IPv4)
					//.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
					.setExact(MatchField.IPV4_DST, IPv4Address.of("10.33.25.2"))
					.setExact(MatchField.IPV4_SRC, IPv4Address.of("10.33.25.1"))
					//.setExact(MatchField.UDP_SRC, TransportPort.of(5202))
					.build();
			
			ArrayList<OFAction> actionsList = new ArrayList<OFAction>();
			long queue = 3;
			long queue2 = 3;
			OFActionSetQueue setQueue = factory.actions().buildSetQueue()
			        .setQueueId(queue)
			        .build();
			OFActionSetQueue setQueue2 = factory.actions().buildSetQueue()
			        .setQueueId(queue2)
			        .build();
			ArrayList<OFAction> actionsList2 = new ArrayList<OFAction>();
			
			OFActionOutput setPort = factory.actions().buildOutput()
					.setPort(OFPort.of(7))
					.setMaxLen(0xffFFffFF)
					.build();
			
			OFActionOutput setPort2 = factory.actions().buildOutput()
					.setPort(OFPort.of(8))
					.setMaxLen(0xffFFffFF)
					.build();
			
			
			actionsList.add(setQueue);
			actionsList.add(setPort);
			
			actionsList2.add(setQueue2);
			actionsList2.add(setPort2);
			ArrayList<OFInstruction> instructionsList = new ArrayList<OFInstruction>();
			OFInstructionApplyActions applyActions = factory.instructions().buildApplyActions()
					.setActions(actionsList)
					.build();
			ArrayList<OFInstruction> instructionsList2 = new ArrayList<OFInstruction>();
			OFInstructionApplyActions applyActions2 = factory.instructions().buildApplyActions()
					.setActions(actionsList2)
					.build();
			
			OFInstructionMeter meter = factory.instructions().buildMeter()
				    .setMeterId(7)
				    .build();
			
			//instructionsList.add(meter);
			instructionsList.add(applyActions);
			//instructionsList2.add(meter);
			instructionsList2.add(applyActions2);
			/*Creating FlowMod*/
			OFFlowAdd flowAdd = factory.buildFlowAdd()
					.setMatch(udpDstMatch1)
					.setCookie((U64.of(test.COOKIE)))
					.setIdleTimeout(test.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
					.setHardTimeout(test.FLOWMOD_DEFAULT_HARD_TIMEOUT)
					.setPriority(test.FLOWMOD_PRIORITY)
					.setBufferId(OFBufferId.NO_BUFFER)
					.setInstructions(instructionsList)
					.build();
			OFFlowAdd flowAdd2 = factory.buildFlowAdd()
					.setMatch(udpSrcMatch1)
					.setCookie((U64.of(test.COOKIE)))
					.setIdleTimeout(test.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
					.setHardTimeout(test.FLOWMOD_DEFAULT_HARD_TIMEOUT)
					.setPriority(test.FLOWMOD_PRIORITY)
					.setBufferId(OFBufferId.NO_BUFFER)
					.setInstructions(instructionsList2)
					.build();
			sw.write(flowAdd);
			sw.write(flowAdd2);
			logger.info("SIP flows were pushed to the switch.");
		} else{
			logger.info("other switch");
		}
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
