package net.floodlightcontroller.proactiveflowpusher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.sdp.SdpParseException;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.VlanVid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.proactiveflowpusher.IRTPFlowPusher;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Path;
import gov.nist.javax.sdp.fields.ConnectionField;
import gov.nist.javax.sdp.fields.MediaField;
import gov.nist.javax.sdp.parser.ConnectionFieldParser;
import gov.nist.javax.sdp.parser.MediaFieldParser;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.parser.StringMsgParser;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;

public class SIPMsgAnalyzer implements IFloodlightModule, IOFMessageListener {
	
	//------------------------------------------------------
	protected static Logger logger;
	protected IFloodlightProviderService floodlightProvider;
	protected IRTPFlowPusher rtpFlowPusher;
	protected IRoutingService routingService;
	protected IDeviceService deviceService;
	//------------------------------------------------------
	public StringMsgParser sipMsgParser;
	public MediaFieldParser mediaParser;
	public ConnectionFieldParser connectionParser;
	//------------------------------------------------------
	/* The HashMap has next structure: <CallID> | [0 = <MediaType>, 1 = <Src Media Port>, 2 = <Src IP Address>, 3 = <Dst Media Port>, 4 = <Dst IP Address>
	 * 												5 = <Src OFPort>, 6 = Src OFPort, []] */
	public HashMap<String, ArrayList<String>> extractedData;
	public HashMap<String, ArrayList<String>> pathID;
	//------------------------------------------------------
	
	@Override
	public String getName() {
		return SIPMsgAnalyzer.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		//return (type.equals(OFType.PACKET_IN) && (name.equals("RTPFLowPusher")));
		return false;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg,
			FloodlightContext cntx){
		
		/*Collection<? extends IDevice> allDev = deviceService.getAllDevices();
		for(IDevice dev: allDev){
			System.out.println(dev.toString());
		}*/
		switch (msg.getType()){
		case PACKET_IN:
			Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, 
	        		IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
			
			 if (eth.getEtherType() == EthType.IPv4) {
		            /* We got an IPv4 packet; get the payload from Ethernet */
		            IPv4 ipv4 = (IPv4) eth.getPayload();
		            if (ipv4.getProtocol() == IpProtocol.UDP) {
		                /* We got a UDP packet; get the payload from IPv4 */
		                UDP udp = (UDP) ipv4.getPayload();
		                if(udp.getSourcePort().getPort() == 5060 || 
		                		udp.getDestinationPort().getPort() == 5060){
		                	try{
		                		MacAddress mac = eth.getSourceMACAddress();
		                		dataExtracting(udp, mac);
		    				} catch (Exception e) {
		    					//e.printStackTrace();
		    				}
		                }
		            } else if (ipv4.getProtocol() == IpProtocol.TCP) {
		               // TCP packet
		            }  
		        }
			break;
		default:
			break;
		}
		return Command.CONTINUE;		
	}
	
	public ArrayList<String> getAttachmentPoints(MacAddress mac){
		ArrayList<String> list = new ArrayList<>();
		IDevice device = deviceService.findDevice(
				MacAddress.of(mac.getBytes()), 
				VlanVid.ZERO, IPv4Address.NONE, 
				IPv6Address.NONE, 
				DatapathId.NONE, 
				OFPort.ZERO);
		
		if (device != null){
			SwitchPort[] attachmentPoints = device.getAttachmentPoints();
			for (SwitchPort sp : attachmentPoints){
				OFPort devPort = sp.getPortId();
				DatapathId devNode = sp.getNodeId();
				list.add(0, devNode.toString());
				list.add(1, devPort.toString());
				//System.out.println(mac.toString() + "	" + sp.toString());
				break;
			}
		}
		return list;
	}
	
	public Path getPath(ArrayList<String> listEndNodes){
		Path shortestPath = routingService.getPath(
				DatapathId.of(listEndNodes.get(0)), 
				OFPort.of(Integer.parseInt(listEndNodes.get(1))), 
				DatapathId.of(listEndNodes.get(2)), 
				OFPort.of(Integer.parseInt(listEndNodes.get(3))));
		return shortestPath;
	}
	
	public synchronized HashMap<String, ArrayList<String>> getExtractedDataTable(){
		return extractedData;
	}
	
	public synchronized HashMap<String, ArrayList<String>> getPathIDTable(){
		return pathID;
	}
	
	
	
	/* This Method is analyzes a UDP packet (is it SIP message, Request/Response, class of Request/Response etc.)
	 * Extracts information (CallID, Media Port, IP Address, Media type etc.)
	 * Saves to common structure
	 * Returns HashMap with extracted informations
	 */
	public void dataExtracting(UDP udpPacket, MacAddress mac) 
			throws ParseException, UnsupportedEncodingException, SdpParseException{
		//------------------------------------------------
		ArrayList<String> valueList = new ArrayList<String>();
		ArrayList<String> pathList = new ArrayList<String>();
		ArrayList<String> tempList = null;
		ArrayList<String> tempListPath = null;
		Integer mediaPort = null;
		MediaField parsedMediaField = null;
		ConnectionField parsedConnectionField = null;
		//------------------------------------------------
		byte[] udpPayload = udpPacket.getPayload().serialize(); // Getting Payload from UDP packet and serializing it
		SIPMessage parsedSIPMsg = sipParser(udpPayload);		// Getting SIPMessage from UDP Payload
		String sdpContent = parsedSIPMsg.getMessageContent();	// Getting SDP content from Parsed SIP message
		String callID = parsedSIPMsg.getCallId().toString();	// Getting CallID from Parsed SIP message
		String mediaField = null;
		String connectionField = null;
		//------------------------------------------------
		// Is message Request ...
		if (parsedSIPMsg instanceof Request){
			// If message is INVITE
			if (((Request) parsedSIPMsg).getMethod().equals(Request.INVITE) && parsedSIPMsg.getContentTypeHeader().getContentType().contains("application")){
				if (!extractedData.containsKey(callID)) {
					mediaField = getField(sdpContent, 'm');
					connectionField = getField(sdpContent, 'c');
					parsedMediaField = mediaParser(mediaField);
					parsedConnectionField = connectionParser(connectionField);
					mediaPort = parsedMediaField.getMediaPort();
					valueList.add(0, parsedMediaField.getMediaType());
					valueList.add(1, mediaPort.toString());
					valueList.add(2, parsedConnectionField.getAddress());
			    	extractedData.put(callID, valueList);
			    	logger.info("This is a 'INVITE' message with DialogID: {}", callID);
			    	//logger.info(extractedData.toString());
				}
				if (!pathID.containsKey(callID)){
            		pathID.put(callID, getAttachmentPoints(mac));
            		//logger.info(pathID.toString());
				}
			}
			// If message is BYE
			else if (((Request) parsedSIPMsg).getMethod().equals(Request.BYE)) {
				if (extractedData.containsKey(callID)){
					extractedData.remove(callID);
					//System.out.println(extractedData.toString());
					logger.info("This is a 'BYE' message with DialogID: {}", callID);
					//logger.info(extractedData.toString());
				}
				if (pathID.containsKey(callID)){
					pathID.remove(callID);
					//logger.info(pathID.toString());
				}
			}
		// If message is Response ... 
		} else if (parsedSIPMsg instanceof Response){
			Integer statusCode = ((Response) parsedSIPMsg).getStatusCode();
			Integer responseClass = statusCode/100;
			//------------------------------------
			// If message is OK response to INVITE
			if (statusCode == Response.OK && parsedSIPMsg.hasContent() == true){
				tempList = extractedData.get(callID);
				if (extractedData.containsKey(callID) && tempList.size() <= 3){
					mediaField = getField(sdpContent, 'm');
					connectionField = getField(sdpContent, 'c');
					parsedMediaField = mediaParser(mediaField);
					parsedConnectionField = connectionParser(connectionField);
					mediaPort = parsedMediaField.getMediaPort();
					tempList.add(3, mediaPort.toString());
					tempList.add(4, parsedConnectionField.getAddress());
					extractedData.put(callID, tempList);
					logger.info("This is a '200 OK' message with DialogID: {}", callID);
					//logger.info(extractedData.toString());
					
					tempListPath = pathID.get(callID);
					if (pathID.containsKey(callID) && tempListPath.size() <= 2){
						pathList = getAttachmentPoints(mac);
						tempListPath.add(2, pathList.get(0));
						tempListPath.add(3, pathList.get(1));
						pathID.put(callID, tempListPath);
						//logger.info(pathID.toString());
					}
					rtpFlowPusher.flowPusher(tempList, getPath(tempListPath));
				}		
			} 
			// If message has Error code
			else if (responseClass == 4 || responseClass == 5 || responseClass == 6){
				if (extractedData.containsKey(callID)){
					extractedData.remove(callID);
					//System.out.println(extractedData.toString());
					logger.info("This is an '{}XX' message with DialogID: {}", responseClass, callID);
					//logger.info(extractedData.toString());
				}
				if (pathID.containsKey(callID)){
					pathID.remove(callID);
					//logger.info(pathID.toString());
				}
			}
		}
	}
	
	/* Gets Media Field from SDP Content String
	 * 
	 */
	public String getField(String sdpContent, char field){
		for(String str : sdpContent.split("\n")){
			if(str.charAt(0) == field){
				return str;
				}
		}
		return null;
	}

	
	/* Parser for Media Description field in SDN content.
	 * Accepting string with "m=..." attribute and parsering it
	 */
	public MediaField mediaParser (String str) throws ParseException {
        try{
    		mediaParser = new MediaFieldParser(str);
        	MediaField mediaContent = mediaParser.mediaField();
        	return mediaContent;
        } catch (ParseException e) {
			// TODO: handle exception
        	logger.info("mediaParserException");
			e.printStackTrace();
		}
        return null;
	}
	
	public ConnectionField connectionParser (String str) throws ParseException {
        try{
    		connectionParser = new ConnectionFieldParser(str);
        	ConnectionField connectionContent = connectionParser.connectionField();
        	return connectionContent;
        } catch (ParseException e) {
			// TODO: handle exception
        	logger.info("mediaParserException");
			e.printStackTrace();
		}
        return null;
	}

	
	/* Parser for UDP payload, returning SIPMessage
	 */
	public SIPMessage sipParser(byte[] msgBuffer) throws ParseException{
		try {
			SIPMessage parsedSIPMsg = sipMsgParser.parseSIPMessage(msgBuffer, true, false, null);
			return parsedSIPMsg;
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			logger.info("sipParseExeption");
			e.printStackTrace();
		}
		return null;
	}
	

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = 
				new ArrayList<Class<? extends IFloodlightService>>();
	    return l;
	}
	

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		 Map<Class<? extends IFloodlightService>, IFloodlightService> m = 
				 new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		 return m;
	}
	

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
		        new ArrayList<Class<? extends IFloodlightService>>();
		    l.add(IFloodlightProviderService.class);
		    l.add(IRTPFlowPusher.class);
		    l.add(IRoutingService.class);
		    l.add(IDeviceService.class);
		    return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		rtpFlowPusher = context.getServiceImpl(IRTPFlowPusher.class);
		routingService = context.getServiceImpl(IRoutingService.class);
		deviceService = context.getServiceImpl(IDeviceService.class);
	    logger = LoggerFactory.getLogger(SIPMsgAnalyzer.class);
		sipMsgParser = new StringMsgParser();
		extractedData = new HashMap<String, ArrayList<String>>();
		pathID = new HashMap<String, ArrayList<String>>();

	}
	

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		
	}

}
