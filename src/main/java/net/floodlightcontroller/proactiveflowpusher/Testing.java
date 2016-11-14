package net.floodlightcontroller.proactiveflowpusher;
//net.floodlightcontroller.siptracker.Testing

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
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

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;

public class Testing implements IFloodlightModule, IOFMessageListener, IOFSwitchListener {
	
	protected static Logger logger;
	protected IFloodlightProviderService floodlightProvider;
	protected IOFSwitchService switchService;
	//-----------------------------------------------------
	// flow-mod - for use in the cookie
	public static final int LEARNING_SWITCH_APP_ID = 1;
	// LOOK! This should probably go in some class that encapsulates
	// the app cookie management
	public static final int APP_ID_BITS = 12;
	public static final int APP_ID_SHIFT = (64 - APP_ID_BITS);
	public static final long LEARNING_SWITCH_COOKIE = (long) (LEARNING_SWITCH_APP_ID & ((1 << APP_ID_BITS) - 1)) << APP_ID_SHIFT;

	// more flow-mod defaults
	protected static short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 3; // in seconds
	protected static short FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; // infinite
	protected static short FLOWMOD_PRIORITY = 101;

	// for managing our map sizes
	protected static final int MAX_MACS_PER_SWITCH  = 1000;

	// normally, setup reverse flow as well. Disable only for using cbench for comparison with NOX etc.
	protected static final boolean LEARNING_SWITCH_REVERSE_FLOW = true;
	//----------------------------------------------------
	
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return Testing.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void switchActivated(DatapathId switchId) {
		// TODO Auto-generated method stub

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
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg,
			FloodlightContext cntx) {
		switch (msg.getType()){
		case PACKET_IN:
			ArrayList<String> data = UDPPacket(cntx);
			if (data != null){
				try{
					mainMethod(sw, data);

				} catch (Exception e) {
					// TODO: handle exception
					//e.printStackTrace();
				}
			}
			break;
		default:
			break;
		}
		return Command.CONTINUE;
	}
	
	public void mainMethod(IOFSwitch sw, ArrayList<String> data) {
		/* <CallID> | [0 = <Src Media Port>, 1 = <Src IP Address>, 2 = <Dst Media Port>, 3 = <Dst IP Address>, 4 = <MediaType>] */
		//HashMap<String, ArrayList<String>> extractedData = new HashMap<String, ArrayList<String>>();
		//extractedData.put("1", data);
		//logger.info("mainMethod");
		
		//IOFSwitch sw = switchService.getSwitch(switchId);
		OFFactory myFactory = sw.getOFFactory(); /* Use the factory version appropriate for the switch in question. */
		OFFlowAdd forwardFlow = null;
		OFFlowAdd backwardFlow = null;
		
		Match forwardMatch = matcher(myFactory, data, "forward");
		Match backwardMatch = matcher(myFactory, data, "backward");
		forwardFlow = flowCreator(5, 5, 1, myFactory, forwardMatch);
		backwardFlow = flowCreator(5, 5, 2, myFactory, backwardMatch);
		
		sw.write(forwardFlow);
		sw.write(backwardFlow);
		logger.info("Flows were pushed");

		
	}
	

	public ArrayList<String> UDPPacket(FloodlightContext cntx){
		/* Retrieve the deserialized packet in message */
		ArrayList<String> data = new ArrayList<String>();
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, 
        		IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        
        /* Check the ethertype of the Ethernet frame and retrieve the appropriate payload.
         * Note the shallow equality check. EthType caches and reuses instances for valid types.*/
        if (eth.getEtherType() == EthType.IPv4) {
            /* We got an IPv4 packet; get the payload from Ethernet */
            IPv4 ipv4 = (IPv4) eth.getPayload();
        
            if (ipv4.getProtocol() == IpProtocol.UDP) {
                /* We got a UDP packet; get the payload from IPv4 */
                UDP udp = (UDP) ipv4.getPayload();
                
                IPv4Address ipDST = ipv4.getDestinationAddress();
                IPv4Address ipSRC = ipv4.getSourceAddress();
                /* Various getters and setters are exposed in UDP */
                TransportPort srcPort = udp.getSourcePort();
                TransportPort dstPort = udp.getDestinationPort();
                
                data.add(srcPort.toString());
            	data.add(dstPort.toString());
            	data.add(ipDST.toString());
            	data.add(ipSRC.toString());
            	return data; 

            } else if (ipv4.getProtocol() == IpProtocol.TCP) {
            	
            	return null; 
            }  
        }
        return null;
	}
	
	public Match matcher(OFFactory myFactory, ArrayList<String> list, String direction) {
		switch(direction){
		case "forward":
			Match  forward = myFactory.buildMatch()
			.setExact(MatchField.ETH_TYPE, EthType.IPv4)
			.setExact(MatchField.IPV4_DST, IPv4Address.of(list.get(2)))		//DST IP Address
			.setExact(MatchField.IPV4_SRC, IPv4Address.of(list.get(3)))		//SRC IP Address
			.setExact(MatchField.IP_PROTO, IpProtocol.UDP)						//UDP Protocol
			.setExact(MatchField.UDP_DST, TransportPort.of(Integer.parseInt(list.get(1))))		//DST Port
			.setExact(MatchField.UDP_SRC, TransportPort.of(Integer.parseInt(list.get(0))))		//SRC Port
			.build();
			return forward;
		case "backward":
			Match  backward = myFactory.buildMatch()
			.setExact(MatchField.ETH_TYPE, EthType.IPv4)
			.setExact(MatchField.IPV4_DST, IPv4Address.of(list.get(3)))
			.setExact(MatchField.IPV4_SRC, IPv4Address.of(list.get(2)))
			.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
			.setExact(MatchField.UDP_DST, TransportPort.of(Integer.parseInt(list.get(0))))
			.setExact(MatchField.UDP_SRC, TransportPort.of(Integer.parseInt(list.get(1))))
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
	public OFFlowAdd flowCreator(Integer queueID, Integer meterID, Integer port, OFFactory factory, Match  match){
		ArrayList<OFAction> actionsList = new ArrayList<OFAction>();
		ArrayList<OFInstruction> instructionsList = new ArrayList<OFInstruction>();
		
		OFActionSetQueue setQueue = factory.actions().buildSetQueue()
		        .setQueueId(queueID)
		        .build();
		OFActionOutput setPort = factory.actions().buildOutput()
				.setPort(OFPort.of(port))
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
				.setCookie((U64.of(SIPFlowPusher.LEARNING_SWITCH_COOKIE)))
				.setIdleTimeout(SIPFlowPusher.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
				.setHardTimeout(SIPFlowPusher.FLOWMOD_DEFAULT_HARD_TIMEOUT)
				.setPriority(SIPFlowPusher.FLOWMOD_PRIORITY)
				.setBufferId(OFBufferId.NO_BUFFER)
				.setInstructions(instructionsList)
				.build();
		
		return flow;
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
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l = 
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IOFSwitchService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		logger = LoggerFactory.getLogger(SIPFlowPusher.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		switchService.addOFSwitchListener(this);
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);

	}

}
